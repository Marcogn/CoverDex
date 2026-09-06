# Phase 7 — Engine accuracy, ability/item modelling, BST-aware suggestions

**Status:** planned, not started.
**Executed by:** one agent session, in the task order below.
**Read first:** [`../../CLAUDE.md`](../../CLAUDE.md), [`README.md`](README.md)
(working rules), [`native-spec.md`](native-spec.md),
[`reference-pokedata.md`](reference-pokedata.md),
[`../post-migration-review.md`](../post-migration-review.md).

This is the first phase after the six-phase native rewrite closed. Unlike
Phases 0–6 it has no `legacy-web/` oracle: that directory was deleted in
Phase 6, and several items here are **deliberate corrections of ported
behaviour**, not ports. Where this file and a Phase 0–6 doc disagree, this
file wins, and the divergence must be written into
[`../implementation-decisions.md`](../implementation-decisions.md) under a
new "Phase 7" heading as you go.

---

## 0. Why this phase exists — the pre-plan audit

Everything in this section was **measured against the pinned dataset
revision `d4f9a4af58ade123fbc0558f68b1c69daa97d9e4`** during planning, not
recalled. The numbers are reproducible with `curl` against
`https://raw.githubusercontent.com/PokeAPI/pokeapi/<rev>/data/v2/csv/<file>`.

### 0.1 The suggestion list degenerates to "lowest Pokédex id"

`domain/suggestion/SuggestionEngine.kt`'s ranking comparator is:

```kotlin
compareByDescending<RankedCandidate> { it.bestScore }
    .thenByDescending { it.isFinal }
    .thenBy { it.entryId }
```

and `bestScore` is `Scoring.kt`'s composite:

```
offensiveGain − 0.5 × newWeaknesses.size − 1.0 × aggravatedWeaknesses.size
```

When a full team already covers all 18 types, `offensiveGain` is `0` or
negative **for every candidate**, so the score collapses to
*"minus the number of distinct types that hit the candidate for more than
1×"*. The best possible score therefore belongs to whatever typing has the
fewest weakness types — pure Normal has exactly one (Fighting) — and every
pure-Normal final evolution ties at `−0.5`. The `thenBy { entryId }`
tie-break then sorts those ties by ascending Pokédex id.

That is exactly the screenshot the user reported: Raticate (id 20), Persian
(53), Kangaskhan (115) — the three lowest-id pure-Normal final evolutions,
all at `Punteggio: −0,5`. The second reported case ("it suggested another
Water/Ground instead of Dragonite") is the same mechanism: Water/Ground has
a single weakness type (Grass), so it ties at the top of the penalty term.

Two further consequences worth knowing before touching this code:

- **`weaknesses()` counts types, not magnitude.** A 4× weakness and a 2×
  weakness both contribute exactly `1` to the penalty. Water/Ground's ×4
  Grass weakness scores identically to a plain ×2. This is ported
  behaviour; **do not change the formula in this phase** — see §5.
- **`Suggestion.gain` means different things in the two modes**
  (`newlyCovered.size` in addition mode, `offensiveGain` in replacement
  mode). Left alone here; recorded in §7.4 as a follow-up.

The fix in this phase is a **tie-break only** (decision taken with the
repository owner): the composite score and its `0.5`/`1.0` weights — shared
with `domain/generator/TeamGenerator.kt` and asserted by
`ScoringTest`/`SuggestionEngineTest` — are untouched. See §5.

### 0.2 Abilities are stored and displayed as raw PokéAPI slugs

`domain/pokeapi/DatasetAssembly.kt`'s `resolveDefaultAbility()` returns
`abilityIdentifierById[row.abilityId]` — the raw `abilities.csv`
`identifier`, e.g. `"sap-sipper"`, `"damp"`. That value is written to
`PokemonEntry.defaultAbility`, copied into `TeamMember.ability` by the slot
editor and by `AnalysisViewModel.applySuggestion()`, and rendered verbatim
by `ui/team/analysis/PerPokemonCard.kt:115` and by the slot editor's text
field. The ability *picker* meanwhile offers `AbilityEntry.displayName`
(`prettify(identifier)`), so the same ability appears as `sap-sipper` in
the field and `Sap Sipper` one row below it — both screenshots the user
sent show this.

### 0.3 `prettify()` cannot produce correct English names

`prettify()` splits on `-` and joins with a space, so it is wrong for every
name whose canonical form keeps a hyphen or a symbol:

| identifier | `prettify()` | correct (`*_names.csv`, `local_language_id = 9`) |
|---|---|---|
| `well-baked-body` | Well Baked Body | **Well-Baked Body** |
| `soul-heart` | Soul Heart | **Soul-Heart** |
| `double-edge` | Double Edge | **Double-Edge** |
| `u-turn` | U Turn | **U-turn** |
| `will-o-wisp` | Will O Wisp | **Will-O-Wisp** |
| `self-destruct` | Self Destruct | **Self-Destruct** |
| `x-scissor` | X Scissor | **X-Scissor** |
| `freeze-dry` | Freeze Dry | **Freeze-Dry** |

The only correct source is PokéAPI's own `ability_names.csv` /
`move_names.csv`. §3.1 adds them.

### 0.4 The ability effect table is materially incomplete

`domain/ability/AbilityEffects.kt` models 14 abilities. Cross-checked
against `ability_prose.csv` (`local_language_id = 9`, the English
`short_effect` column) at the pinned revision, these **defensive**
abilities alter type effectiveness and are entirely absent:

| ability | PokéAPI `short_effect` (verbatim) | forms | is slot-1 default for |
|---|---|---:|---:|
| `heatproof` | "Halves damage from Fire moves and burns." | 5 | 0 |
| `water-bubble` | "Halves damage from Fire moves, doubles damage of Water moves, and prevents burns." | 3 | 3 |
| `purifying-salt` | "Protects from status conditions and halves damage from Ghost-type moves." | 3 | 3 |
| `filter` | "Decreases damage taken from super-effective moves by 1/4." | 4 | 1 |
| `solid-rock` | "Decreases damage taken from super-effective moves by 1/4." | 4 | 2 |
| `prism-armor` | "Reduces super-effective damage to 0.75×." | 3 | 3 |
| `primordial-sea` | "…causes damaging Fire moves to fail." | 1 | 1 |
| `desolate-land` | "…causes damaging Water moves to fail." | 1 | 1 |
| `delta-stream` | "…causes moves to never be super effective against Flying Pokémon." | 1 | 1 |
| `tera-shell` | "All damage-dealing moves that hit the Pokémon when its HP is full will not be very effective." | 1 | 1 |

**26 catalogue forms carry one of these; 16 of them carry it as their
slot-1 default ability**, i.e. as the value CoverDex auto-fills. For
comparison, the 12 abilities currently modelled cover 230 forms.

One modelled ability is also **incomplete**: `dry-skin` is entered as a
Water immunity only. Its `short_effect` reads "Increases damage from Fire
moves to 1.25×, but absorbs Water moves" — the Fire ×1.25 half is missing.
7 forms have Dry Skin.

On the **offensive** side, `AbilityEffectSide.OFFENSIVE` is declared in the
sealed hierarchy and **never constructed or consumed anywhere in the
codebase** — `offensiveCoverageForMember()` does not take an `ability`
parameter at all. Abilities that genuinely change what a Pokémon covers:

| ability | effect on coverage | forms |
|---|---|---:|
| `scrappy` | Normal and Fighting moves hit Ghost | 15 |
| `minds-eye` | same, plus accuracy clauses | 1 |
| `refrigerate` | Normal moves become Ice | 3 |
| `pixilate` | Normal moves become Fairy | 3 |
| `aerilate` | Normal moves become Flying | 2 |
| `galvanize` | Normal moves become Electric | 3 |
| `normalize` | all moves become Normal | 2 |
| `liquid-voice` | sound moves become Water | 3 |

`tinted-lens` (0.5× → 1×) and `neuroforce` (SE × 1.25) are **correctly
absent**: neither moves a multiplier across the ≥2× threshold
`offensiveCoverageForMember()` tests, so neither changes coverage. Say so
in the code rather than leaving their absence to look like an oversight.

### 0.5 Held items are not modelled anywhere

There is no item concept in the app at all: no field on
`domain/model/TeamMember.kt`, no column on `team_member` or
`custom_pokemon`, and `domain/showdown/ShowdownFormat.kt` exports `"@ "`
with an empty right-hand side and discards the item on import
(`line.substringBefore("@")`). The repository owner asked for the
**defensive subset only** — §4.

### 0.6 Generational type-chart and typing changes are not modelled

`type_efficacy_past.csv` (6 rows) and `pokemon_types_past.csv` (36 rows)
exist at the pinned revision and are not read. They encode, among others,
Gen-1 Ghost↔Psychic, Gen-1 Bug↔Poison, and the pre-Gen-6 Steel/Dark
resistance to Ghost, plus the Fairy retcons (Clefairy et al. were Normal
through Gen 5). **Out of scope for this phase** — the app has no "which
game am I playing" concept and adding one is a larger change than anything
here. Record it in `ROADMAP.md` under "Ideas not yet committed to" rather
than half-implementing it.

### 0.7 Performance defect found while auditing

`SuggestionEngine.findEntry()` is a linear `pool.find { … }` over ~1351
entries and is called **once per candidate** (plus once per team member in
the legendary pre-filter). With the full pool that is on the order of
1.8 million string comparisons per suggestion recomputation, on every team
edit, toggle flip and filter change. Fix it in §5.4.

---

## 1. Scope

**In scope**

1. Dataset: base stats (current and historical) and correct English
   ability/move names (§2, §3.1).
2. Correct, capitalised ability names everywhere; canonical-vs-custom
   ability selection per species (§3).
3. A held-item field, limited to items that change type effectiveness (§4).
4. BST as the suggestion tie-break, generation-aware (§5).
5. A configurable number of displayed suggestions, 5–10, default 5 (§6).
6. Closing the ability-effect gaps of §0.4 and adding the regression tests
   that prove it (§7).

**Explicitly out of scope** — do not implement, do not ask:

- Generational type charts and generational typings (§0.6).
- Changing the composite score formula or its `0.5`/`1.0` weights (§0.1).
- Species/form *display* names: `prettify()` stays for those. Correcting
  them needs `pokemon_species_names.csv` **and** `pokemon_form_names.csv`
  and a join this phase does not need; "Goodra Hisui" is acceptable, while
  `sap-sipper` in a text field is not.
- Localised (Italian) ability/move names. `ability_names.csv` and
  `move_names.csv` do carry `local_language_id = 8` rows; store English
  only and note in `implementation-decisions.md` that the data is already
  on disk if this is ever wanted.
- The Surprise Me generator's own ranking. It shares `Scoring.kt` but not
  the comparator; leave `domain/generator/TeamGenerator.kt` alone except
  where §7 fixes a shared engine it calls.
- Damage calculation, battle simulation, EV/IV, legality. Still out
  (`native-spec.md`).

---

## 2. Dataset — four new pinned CSVs

`data/pokeapi/PokeDataClient.kt`'s `DatasetFile` enum grows from 8 to 12.
Do **not** bump `DATASET_REVISION`; these files are read at the same pinned
commit as the existing eight.

| new file | bytes @ pinned rev | why |
|---|---:|---|
| `pokemon_stats.csv` | 94,392 | current base stats → BST |
| `pokemon_stats_past.csv` | 3,046 | historical base stats → per-generation BST |
| `ability_names.csv` | 65,239 | correct English ability names |
| `move_names.csv` | 202,670 | correct English move names |

Measured totals: **212,818 B today → 578,165 B** across 12 files. Update
`reference-pokedata.md` §2's table and its headline figure in the same
commit — `CLAUDE.md` quotes "~208 KB" and must be corrected too.

`move_names.csv` alone is 203 KB, more than a third of the new total, and
buys only correct move capitalisation. It is included because the
repository owner asked for it explicitly; if the download cost is ever
judged not worth it, dropping this one file and keeping `prettify()` for
moves is a self-contained reversal.

`HttpURLConnection` still negotiates gzip transparently — do **not** add an
`Accept-Encoding` header (`CLAUDE.md`, "Known gotchas"). These are the raw
byte counts, not the wire cost.

### 2.1 Parsers — `domain/pokeapi/DatasetParsers.kt`

Add, in the existing one-row-type-plus-one-function style, reading every
column by name:

```kotlin
data class PokemonStatCsvRow(val pokemonId: Int, val statId: Int, val baseStat: Int)
fun parsePokemonStats(csv: String): List<PokemonStatCsvRow>

data class PokemonStatPastCsvRow(val pokemonId: Int, val generationId: Int, val statId: Int, val baseStat: Int)
fun parsePokemonStatsPast(csv: String): List<PokemonStatPastCsvRow>

/** Only `local_language_id == 9` (English) rows are kept. */
data class LocalizedNameCsvRow(val id: Int, val name: String)
fun parseAbilityNames(csv: String): List<LocalizedNameCsvRow>   // ability_id,local_language_id,name
fun parseMoveNames(csv: String): List<LocalizedNameCsvRow>      // move_id,local_language_id,name
```

Stat ids are **hardcoded**, matching how `damage_class_id` (1/2/3) and the
type ids are already hardcoded in `assembleDataset`: `1` hp, `2` attack,
`3` defense, `4` special-attack, `5` special-defense, `6` speed, `9` the
Gen-1-only combined special. Do not download `stats.csv` for this.

`move_names.csv` has 937 English rows against `moves.csv`'s full move list;
`ability_names.csv` has 374 English rows against 374 abilities. A move or
ability with **no** English name row keeps `prettify(identifier)` as its
display name — never blank, never a crash.

### 2.2 BST derivation — the exact rule

`pokemon_stats_past` semantics, verified during planning against a known
case: a row `(pokemonId, generationId = g, statId, baseStat)` means *"this
stat had this value in every generation up to and including `g`"*.
Butterfree (`pokemon_id = 12`) has `12,5,4,80` and a current
`special-attack` of 90 — i.e. 80 through Gen V, 90 from Gen VI. That
matches the documented Gen-VI Butterfree change.

```
fun statAt(pokemonId, statId, generation):
    candidates = past rows for (pokemonId, statId) with generationId >= generation
    return candidates.minBy { generationId }?.baseStat ?: currentStat(pokemonId, statId)

fun bstAt(pokemonId, generation):
    if generation == 1:
        # Gen I has no Sp. Atk / Sp. Def split; stat id 9 ("special") is the
        # single stat, and the canonical Gen-I base stat total is the sum of
        # FIVE stats, not six.
        return statAt(hp) + statAt(attack) + statAt(defense) + statAt(speed) + statAt(special)
    else:
        return sum of statAt(hp, attack, defense, special-attack, special-defense, speed)
```

**The Gen-I five-stat convention is a decision, not an accident.** The
alternative (mirroring `special` into both Sp. Atk and Sp. Def, giving a
six-stat total) inflates Gen-I totals and changes the *ordering* among
special-heavy species. Worked examples under the chosen rule, for the
regression tests:

| form | gen 1 | gen 5 | gen 9 |
|---|---:|---:|---:|
| Alakazam (65) | 405 | 490 | 500 |
| Gengar (94) | 425 | 500 | 500 |
| Raticate (20) | 343 | 413 | 413 |
| Butterfree (12) | 305 | 385 | 395 |

A Gen-I total is therefore **on a different scale** from a Gen-II+ total
and the two must never be compared. That invariant holds for free in this
app: BST is only ever used to order candidates *within one value of the
generation filter*, and when the filter is `null` every candidate is scored
at the latest generation (§5.2). State this in the KDoc of whatever
function computes it — it is the one way this feature can go quietly wrong.

Only **200** forms have any `pokemon_stats_past` row, and only **69** have
one that is not the Gen-1 `special` stat. Historical BST is a small
correction, not a broad one; do not over-engineer it.

### 2.3 Assembly — `domain/pokeapi/DatasetAssembly.kt`

`assembleDataset(...)` grows four parameters (keep the existing
one-CSV-per-parameter shape; do not introduce a map). It must now produce:

- `PokemonEntry.baseStatTotal: Int` — the **current** (latest generation)
  BST, on every entry. `0` if the form has no `pokemon_stats` rows at all;
  never null, never negative.
- `ParsedDataset.pastBst: List<PastBstRow>` where
  `PastBstRow(pokemonId: Int, generationId: Int, bst: Int)` — one row per
  `(form, generation)` whose BST differs from `baseStatTotal`, carrying the
  BST **that held through that generation**. Emit the row for each distinct
  `generationId` present in `pokemon_stats_past` for that form, plus a
  Gen-1 row for every form that has a `special` (stat 9) row, since the
  five-stat rule makes Gen-1 differ even when no stat value changed.
  Expected magnitude: a few hundred rows, not 12k. Assert the order of
  magnitude in a test rather than a brittle exact count.
- `AbilityEntry.displayName` and `MoveEntry.displayName` from the name
  CSVs, falling back to `prettify(identifier)`.
- `PokemonEntry.defaultAbility` — **now the display name**
  ("Sap Sipper"), not the slug. This is the §0.2 fix.
- `ParsedDataset.pokemonAbilities: List<PokemonAbilityRow>` where
  `PokemonAbilityRow(pokemonId: Int, abilitySlug: String, displayName: String, isHidden: Boolean, slot: Int)`
  — every row of `pokemon_abilities.csv` (2,941 at the pinned revision),
  which is already downloaded and today only used to derive
  `defaultAbility`. This backs §3.2's canonical ability list.

Keep `resolveDefaultAbility`'s existing fallback (a form with no
`pokemon_abilities` row falls back to its species' default form, then to
`null`) exactly as it is; only the returned string's format changes.

---

## 3. Abilities — correct names, canonical list, custom choice

### 3.1 Display names

Covered by §2.1–2.3. The one thing that must not break: effect lookup.

`domain/ability/AbilityEffects.kt`'s `normalizeAbilityName()` currently
lowercases and replaces whitespace runs with `-`, so `"Sap Sipper"` →
`"sap-sipper"` resolves correctly. It also happens to work for
`"Well-Baked Body"` → `"well-baked-body"`. It does **not** work for a name
carrying a symbol the slug does not have.

Replace it with a symbol-insensitive key, mirroring
`domain/pokeapi/searchKey()`:

```kotlin
/** Lowercase, letters and digits only — so "Well-Baked Body", "well-baked-body"
 * and "wellbakedbody" all resolve to the same effects entry. */
fun abilityKey(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }
```

and key `ABILITY_EFFECTS` by that. Keep `normalizeAbilityName` as a
deprecated alias only if something outside the domain layer still calls it
(`PerPokemonCard.kt` does, for its Wonder Guard check) — otherwise delete
it and update the call site. An unrecognised ability must still degrade to
"no effect", never throw.

**Migration of already-saved data.** `team_member.ability` and
`custom_pokemon.ability` may hold slugs written by earlier builds. Do
**not** rewrite them in a Room migration — they are user data snapshots and
a ROM-hack ability the user typed by hand must survive untouched. Instead
make display tolerant: a helper in `ui/common/` that renders a stored
ability string through the cached ability catalogue when a match is found
(by `abilityKey`) and verbatim otherwise. This also makes a stored slug
from a pre-Phase-7 build render as "Sap Sipper" without touching the
database.

### 3.2 Canonical abilities per species, plus a custom choice

**Data.** New cache table (§8 covers the migration):

```kotlin
@Entity(tableName = "poke_pokemon_ability", primaryKeys = ["pokemonId", "slot"])
data class PokePokemonAbilityEntity(
    val pokemonId: Int,
    val slot: Int,
    val abilitySlug: String,
    val displayName: String,
    val isHidden: Boolean,
)
```

Written inside `PokedexDao.replaceCache()`'s existing single transaction
and wiped by `clearCache()` — **name it explicitly in both**;
`clearAllTables()` remains banned (`CLAUDE.md`).

`PokedexRepository` gains
`suspend fun abilitiesForSpecies(pokemonId: Int): List<SpeciesAbility>`
with `SpeciesAbility(displayName: String, slug: String, isHidden: Boolean, slot: Int)`,
ordered by `isHidden` then `slot`.

**UI — `ui/team/SlotEditorScreen.kt`.** Replace the bare
`EditableComboBox` for the ability field with a two-level control:

1. A dropdown listing, for the currently selected species:
   - each canonical non-hidden ability, by display name;
   - each hidden ability, suffixed with a localised "(hidden)" marker;
   - a final, visually separated entry **"Custom ability…"**.
2. Selecting "Custom ability…" swaps the control for the existing
   `SearchableDropdown` over the whole cached ability catalogue (374
   entries) **with free text still accepted**, exactly as the ability field
   behaves today — a ROM hack can carry an ability that exists in no
   PokéAPI table at all, and the app must not reject it. Offer a way back
   to the canonical list (a "back to canonical" affordance or simply
   reselecting a canonical entry).
3. Any ability that has an entry in `ABILITY_EFFECTS` — canonical or
   custom — is marked in the list with a small badge, so the user can see
   which choices actually move the weakness map. Do not filter the
   non-affecting ones out: the repository owner asked for Moxie and
   Intimidate to remain selectable.

A member with **no** `pokedexId` (a hand-typed or roster Pokémon) has no
canonical list; go straight to the full picker. Same in
`ui/roster/RosterEditorScreen.kt`.

Picking a species still resets the draft's ability to that species'
`defaultAbility` — `SlotEditorScreen`'s existing `selectPokemon` behaviour
is unchanged, only the value it writes is now a display name.

---

## 4. Held items — the defensive subset

Decision taken with the repository owner: add a real item field, but model
effects **only for items that change type effectiveness**. Do **not**
download `items.csv` (59 KB) — the modelled set is small enough to be a
hardcoded table, exactly like `ABILITY_EFFECTS`, and the field itself
accepts free text for everything else.

### 4.1 `domain/item/ItemEffects.kt` (new)

```kotlin
sealed interface ItemEffect {
    /** Air Balloon: Ground moves miss entirely. */
    data class Immunity(val type: PokemonType) : ItemEffect
    /** Iron Ball / Ring Target: cancel the holder's immunities before anything else. */
    data object GroundsHolder : ItemEffect        // Iron Ball
    data object RemovesTypeImmunities : ItemEffect // Ring Target
    /** A resist berry: halves an incoming hit of this type, but only when it is
     *  already super-effective. Chilan Berry is the exception — see [alwaysApplies]. */
    data class ResistBerry(val type: PokemonType, val alwaysApplies: Boolean = false) : ItemEffect
}
```

Modelled items — the 17 type-resist berries plus Chilan, plus the three
immunity-shaped items:

| item | effect |
|---|---|
| Air Balloon | `Immunity(GROUND)` |
| Iron Ball | `GroundsHolder` |
| Ring Target | `RemovesTypeImmunities` |
| Occa / Passho / Wacan / Rindo / Yache / Chople / Kebia / Shuca / Charti / Tanga / Payapa / Kasib / Haban / Colbur / Babiri / Roseli / Chilan Berry | `ResistBerry(<its type>)`; Chilan is `alwaysApplies = true` (Normal) |

Deliberately **not** modelled, with a comment saying why in the file:
Heavy-Duty Boots and Utility Umbrella (entry hazards / weather — neither
touches a type multiplier), Expert Belt and the type-boosting plates/gems
(offensive damage, not the ≥2× coverage threshold).

### 4.2 Application order in `defensiveMultiplier`

`domain/coverage/CoverageEngine.kt`'s `defensiveMultiplier` gains an
`item: String? = null` parameter (defaulted, so no existing call site
breaks) and applies effects in this **exact** order. Write the order into
the KDoc; it is the part a future reader will get wrong.

1. Type-chart product across the defender's one or two types.
2. `RemovesTypeImmunities` (Ring Target): a `0.0` from step 1 becomes
   `1.0`. `GroundsHolder` (Iron Ball): only a Ground-move `0.0` becomes
   `1.0`, and the holder's Levitate/Earth Eater/Air Balloon Ground
   immunities in steps 3–4 are skipped.
3. Ability immunities → return `0.0` (unless step 2 cancelled them).
4. Item `Immunity` (Air Balloon) → return `0.0`.
5. Ability multipliers (Thick Fat, Fluffy, Heatproof, Water Bubble,
   Purifying Salt, Dry Skin's Fire ×1.25).
6. Ability super-effective reducers (Filter / Solid Rock / Prism Armor
   ×0.75; Delta Stream's Flying cap) — applied only when the running value
   is already `> 1.0`.
7. `ResistBerry`: ×0.5 when the running value is `> 1.0`, or
   unconditionally for Chilan.

Every function that today takes `ability` must take `item` alongside it and
thread it through: `defensiveProfile`, `sharedWeaknessCounts`,
`sharedWeaknesses`, `mostVulnerableByType`, and `Scoring.kt`'s
`weaknesses()` / `teamScoringContext()` / `computeCompositeScore()`.
Suggestion candidates built by `memberFromEntry()` have **no** item —
`null` — which keeps candidate scoring conservative and matches how the
app auto-fills only an ability.

### 4.3 Plumbing

- `domain/model/TeamMember.kt`: `val item: String? = null`.
- Room: `team_member.item` and `custom_pokemon.item`, nullable TEXT
  (§8).
- `data/repository/TeamMappers.kt`, `Mappers.kt`: map it.
- `domain/backup/BackupPayload.kt`: add `item` to `BackupTeamMemberDto`
  and to the roster DTO, and bump `CURRENT_BACKUP_FORMAT_VERSION` to `2`.
  A v1 file must still restore (the field is absent → `null`); a v2 file
  in an older build already fails loudly via
  `BackupFormatTooNewException`. Add a test for the v1-restores-into-v2
  path.
- `domain/showdown/ShowdownFormat.kt`: export `"<Species> @ <item>"` when
  an item is set (keep the bare `"@ "` when it is not, so existing
  round-trip tests still pass), and **stop discarding** the item on
  import — `line.substringAfter("@").trim()` when a `@` is present.
- UI: an item field in `SlotEditorScreen` and `RosterEditorScreen`,
  identical in shape to the ability field's free-text-plus-suggestions
  control, suggesting the modelled items from §4.1. Show the item on
  `SlotSummaryCard` and `PerPokemonCard`, and mark the ones with an
  effect the same way §3.2 marks abilities.

---

## 5. BST-aware suggestion ranking

### 5.1 What changes

**Only the tie-break.** `NEW_WEAKNESS_PENALTY`, `AGGRAVATED_WEAKNESS_PENALTY`
and `computeCompositeScore` are untouched, so `ScoringTest`,
`SuggestionEngineTest`'s score assertions and `TeamGeneratorTest` keep
passing unchanged. If any of them break, you changed something you were not
meant to.

```kotlin
private val rankingComparator = compareByDescending<RankedCandidate> { it.bestScore }
    .thenByDescending { it.isFinal }
    .thenByDescending { it.baseStatTotal ?: -1 }   // NEW: stronger first; customs last
    .thenBy { it.entryId }                          // unchanged final tie-break
```

`SuggestionEngineTest`'s "secondary sort on a compositeScore tie is by
ascending catalogue id" **will** need updating — it is now the *tertiary*
sort. Rewrite it as two tests: ties break by BST descending; ties at equal
BST still break by ascending id.

Applied to the screenshot's team, this replaces "Raticate, Persian,
Kangaskhan" (ids 20/53/115, BST 413/440/490) with the highest-BST members
of the same tie group. Add exactly that as a regression test with a small
hand-built pool — do not assert against the real catalogue.

### 5.2 Which BST

Keyed off the **existing** generation dropdown in
`ui/team/analysis/SuggestionFilters.kt` — the repository owner confirmed
this is the intended control:

- `generation = null` ("all generations") → `PokemonEntry.baseStatTotal`,
  the latest-generation value.
- `generation = N` → the historical BST for generation `N` (§2.2).

Note in the code and in `implementation-decisions.md` that this dropdown is
a **pool filter** ("only suggest species introduced in generation N"), so
selecting `N` also guarantees every candidate is a generation-`N` species —
which is precisely why the Gen-I five-stat scale never mixes with any other
(§2.2). If that filter's meaning ever changes, this coupling must be
revisited.

`SuggestionOptions` gains nothing; the BST resolution belongs to the caller
building the pool. Pass a resolved `bstFor: (PokemonEntry) -> Int?` into
`computeSuggestions`, or resolve it into the pool entries before the call —
either is fine, but `domain/suggestion/` must not reach into Room.

### 5.3 Showing it

`domain/suggestion/Suggestion.kt`'s `Suggestion` gains
`val baseStatTotal: Int?`, and `ui/team/analysis/SuggestionCard.kt` renders
it as a new row (`BST: 413`) under the existing score row. A custom
Pokémon has no BST; render nothing rather than `0` or a dash-only row.

While you are in that file: the existing `Punteggio: −0,5` row is opaque to
a user. Leave the value, but add the localised explanatory string
`suggestions_score_hint` — one short line saying the score is
*coverage gained minus weaknesses introduced*, so a negative number stops
reading like an error.

### 5.4 The `findEntry` fix (§0.7)

Build **two maps once** at the top of `computeSuggestions` — one keyed by
`displayName`, one by lowercased `name` — and look candidates up in them
instead of calling `pool.find { … }` per candidate. Behaviour must be
identical, including the current precedence (`displayName` match first).
This is a pure performance fix; assert nothing new about it beyond the
existing tests still passing.

---

## 6. Configurable suggestion count

- `data/settings/SettingsPreferences.kt`: add
  `internal val SUGGESTION_COUNT_KEY = intPreferencesKey("suggestion_count")`,
  a `val suggestionCount: Flow<Int>` that reads it, **coerces into `5..10`**
  (a value outside the range, from a hand-edited store, must clamp, not
  crash — the file's existing "unknown stored value falls back to the
  default" habit) and defaults to `5`, plus
  `suspend fun setSuggestionCount(count: Int)` that clamps on write too.
- `ui/settings/SettingsScreen.kt`: a row in the existing
  `settings_section_team_suggestions` section. Reuse the `−`/`+` stepper
  composable already private to `ui/surprise/SurpriseMeScreen.kt`
  (around line 255) — **promote it to `ui/common/`** rather than copying
  it, and update Surprise Me to use the shared one. Bounds 5 and 10, both
  buttons disabled at their end.
- `ui/team/analysis/AnalysisViewModel.kt`: fold `suggestionCount` into the
  `combine()`. Note that `combine` is already at its 5-argument arity in
  both the `core` and the `uiState` combines — add the new flow to whichever
  one keeps the code readable, creating a small holder data class in the
  same style as the existing `CoreData` if you need a sixth slot.
- `AnalysisUiState` gains `val suggestionCount: Int = 5`;
  `ui/team/analysis/AnalysisScreen.kt:154` becomes
  `state.suggestions.take(state.suggestionCount)`.
  `AnalysisUiState.suggestions` stays unsliced, as its KDoc already
  promises.

New strings in **both** `res/values/strings.xml` (Italian, default) and
`res/values-en/strings.xml`, in the same commit:
`settings_suggestion_count` (label) and `settings_suggestion_count_value`
(`%1$d`), plus everything §3, §4 and §5.3 need.

---

## 7. Closing the engine gaps, and proving it

### 7.1 Defensive abilities

Add to `ABILITY_EFFECTS` the ten abilities of §0.4 and complete `dry-skin`.
Two new `AbilityEffect` variants are required:

```kotlin
/** Filter / Solid Rock / Prism Armor: multiplies an already-super-effective hit. */
data class SuperEffectiveMultiplier(val factor: Double) : AbilityEffect
/** Delta Stream: nothing is super-effective against the holder while it is Flying. */
data object NeverSuperEffective : AbilityEffect
```

Mapping, each traceable to the `short_effect` quoted in §0.4:

- `heatproof` → `Multiplier(FIRE, 0.5, DEFENSIVE)`
- `water-bubble` → `Multiplier(FIRE, 0.5, DEFENSIVE)`
- `purifying-salt` → `Multiplier(GHOST, 0.5, DEFENSIVE)`
- `dry-skin` → existing `Immunity(WATER)` **plus**
  `Multiplier(FIRE, 1.25, DEFENSIVE)`
- `filter`, `solid-rock` → `SuperEffectiveMultiplier(0.75)`
- `prism-armor` → `SuperEffectiveMultiplier(0.75)`
- `primordial-sea` → `Immunity(FIRE)`
- `desolate-land` → `Immunity(WATER)`
- `delta-stream` → `NeverSuperEffective`
- `tera-shell` → keep as `BadgeOnly("Not very effective at full HP")`.
  It is unconditional only at full HP, and the coverage engine has no HP
  concept; modelling it as a real multiplier would be wrong more often
  than right. Say that in the comment.
- `wonder-guard` → **promote from `BadgeOnly` to a real effect**:
  everything that is not super-effective deals `0`. That is what the
  ability does, it is Shedinja's entire identity, and leaving it as a
  badge makes `defensiveProfile` actively wrong for the one Pokémon it
  applies to. Add `data object OnlySuperEffective : AbilityEffect`,
  applied last, and keep `PerPokemonCard`'s existing Wonder Guard badge.

`primordial-sea`/`desolate-land` are field effects in the real games and
apply to *both* sides; modelled here as the holder's own immunity only.
Comment it.

Update `KNOWN_ABILITIES_WITH_EFFECTS` — it is the list §3.2's badge reads
from, and it is currently in display format ("volt absorb"). Regenerate it
from `ABILITY_EFFECTS.keys` instead of maintaining a second hand-written
list that can drift.

### 7.2 Offensive abilities

`offensiveCoverageForMember()` gains an `ability: String?` parameter
(defaulted `null`) and applies, before the ≥2× scan:

- `scrappy` / `minds-eye` → Normal and Fighting attacks treat Ghost as
  `1.0` rather than `0.0`. This does not add a ≥2× cell, so it changes
  nothing in `offensiveCoverageForMember` itself — but it **does** change
  `offensiveMultipliersForMember`, which the offensive grid renders. Apply
  it there and add a test; note in the code that coverage is unaffected so
  the next reader does not "fix" it.
- `refrigerate` / `pixilate` / `aerilate` / `galvanize` → a Normal-type
  attacking move is rewritten to Ice / Fairy / Flying / Electric.
- `normalize` → every attacking move becomes Normal.
- `liquid-voice` → out of scope: it applies to sound-based moves, and the
  app has no move-flag data (`move_flags.csv` is not downloaded). Add it to
  the file's "deliberately not modelled" comment.
- `tinted-lens`, `neuroforce` → deliberately not modelled, with the §0.4
  reasoning in the comment.

Note that the `-ate` abilities only bite when move slots are enabled
(`showMoves`), since type-based coverage has no Normal move to rewrite.

Once these exist, `AbilityEffectSide.OFFENSIVE` is finally constructed and
consumed. If you end up not needing the enum, delete it rather than leaving
a permanently-unreachable branch.

### 7.3 Tests — this is the deliverable for point 3

All pure JVM (`app/src/test/`), no Robolectric except where a DAO is
involved (then `@Config(sdk = [26])`, per `CLAUDE.md`). **Plain ASCII test
method names** — a non-ASCII character in a backtick name breaks
`compileDebugUnitTestKotlin` in this sandbox's POSIX locale.

1. **Type chart, exhaustively.** From the real `type_efficacy.csv` fixture,
   assert all 18 × 18 = 324 cells are present and that every value is one
   of `0.0 / 0.5 / 1.0 / 2.0`. Spot-check the classic asymmetries
   (Ghost→Normal 0, Fighting→Ghost 0, Ground→Flying 0, Fairy→Dragon 2,
   Steel→Fairy 2, Fire→Steel 2, Poison→Steel 0, Electric→Ground 0).
2. **Every dual-type combination.** For all 18 single types and all
   18 × 17 / 2 = 153 unordered dual typings, assert `defensiveMultiplier`
   equals the product of the two chart lookups and that
   `defensiveProfile` partitions all 18 attacking types with no type
   appearing in two buckets and none missing. This is the "1000+ Pokémon"
   guarantee, expressed at the level where it is actually decidable — 171
   typings is the complete space; enumerating 1,351 forms only re-tests the
   same 171 with worse failure messages.
3. **Every catalogue form's typing is one of those 171.** A cheap dataset
   test over the assembled `PokemonEntry` list: `type1 != type2`, `type1`
   non-null, both in `PokemonType.entries`.
4. **One test per ability in `ABILITY_EFFECTS`**, asserting the specific
   multiplier change against a typing where it is observable, including
   the ten new ones and the stacking cases (Thick Fat on a Fire-weak
   typing; Heatproof on a ×4 Fire weakness → ×1; Filter turning ×4 into
   ×3; Wonder Guard zeroing a ×1 and a ×0.5 while leaving ×2 alone).
5. **Item tests**: Air Balloon on a Ground-weak typing; Iron Ball
   cancelling Levitate; Ring Target cancelling a type immunity; each
   resist berry halving only when already super-effective; Chilan always.
   Plus the §4.2 ordering: Ring Target + Levitate, Air Balloon + Iron Ball.
6. **BST tests**: the four worked examples of §2.2 verbatim; a form with no
   past rows returns the same value for every generation; the Gen-1
   five-stat rule; a form absent from `pokemon_stats` yields `0` and
   sorts last.
7. **Ranking tests**: §5.1's two rewritten tie-break tests, plus the
   Raticate/Persian/Kangaskhan regression from a hand-built pool.
8. **Name tests**: `Double-Edge`, `U-turn`, `Will-O-Wisp`, `Self-Destruct`,
   `Well-Baked Body`, `Soul-Heart` come out correct; an identifier with no
   English name row falls back to `prettify`.
9. **Effect-lookup robustness**: `abilityKey` resolves the slug, the
   display name and a mixed-case/space variant to the same effects; an
   unknown string returns `null` and never throws.

### 7.4 Audit findings recorded but not fixed here

Write these into `docs/post-migration-review.md` as a new "Phase 7 audit"
section (that file is already the home for this kind of finding), each with
a one-line reason for deferring:

- `Suggestion.gain` means `newlyCovered.size` in addition mode and
  `offensiveGain` in replacement mode (§0.1).
- `weaknesses()` counts weakness *types*, so ×4 and ×2 score identically
  (§0.1).
- Replacement mode computes `replacementContexts[0]`'s score twice — once
  to seed `bestResult`, once in the loop.
- `deduped`'s `seen.add(speciesName.lowercase())` silently drops a custom
  Pokémon named after a catalogue species.
- Generational type charts and typings (§0.6) → also `ROADMAP.md`.

---

## 8. Room schema v3

One additive migration, `MIGRATION_2_3` in
`data/local/migration/Migrations.kt`, written in the same explicit-`execSQL`
style as `MIGRATION_1_2`. `fallbackToDestructiveMigration()` stays banned.

```sql
ALTER TABLE `team_member`     ADD COLUMN `item` TEXT;
ALTER TABLE `custom_pokemon`  ADD COLUMN `item` TEXT;
CREATE TABLE IF NOT EXISTS `poke_pokemon_ability` (
  `pokemonId` INTEGER NOT NULL, `slot` INTEGER NOT NULL,
  `abilitySlug` TEXT NOT NULL, `displayName` TEXT NOT NULL,
  `isHidden` INTEGER NOT NULL,
  PRIMARY KEY(`pokemonId`, `slot`));
CREATE TABLE IF NOT EXISTS `poke_species_bst_past` (
  `pokemonId` INTEGER NOT NULL, `generationId` INTEGER NOT NULL,
  `bst` INTEGER NOT NULL,
  PRIMARY KEY(`pokemonId`, `generationId`));
ALTER TABLE `poke_species` ADD COLUMN `baseStatTotal` INTEGER NOT NULL DEFAULT 0;
```

Checklist, all of which cost time in this repo before:

- Bump `@Database(version = 3)` and register `MIGRATION_2_3` in
  `di/DatabaseModule.kt`.
- Commit the exported `app/schemas/…/3.json`, and verify your hand-written
  SQL **byte-for-byte** against it — column order, `NOT NULL`, defaults.
- `Migration2To3Test` alongside `Migration1To2Test`, Robolectric +
  `MigrationTestHelper`, `@Config(sdk = [26])`. The schema JSONs are wired
  into `sourceSets["debug"].assets`, **not** `test`'s — do not "fix" that
  (`CLAUDE.md`, "Known gotchas").
- The three new cache tables/columns must be added to **both**
  `PokedexDao.replaceCache()` and `PokedexDao.clearCache()`, by name.
- The `poke_cache_meta.schemaVersion` constant must be bumped so every
  existing install re-syncs and picks up base stats and the per-form
  ability rows. Without this the new tables stay empty until the user
  manually forces a resync, and the BST tie-break silently no-ops. This
  is the single most likely way to ship this phase broken.
- `data/debug/DebugSeeder.kt` still compiles and seeds valid rows.
- Never introduce a `@Insert(onConflict = REPLACE)` upsert on a table that
  is the parent of a cascading foreign key (`CLAUDE.md`).

---

## 9. Task order

Each step should build and test green before the next. Commit per step.

1. **§2** — dataset: 4 new CSVs, parsers, assembly, `PokemonEntry.baseStatTotal`,
   past-BST rows, per-form ability rows, correct display names. Tests 7.3.6
   and 7.3.8. No UI yet.
2. **§8** — Room v3, migration, DAO wiring, `schemaVersion` bump,
   `Migration2To3Test`.
3. **§3** — ability display names end to end, `abilityKey`, canonical +
   custom ability picker in slot and roster editors. Test 7.3.9.
4. **§7.1 + §7.2** — ability effect gaps, offensive abilities, the full
   engine test suite (7.3.1–7.3.4). This is point 3 of the request; it is
   also the step most likely to surface a real bug, so do it before the
   ranking work depends on it.
5. **§4** — item field: model, Room columns (already added in step 2),
   mappers, backup v2, Showdown round-trip, UI, `ItemEffects`, tests 7.3.5.
6. **§5** — BST tie-break, `findEntry` map, suggestion card BST row and
   score hint. Tests 7.3.7.
7. **§6** — the 5–10 suggestion-count setting, shared stepper.
8. **Docs** — `CHANGELOG.md` under `## [Unreleased]` (one bold-lead bullet
   per user-visible change: correct ability names, canonical/custom ability
   choice, held items, stronger-first suggestions, configurable suggestion
   count, and the ability-accuracy fixes); `docs/implementation-decisions.md`
   "Phase 7"; `docs/post-migration-review.md` "Phase 7 audit" (§7.4);
   `docs/test-plan.md` a Phase 7 section; `docs/plan/reference-pokedata.md`
   §2 and its size figures; `CLAUDE.md`'s "~208 KB" line, its architecture
   tree (`domain/item/`), its phase list; `docs/plan/README.md`'s order
   table; `docs/STATUS.md`; `ROADMAP.md` (generational type charts).

## 10. Definition of done

- `./gradlew testDebugUnitTest lintDebug assembleDebug` green, and CI green
  on the PR. Never report a build as passing that you did not run — this
  sandbox may have no Android SDK; check `$ANDROID_HOME` and
  `command -v sdkmanager` first and fall back to CI.
- Every new user-visible string present in **both** `values/strings.xml`
  and `values-en/strings.xml`.
- A fresh install and an **upgrade from a v2 database** both re-sync and
  show BSTs — verify the upgrade path by hand and record it in
  `docs/test-plan.md`.
- `docs/test-plan.md` has a Phase 7 section covering: the ability picker
  (canonical, hidden, custom, ROM-hack free text), item entry and its
  effect on the per-Pokémon card, the suggestion count setting, and that
  suggestions on a solid team now lead with strong Pokémon rather than
  Raticate.
- No new Gradle dependency. Everything here is hand-rolled parsing and
  Compose, consistent with every phase before it.
