# Phase 1 — The dataset sync, the Room cache and sprites

**Goal:** the app downloads the full Pokémon catalogue in ~8 requests and
~208 KB, stores it in Room, and can render any Pokémon's sprite from its id
alone. Settings shows the dataset's state and can re-sync or wipe it.

**Depends on:** Phase 0.

**Read first:** [`reference-pokedata.md`](reference-pokedata.md), in full.
It is the contract this phase implements and it contains every measured
number, every field derivation and every gotcha. This file does not repeat
it.

---

## 1. The pure layer — `domain/`

No Android imports anywhere in this section. All of it is unit-tested on the
plain JVM.

```
domain/model/PokemonType.kt        enum, 18 entries, kebab `apiName` + ordinal id 1..18
domain/model/DamageClass.kt        enum PHYSICAL, SPECIAL, STATUS
domain/model/PokemonEntry.kt       id, name, displayName, speciesId, speciesName,
                                   types (Pair<PokemonType, PokemonType?>),
                                   isLegendary, isMythical, isFinalEvolution,
                                   generationIntroduced: Int, defaultAbility: String?
domain/model/MoveEntry.kt          id, name, displayName, type, power: Int?, damageClass
domain/model/AbilityEntry.kt       id, name, displayName
domain/model/TypeChart.kt          value class over Map<PokemonType, Map<PokemonType, Double>>
                                   with `fun multiplier(attacker, defender): Double`

domain/pokeapi/CsvParser.kt        header-aware CSV reader (see below)
domain/pokeapi/DatasetParsers.kt   one pure function per file: parsePokemon(csv), parseSpecies(csv), …
domain/pokeapi/DatasetAssembly.kt  joins the parsed rows into PokemonEntry/MoveEntry/TypeChart
domain/pokeapi/SyncStage.kt        enum + DATASET_SCHEMA_VERSION const
domain/sprite/SpriteUrlResolver.kt pure; returns an ordered candidate list
```

### `CsvParser`

Roughly 40 lines. Reads the header row, returns rows as
`Map<String, String>` (or an index-by-name accessor — either is fine, but
**never positional indexing**; upstream adds columns). Must handle:

- quoted fields containing commas and escaped quotes,
- empty trailing fields (`pokemon_species.csv` ends rows with an empty
  `conquest_order`),
- `\r\n` as well as `\n`,
- an empty string meaning **absent**, not zero — `moves.power` is the case
  that matters.

Do not add a CSV dependency for this.

### `DatasetAssembly`

Implements `reference-pokedata.md` §3 exactly. The two derivations worth
calling out because they are computed, not read:

- **`isFinalEvolution`**: build the set of every non-empty
  `evolves_from_species_id`; a species is final iff its id is not in that
  set. Expect **568 finals of 1025 species**.
- **`defaultAbility`**: the `pokemon_abilities` row for that form with
  `is_hidden = 0` and the lowest `slot`. **11 forms (all id ≥ 10301) have no
  row at all** — fall back to the same species' default form
  (`pokemon.is_default = 1`), then to null.

Filter `types.csv` to ids 1–18. `stellar`, `unknown` and `shadow` never
enter the app.

### `SpriteUrlResolver`

```kotlin
fun resolveSpriteCandidates(pokemonId: Int, context: SpriteContext): List<String>
```

- `CARD` → `other/home/{id}.png`, `other/official-artwork/{id}.png`,
  `{id}.png`
- `DROPDOWN` → `{id}.png`

Pure and stateless. The 404-walking lives in the composable (§4), not here.
Also expose `typeBadgeUrl(type)` built from the type's id, matching
`legacy-web/src/data/typeSprites.ts` — the ids are the same as `types.csv`'s.

## 2. The network and cache layer — `data/`

```
data/pokeapi/PokeDataClient.kt      HttpURLConnection, fetches the 8 CSVs
data/pokeapi/DatasetSyncManager.kt  orchestrates fetch → parse → assemble → one transaction
data/local/entity/PokeSpeciesEntity.kt
data/local/entity/PokeMoveEntity.kt
data/local/entity/PokeAbilityEntity.kt
data/local/entity/TypeEfficacyEntity.kt
data/local/entity/PokeCacheMetaEntity.kt
data/local/dao/PokedexDao.kt
data/local/CoverDexDatabase.kt      version 1, "coverdex.db"
data/local/Converters.kt
data/repository/PokedexRepositoryImpl.kt
di/DatabaseModule.kt, di/RepositoryModule.kt, di/NetworkModule.kt, di/CoroutinesModule.kt
```

### `PokeDataClient`

Hand-rolled `HttpURLConnection`, styled on Hall of Memories'
`PokeApiClient`. One `const val DATASET_REVISION` holds the pinned
`PokeAPI/pokeapi` commit sha (`d4f9a4af58ade123fbc0558f68b1c69daa97d9e4`, as
resolved on 2026-09-04) and the base URL is built from it.

**Do not set `Accept-Encoding`.** Left alone the connection negotiates gzip
and decompresses transparently; setting it by hand hands you raw gzip bytes.
CSV compresses well, so this is worth several times more here than it was in
the sibling app.

The 8 files are independent — fetch them concurrently on the IO dispatcher
rather than in sequence. With one retry each, the whole sync should be a
couple of seconds on a normal connection.

### Cache tables

```
poke_species        id PK, name, displayName, searchName, speciesId, speciesName,
                    type1, type2 (nullable), isLegendary, isMythical,
                    isFinalEvolution, generationIntroduced, defaultAbility (nullable),
                    isDefaultForm
poke_move           id PK, name, displayName, searchName, typeName, power (nullable), damageClass
poke_ability        id PK, name, displayName, searchName
type_efficacy       attacker + defender composite PK, factor REAL
poke_cache_meta     id PK (always 1), schemaVersion, datasetRevision, syncedAt (epoch millis),
                    speciesCount, moveCount
```

`searchName` is lowercase with non-alphanumerics stripped, so the picker's
`LIKE` query matches "mrmime", "mr mime" and "mr-mime" alike — carried over
from Hall of Memories. Index it.

`type2` is nullable, not an empty string. `power` is nullable, not 0.

### `DatasetSyncManager`

Exposes `Flow<SyncState>` with a `SyncStage` (`DOWNLOADING`, `PARSING`,
`WRITING`, `DONE`, `FAILED(reason)`) and a coarse progress fraction. Rules:

- The write is **one Room transaction**: all five cache tables plus the meta
  row, or nothing. A half-written cache reporting itself synced is worse
  than no cache.
- On success, write `poke_cache_meta` with `DATASET_SCHEMA_VERSION` and
  `DATASET_REVISION`.
- `PokedexRepository.isCacheUsable()` is false when the meta row is missing,
  when its `schemaVersion` differs, or when its `datasetRevision` differs.
  A mismatch means "absent" and triggers a silent re-sync — **never** a
  crash on a column that isn't there.
- Wiping the cache deletes the five cache tables **by name**. There are no
  user tables yet, but `clearAllTables()` must never appear in this
  codebase, so do not write it here either.
- The sync never blocks the UI behind a full-screen loader. This is the
  whole point of the phase: the PWA's `LoadingScreen` gate does not survive
  the port. Show inline progress on the Teams screen and let Settings stay
  reachable.

## 3. Repository

```kotlin
interface PokedexRepository {
    val cacheStatus: Flow<CacheStatus>            // synced-at, counts, revision, usable
    fun searchSpecies(query: String, limit: Int? = null): Flow<List<PokemonEntry>>
    fun searchMoves(query: String): Flow<List<MoveEntry>>
    fun searchAbilities(query: String): Flow<List<AbilityEntry>>
    suspend fun speciesById(id: Int): PokemonEntry?
    suspend fun speciesByName(name: String): PokemonEntry?
    suspend fun allSpecies(): List<PokemonEntry>   // the suggestion/generator pool
    suspend fun typeChart(): TypeChart
    suspend fun sync(): Result<Unit>
    suspend fun wipeCache()
}
```

`searchSpecies` with a blank query returns **empty**, not everything — that
is the dropdown UX contract from `native-spec.md`, enforced at the
repository so no picker can get it wrong. `searchSpecies(query, limit = null)`
returns **all** matches: no cap, no pagination.

## 4. UI

```
ui/common/PokemonSprite.kt      Coil + the ordered candidate list, advances on error
ui/common/TypeBadge.kt          type icon by id
ui/settings/DataSectionRow.kt   dataset status, "Sync now", "Clear cached data"
ui/teams/SyncBanner.kt          inline progress while a sync runs
```

`PokemonSprite` is the stateful half of the sprite story: it holds a
candidate index, renders `candidates[index]` through Coil, and increments the
index in `onError` until the list runs out, then draws the placeholder. Keep
the resolver pure — that split is what makes the URL logic testable.

Settings → Data shows: last sync time, species and move counts, the pinned
dataset revision (short sha), and the two buttons. Wiping asks for
confirmation.

## 5. Debug seeding

`data/debug/DebugSeeder.kt` behind `BuildConfig.SEED_DEBUG_DATA`, debug
builds only, as in Hall of Memories. In this phase it does nothing useful
yet — Phase 2 gives it teams to seed. Create the seam now.

## Deliverables

- [ ] All of `domain/pokeapi/` and `domain/sprite/`, pure and tested.
- [ ] `PokeDataClient` fetching the 8 pinned CSVs concurrently, with retry.
- [ ] Room v1 with the five cache tables and a schema JSON committed.
- [ ] `DatasetSyncManager` writing transactionally and reporting stages.
- [ ] `PokedexRepository` + Hilt wiring.
- [ ] `PokemonSprite`, `TypeBadge`, Settings → Data, the sync banner.
- [ ] Strings in **both** `values/` and `values-en/`.
- [ ] `CHANGELOG.md`, `docs/test-plan.md`, `docs/implementation-decisions.md`.

## Tests

Pure JVM unless noted.

- `CsvParserTest` — quoted fields, embedded commas, escaped quotes, empty
  trailing fields, CRLF, empty string ≠ zero.
- `DatasetParsersTest` — one small fixture per file, checked into
  `src/test/resources/`. **Build the fixtures from real rows**, not invented
  ones: take the first few lines of each real CSV plus one awkward row
  (a form with id > 10000, a null-power move, a species with no
  `evolves_from_species_id`).
- `DatasetAssemblyTest` — the joins, and specifically:
  - a dual-type form gets its types in `slot` order,
  - a form with no `pokemon_abilities` row falls back to its species'
    default form,
  - `isFinalEvolution` is false for a species another species evolves from,
  - the type chart contains only ids 1–18 and maps `damage_factor` 0/50/100/200
    onto 0.0/0.5/1.0/2.0.
- `SpriteUrlResolverTest` — candidate order for both contexts; the exact URL
  strings from `reference-pokedata.md` §5; type badge URLs match
  `legacy-web/src/data/typeSprites.ts`.
- `PokedexDaoTest` (Robolectric, **`@Config(sdk = [26])`**) — insert, search
  by `searchName` with the three "mr mime" spellings, blank query returns
  empty, wipe deletes cache rows.
- `DatasetSyncManagerTest` (Robolectric, `@Config(sdk = [26])`) — a failing
  fetch leaves the database untouched; a successful one writes the meta row;
  a schema-version mismatch reports the cache unusable.

### Parity check against `legacy-web`

Not a unit test — a one-off verification to run in the session and record in
`docs/implementation-decisions.md`:

```bash
cd legacy-web && npm ci && npm test        # confirms the oracle still passes
```

Then confirm the CSV-derived catalogue agrees with the mirror-derived one on
the numbers in `reference-pokedata.md` §3: 1351 forms, 1025 species, 937
moves, 568 final evolutions, 71 legendary, 23 mythical, a 324-cell type
chart. If any of these has drifted upstream since 2026-09-04, **update
`reference-pokedata.md` with the new measurement and the date** rather than
making the test tolerant.

## Not in this phase

Teams, the roster, any analysis, any suggestion. The only thing the user can
do at the end of this phase is watch the dataset download and see how many
Pokémon it found.

## Known gotchas for this phase

- `Accept-Encoding` on `HttpURLConnection` — see §2.
- Robolectric needs `@Config(sdk = [26])` — see Phase 0 §6.
- **This sandbox's default locale is POSIX (non-UTF-8).** A non-ASCII
  character in a Kotlin backtick test method name breaks
  `compileDebugUnitTestKotlin` with an opaque `InvalidPathException`. Keep
  test names plain ASCII.
- `Json.encodeToString(value)` without
  `import kotlinx.serialization.encodeToString` binds to the wrong overload
  and fails with a misleading type error. (Not needed for CSV, but Phase 5's
  backup will hit it.)
