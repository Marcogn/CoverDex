# Reference — the Pokémon dataset and sprite URLs

Everything in this file was **measured against the live upstream sources on
2026-09-04** while planning, not recalled. Sizes are uncompressed bytes as
counted from the actual response body; row counts exclude the CSV header.
Treat this file as the contract Phase 1 implements against, and re-measure
before changing any of it.

This is the single most important file in the plan. The whole reason the
native app exists is that the current PWA's first-launch download is
unacceptable on a phone, and this file is the fix.

---

## 1. The problem being solved

`src/utils/pokeApiFetch.ts` (the PWA) assembles its cache from the static
`PokeAPI/api-data` JSON mirror by fetching one detail record per entity.
Measured cost of a single first launch:

| Stage | Requests | Mean size | Total |
|---|---:|---:|---:|
| `pokemon/{id}` | 1351 | 266,425 B | ≈ 360 MB |
| `pokemon-species/{id}` | 1025 | 29,667 B | ≈ 30 MB |
| `move/{id}` | 937 | 34,997 B | ≈ 33 MB |
| `evolution-chain/{id}` | 541 | 3,251 B | ≈ 1.8 MB |
| `type/{id}` | 18 | 27,684 B | ≈ 0.5 MB |
| indexes (`pokemon`, `type`, `move`) | 3 | | ≈ 0.18 MB |
| **Total** | **3875** | | **≈ 426 MB** |

Means are sampled, not exhaustive: 13 `pokemon` records (47 KB for
`pokemon/1000` up to 629 KB for `pokemon/150`), 9 `move` records, 7
`pokemon-species` records. The totals are therefore accurate to roughly
±15%, which does not change the conclusion by any amount that matters.

That is what the user waits through on `LoadingScreen` before the app is
usable, on mobile data, with `BATCH_SIZE = 50` and a 50 ms inter-batch
sleep. Hall of Memories hit the same wall and solved it by refusing to
fetch per-entity detail records at all (see
`../../../Hall-Of-Memories/docs/plan/reference-pokeapi.md`); it got its
sync down to ~57 requests and ~2.5 MB by reading aggregate endpoints.

CoverDex can do considerably better than that, because everything it needs
lives in the **CSV source data of the PokéAPI project itself**.

## 2. The data source

```
CSV = https://raw.githubusercontent.com/PokeAPI/pokeapi/master/data/v2/csv
```

`PokeAPI/pokeapi` is the upstream project that *generates* the
`PokeAPI/api-data` JSON mirror the PWA reads. `data/v2/csv/` is its seed
data: plain, comma-separated, one file per table, no API key, no
documented rate limit, served by GitHub's CDN. It is the same data, one
normalization step earlier, and it is roughly three orders of magnitude
smaller.

### What the sync downloads — measured

The first eight files are Phase 1's original set; the last four were added
in Phase 7 for base stats and correct English ability/move names (see
`phase-7-accuracy-and-customization.md` §2) — still read at the same
pinned `DATASET_REVISION` as everything else.

| File | Size | Rows | Columns used |
|---|---:|---:|---|
| `pokemon.csv` | 47,082 B | 1351 | `id`, `identifier`, `species_id`, `is_default` |
| `pokemon_species.csv` | 56,884 B | 1025 | `id`, `identifier`, `generation_id`, `evolves_from_species_id`, `is_legendary`, `is_mythical` |
| `pokemon_types.csv` | 19,058 B | 2116 | `pokemon_id`, `type_id`, `slot` |
| `pokemon_abilities.csv` | 37,194 B | 2941 | `pokemon_id`, `ability_id`, `is_hidden`, `slot` |
| `abilities.csv` | 7,074 B | 374 | `id`, `identifier` |
| `moves.csv` | 42,322 B | 937 | `id`, `identifier`, `type_id`, `power`, `damage_class_id` |
| `types.csv` | 321 B | 21 | `id`, `identifier` |
| `type_efficacy.csv` | 2,883 B | 324 | `damage_type_id`, `target_type_id`, `damage_factor` |
| `pokemon_stats.csv` | 94,392 B | 8106 | `pokemon_id`, `stat_id`, `base_stat` |
| `pokemon_stats_past.csv` | 3,046 B | 235 | `pokemon_id`, `generation_id`, `stat_id`, `base_stat` |
| `ability_names.csv` | 65,239 B | 3739 (374 English) | `ability_id`, `local_language_id`, `name` |
| `move_names.csv` | 202,670 B | 9532 (937 English) | `move_id`, `local_language_id`, `name` |
| **Total** | **578,165 B** | | **12 requests** |

**≈ 565 KiB and 12 requests, against ≈ 426 MB and 3875 requests** —
still roughly 750× fewer bytes and 320× fewer requests than the JSON
mirror. `move_names.csv` alone is over a third of the total; it buys
correct move capitalisation (`Double-Edge`, `U-turn`, `Will-O-Wisp`) that
`prettify()` cannot produce — see `phase-7-accuracy-and-customization.md`
§0.3. On any usable connection the sync still finishes well under a
second, which is what "recupero fulmineo" means here.

### Exact headers, as measured

```
pokemon.csv            id,identifier,species_id,height,weight,base_experience,order,is_default
pokemon_species.csv    id,identifier,generation_id,evolves_from_species_id,evolution_chain_id,
                       color_id,shape_id,habitat_id,gender_rate,capture_rate,base_happiness,
                       is_baby,hatch_counter,has_gender_differences,growth_rate_id,
                       forms_switchable,is_legendary,is_mythical,order,conquest_order
pokemon_types.csv      pokemon_id,type_id,slot
pokemon_abilities.csv  pokemon_id,ability_id,is_hidden,slot
abilities.csv          id,identifier,generation_id,is_main_series
moves.csv              id,identifier,generation_id,type_id,power,pp,accuracy,priority,target_id,
                       damage_class_id,effect_id,effect_chance,contest_type_id,
                       contest_effect_id,super_contest_effect_id
types.csv              id,identifier,generation_id,damage_class_id
type_efficacy.csv      damage_type_id,target_type_id,damage_factor
pokemon_stats.csv      pokemon_id,stat_id,base_stat,effort
pokemon_stats_past.csv pokemon_id,generation_id,stat_id,base_stat,effort
ability_names.csv      ability_id,local_language_id,name
move_names.csv         move_id,local_language_id,name
```

Do not index columns positionally. Parse the header row and look columns up
by name — upstream has added columns to these files before and will again.

## 3. How every field the app needs is derived

`PokemonEntry` and `MoveEntry` (the PWA's `src/types/index.ts`) are the
contract. Every field maps onto the CSVs with no detail fetch:

| Field | Derivation |
|---|---|
| `id` | `pokemon.id` (the **form** id; 327 rows are forms with id > 10000) |
| `name` | `pokemon.identifier` (kebab-case) |
| `displayName` | `prettify(identifier)` — split on `-`, capitalize each part, join with spaces |
| `speciesName` | `pokemon_species.identifier` joined via `pokemon.species_id` |
| `types` | `pokemon_types` rows for `pokemon_id`, ordered by `slot`, `type_id` → `types.identifier` |
| `isLegendary` / `isMythical` | `pokemon_species.is_legendary` / `.is_mythical` (`0`/`1`) |
| `isFinalEvolution` | a species is final **iff no species has `evolves_from_species_id` equal to it** — a pure local computation over `pokemon_species.csv`, zero extra requests |
| `defaultAbility` | `pokemon_abilities` row for `pokemon_id` with `is_hidden = 0` and the lowest `slot`, `ability_id` → `abilities.identifier` |
| sprite URLs | not stored at all — derived, see §5 |
| `generationIntroduced` | `pokemon_species.generation_id` (**new**; see §4) |
| `MoveEntry.type` | `moves.type_id` → `types.identifier` |
| `MoveEntry.power` | `moves.power`, empty string → `null` |
| `MoveEntry.damageClass` | `moves.damage_class_id` → `1 = status`, `2 = physical`, `3 = special` (verified against `move_damage_classes.csv`) |
| `TypeChart[a][b]` | `type_efficacy.damage_factor / 100.0` (`0`, `50`, `100`, `200` → `0.0`, `0.5`, `1.0`, `2.0`) |

### Verified properties of the join

Checked on 2026-09-04 against the files above:

- `pokemon_types.csv` covers **all 1351** rows of `pokemon.csv`. No gaps.
- Every `pokemon.species_id` resolves in `pokemon_species.csv`. No gaps.
- `type_efficacy.csv` has exactly **324 = 18 × 18** rows: the complete
  matrix, neutral `100` entries included (204 of them), so the chart needs
  no "default to 1" pass — though keeping one costs nothing and guards
  against upstream trimming it later.
- `types.csv` has 21 rows: ids 1–18 are the real types, plus `19 stellar`,
  `10001 unknown`, `10002 shadow`. **Filter to ids 1–18**; the app's
  `POKEMON_TYPES` is and stays 18 entries.
- **Consequence, verified during Phase 1 implementation (2026-09-04):**
  filtering types to ids 1–18 means `moves.csv`'s 18 rows with
  `type_id = 10002` (`shadow`) resolve to no `PokemonType` and are dropped
  from the assembled cache entirely — `shadow-rush`, `shadow-blast`, and
  the rest of the Pokémon Colosseum/XD "Shadow Pokémon" move set (never
  used in any mainline game). 919 of 937 moves make it into the cache; the
  18 that don't are a deliberate consequence of the 18-type model, not a
  bug — see `docs/implementation-decisions.md`. A ROM hack team that
  genuinely needs one of these moves uses the existing unrecognized-move
  fallback (native-spec.md's Showdown import contract): typed in as a
  custom move, completed by hand.
- `pokemon_abilities.csv` is missing **11** of the 1351 forms, all with
  id ≥ 10301 (the newest unreleased mega forms upstream has added ahead of
  the JSON mirror). Fall back to the default form of the same species
  (`pokemon.is_default = 1`), and if that also fails leave `defaultAbility`
  null — it is a convenience pre-fill, never required.
- `is_legendary` is set on **71** species, `is_mythical` on **23**.
- 78 of the 937 moves are non-status with empty or zero `power` (OHKO and
  fixed-damage moves). This is why the coverage engine's
  `damageClass != status && (power ?? 0) > 0` filter is not redundant, and
  why `power` is worth carrying.

### The final-evolution derivation was cross-validated

This is the one field where the CSV derivation is *structurally different*
from the PWA's (which walks `evolution-chain` trees and collects leaves), so
it was checked rather than assumed. All 541 evolution chains were fetched
and compared against the `evolves_from_species_id` derivation:

```
chain-derived finals: 568
csv-derived finals  : 568
only in chains: meltan
only in csv   : phione
```

Two species differ out of 1025, and on `meltan` the CSV is the correct one
(Melmetal evolves from Meltan; the chain walk marks Meltan a leaf).
`phione` is the known Manaphy/Phione breeding special case. **Do not treat
either as a bug to fix**; record the comparison in
`docs/implementation-decisions.md` and move on.

## 4. One deliberate behaviour change: the generation filter

`src/hooks/suggestionEngine.ts` filters candidates by generation using a
hardcoded `GEN_RANGES` table of **id ranges** (`'3': [252, 386]`, …, with
`'9': [906, Infinity]`). That table is wrong for alternate forms: every one
of the 327 forms with id > 10000 falls into the `'9'` bucket, so
Mega Charizard X is offered as a Generation IX candidate and never as a
Generation I one.

`pokemon_species.generation_id` gives the real answer for free. **Use it**,
drop `GEN_RANGES`, and:

- write the change up in `docs/implementation-decisions.md`,
- add a `CHANGELOG.md` bullet — it is user-visible,
- note in `docs/test-plan.md` that Generation-filtered suggestions now
  include the forms of that generation's species.

This is the only intentional behavioural divergence from the PWA in the
whole plan. Everything else must round-trip identically; see each phase's
"Behavioural parity" section.

## 5. Sprite URLs — verified table

```
SPRITES = https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon
```

The CSVs carry no sprite URLs, so — exactly as Hall of Memories does — the
app **derives** them and stores none. `resolveSpriteUrl(entry, context)`
becomes a pure function of `(pokemonId, context)`.

| Variant | Path | `PokemonEntry` field it replaces | Verified |
|---|---|---|---|
| HOME render | `other/home/{id}.png` | `spriteHome` | 200 |
| Official artwork | `other/official-artwork/{id}.png` | `spriteArtwork` | 200 |
| Pixel sprite | `{id}.png` | `spriteDefault` | 200 |

Verified 200 on ids `6`, `1025`, `10034`, `10277` and `10325` — a starter
line member, the newest released species, an old alternate form, a recent
alternate form, and one of the unreleased mega forms upstream added ahead
of everything else. Coverage is good, but it is **not** guaranteed for
every id, so the fallback chain is mandatory:

- **card** context (team slots, suggestion cards): `other/home` →
  `other/official-artwork` → `{id}.png` → placeholder.
- **dropdown** context (list thumbnails): `{id}.png` → placeholder.

These are exactly the orderings in today's `src/utils/spriteUtils.ts`; only
the source of the URLs changes.

Coil has no built-in "try the next URL on a 404", so — as in Hall of
Memories — the ordered candidate list comes from a **pure, unit-tested
resolver**, and a small stateful composable advances an index in `onError`.
Do not put the fallback logic in the resolver.

Custom Pokémon carry no id and resolve to the placeholder, unchanged.

### Type badges

`src/data/typeSprites.ts` maps the 18 type names to ids 1–18 for the
Scarlet/Violet type icons. That mapping is **identical to `types.csv`'s
`id` column** — verified. Derive it from the synced type table rather than
re-hardcoding it, and keep the badge URL construction as it is today.

## 6. Pinning, staleness and re-sync

Both sources are read from `master`, which moves. As measured on
2026-09-04, `PokeAPI/pokeapi` master is **ahead of** the JSON mirror and of
released games: `pokemon.csv` already contains `baxcalibur-mega`,
`meowstic-female-mega` and 300+ other forms. That is harmless — they are
real rows with real types — but it means the dataset is not reproducible
across two syncs.

**Pin the commit.** Fetch from
`raw.githubusercontent.com/PokeAPI/pokeapi/<sha>/data/v2/csv/...` with the
sha in a single `const DATASET_REVISION` in the client, and store it in the
cache metadata row alongside the schema version. Resolved on 2026-09-04:

```
PokeAPI/pokeapi   master = d4f9a4af58ade123fbc0558f68b1c69daa97d9e4
PokeAPI/sprites   master = 090a7f7cc9e707b23707441fe4769d0f72ff6993
```

Use the `pokeapi` sha for the CSV base. Leave sprites on `master` (a pinned
sprite tree would freeze new artwork for no benefit, and a missing sprite
already falls back gracefully). Bumping `DATASET_REVISION` is a normal PR
with a `CHANGELOG.md` bullet; re-check §3's verified properties when you do.

Cache invalidation follows Hall of Memories:

- `DATASET_SCHEMA_VERSION` is compared against the stored meta row. A
  mismatch means "absent" and triggers a silent re-sync — never a crash on
  a column that isn't there. Bump it whenever a cache table's shape changes.
- A sync either commits **fully inside one Room transaction or writes
  nothing**. A half-written cache that reports itself synced is worse than
  no cache.
- Wiping the cache must name the cache tables explicitly. **Never
  `clearAllTables()`** — it would take the user's teams with it.

## 7. Client gotchas

Carried over from Hall of Memories and ThePatientGamerHelper; each cost
real debugging time there.

- **Do not set `Accept-Encoding` on `HttpURLConnection`.** Left alone it
  negotiates gzip and decompresses transparently. Set it by hand and you
  get raw gzip bytes. This matters more here than it did in Hall of
  Memories: CSV compresses extremely well, so the 565 KB figure above is
  what crosses the wire uncompressed and roughly a quarter of that with
  the gzip you get for free.
- **Parse the CSV properly.** `moves.csv` and friends are plain enough that
  `split(",")` mostly works, but `pokemon_species.csv` has trailing empty
  fields and upstream is free to introduce a quoted comma. Write one small
  `CsvParser` in `domain/` handling quoted fields and empty trailing
  columns, unit-test it, and use it everywhere. It is 40 lines; do not pull
  in a dependency for it.
- **Empty string is not zero.** `moves.power` is `""` for status and
  fixed-damage moves and must become `null`, not `0`. The distinction is
  load-bearing in the coverage engine.
- **Partial writes.** See §6.
