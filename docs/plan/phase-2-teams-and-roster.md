# Phase 2 — Teams, slots and the custom roster

**Goal:** the user can create teams, fill their six slots with real Pokémon
from the synced catalogue, override types, set abilities and moves, and save
Pokémon to a reusable custom roster. No analysis yet.

**Depends on:** Phase 1.

---

## 1. Room user schema — version 2

Additive migration `MIGRATION_1_2`. `fallbackToDestructiveMigration()` is
banned; this is the first table that holds data the user cannot re-create.

```
team               id TEXT PK (UUID), name, createdAt (epoch millis), position INT
team_member        id TEXT PK (UUID), teamId FK -> team ON DELETE CASCADE,
                   slotIndex INT (0..5), pokedexId INT NULL, speciesName TEXT,
                   type1 TEXT, type2 TEXT NULL, ability TEXT NULL, isCustomSaved INT
team_member_move   id TEXT PK (UUID), memberId FK -> team_member ON DELETE CASCADE,
                   moveIndex INT (0..3), name TEXT, typeName TEXT,
                   power INT NULL, damageClass TEXT, isCustom INT
custom_pokemon     id TEXT PK (UUID), name TEXT, type1 TEXT, type2 TEXT NULL,
                   ability TEXT NULL, createdAt
custom_pokemon_move  id TEXT PK, customId FK -> custom_pokemon ON DELETE CASCADE,
                   moveIndex INT, name, typeName, power INT NULL, damageClass, isCustom
```

Design notes that are decisions, not suggestions:

- **`pokedexId` is nullable and carries no foreign key** into the cache.
  Wiping the cached catalogue must never alter or blank a saved team. The
  slot stores `speciesName`, `type1`, `type2` and `ability` as
  **denormalized snapshots**; `pokedexId` exists only so the sprite resolver
  has an id, and a custom Pokémon simply has none.
- **Empty slots are rows that do not exist**, not placeholder rows. The PWA
  models `members` as a length-6 array with nulls; in Room, absence is the
  natural encoding and `slotIndex` keeps identity stable. The repository
  maps between the two.
- Moves are a child table rather than four columns, so the "Enable move
  slots" toggle and the Showdown importer do not have to care how many there
  are. `moveIndex` is 0–3.

> **`@Insert(onConflict = REPLACE)` is not an update on a row with
> `ON DELETE CASCADE` children.** It compiles to `INSERT OR REPLACE`, which
> deletes the conflicting row first — silently cascade-deleting its
> children. This was a real on-device bug in Hall of Memories: editing a
> parent row wiped every child. `TeamDao.upsert()` and
> `CustomPokemonDao.upsert()` must check `exists(id)` and dispatch to
> `@Insert` or a real `@Update`. A save that deletes and fully reinserts all
> children in the same transaction is safe; anything else is not. Check any
> `REPLACE`-based upsert you write against this before assuming it is fine.

## 2. Repositories

```kotlin
interface TeamRepository {
    val teams: Flow<List<Team>>
    fun team(id: String): Flow<Team?>
    suspend fun createTeam(name: String): String
    suspend fun renameTeam(id: String, name: String)
    suspend fun deleteTeam(id: String)
    suspend fun saveMember(teamId: String, slotIndex: Int, member: TeamMember)
    suspend fun clearSlot(teamId: String, slotIndex: Int)
}

interface CustomPokemonRepository {
    val roster: Flow<List<TeamMember>>
    suspend fun save(member: TeamMember)
    suspend fun delete(id: String)
}
```

`saveMember` deletes and reinserts the member's moves inside the same
transaction as the member write. Ids are `String` UUIDs generated in the
repository, never in the ViewModel or the DAO.

`domain/model/Team.kt` and `TeamMember.kt` mirror `legacy-web`'s
`src/types/index.ts` shapes: `Team.members` is a `List<TeamMember?>` of
length 6, `TeamMember.moves` a `List<PokemonMove?>` of length 4. Keeping the
nullable-slot shape in the domain model is what lets the ported engines in
Phases 3 and 4 be line-for-line comparable to their TypeScript originals.

## 3. Screens

### Teams (`ui/teams/`)

Replaces Phase 0's empty state. A list of teams, each row showing the name
and six sprite thumbnails. Create (a name dialog), rename, delete (a
confirmation dialog), tap to open. `TeamsViewModel` `combine()`s
`TeamRepository.teams` with the sync state from Phase 1.

Port the strings from `legacy-web`'s `TeamsPage`, `NewTeamModal` and
`DeleteTeamModal` i18n keys.

### Team detail — Pokémon tab (`ui/team/`)

Two tabs; the Analysis tab is a placeholder until Phase 3. The Pokémon tab
shows six slots. Each filled slot shows sprite, name, both type badges, the
ability, and — when "Enable move slots" is on — four move rows.

Editing a slot opens a slot editor with:

- **Species picker** — the searchable dropdown, backed by
  `PokedexRepository.searchSpecies`. Selecting a species fills `pokedexId`,
  `speciesName`, both types and pre-fills `ability` from `defaultAbility`.
- **Type overrides** — two dropdowns over the 18 types, second one
  clearable. An override changes **only this slot** and must never write
  back into the cached entry. This is a ROM-hack feature and one of the
  reasons the app exists.
- **Ability picker** — searchable, over the cached abilities, free text
  accepted.
- **Move slots** — four searchable move pickers. Selecting a cached move
  fills type, power and damage class. A move the user types that is not in
  the cache becomes `isCustom = true`, `type = NORMAL`, `damageClass =
  PHYSICAL`, `power = null` — matching the PWA's `MoveSlot` exactly (its
  custom-move handler is `type: move?.type ?? 'normal', damageClass:
  move?.damageClass ?? 'physical'`; an earlier draft of this line said
  `STATUS`, which was a paraphrase error — see
  `docs/implementation-decisions.md`, "Phase 2"), and the editor exposes
  type / power / damage-class fields so the user can complete it.
- **Save to roster** — writes the slot's Pokémon into `custom_pokemon` and
  sets `isCustomSaved`.

The "Enable move slots" toggle is per-screen UI state persisted in settings
(`showMoves`), exactly as today.

> **The system back gesture bypasses a screen's custom `onBack`** — Compose
> Navigation's callback just calls `popBackStack()`. The slot editor
> discards unsaved edits on back, so it needs an explicit `BackHandler`.

### Custom roster (`ui/roster/`)

List, create, edit, delete. Same editor as a slot, minus the species picker:
a custom Pokémon has a free-text name, two types, an optional ability and
optional moves. Custom Pokémon have no `pokedexId` and render the
placeholder sprite.

## 4. Searchable dropdown

`ui/common/SearchableDropdown.kt`, one implementation used by every picker.
The contract from `native-spec.md`, restated because it is easy to get
wrong:

- Closed/focused with an empty query → **no items**, and a
  "Start typing to search…" placeholder inside the field.
- From the first typed character → **all** matches, no cap, no pagination.
- The list scrolls internally (bounded height) instead of growing the sheet.
- Each row shows the dropdown-context sprite thumbnail where it has one.

`legacy-web/src/components/SearchableDropdown/` is the behavioural
reference.

## 5. Debug seeding

`DebugSeeder` now has something to do: behind `BuildConfig.SEED_DEBUG_DATA`,
seed two teams — one partial, one full six — plus two custom Pokémon, so
Phases 3 and 4 have something to analyse without hand-entering it every
launch. Debug builds only; never in a release build; never surfaced in the
UI as "sample data".

## Deliverables

- [ ] Room v2 + `MIGRATION_1_2` + committed schema JSON.
- [ ] `TeamRepository`, `CustomPokemonRepository`, DAOs with safe upserts.
- [ ] Teams list with create / rename / delete / open.
- [ ] Team detail, Pokémon tab, six slots, full slot editor.
- [ ] Type overrides, ability picker, four move slots, custom moves.
- [ ] Custom roster CRUD.
- [ ] `SearchableDropdown` meeting the UX contract.
- [ ] `DebugSeeder` seeding teams and roster in debug builds.
- [ ] Strings in **both** locales.
- [ ] `CHANGELOG.md`, `docs/test-plan.md`, `docs/implementation-decisions.md`.

## Tests

- `TeamDaoTest` (Robolectric, `@Config(sdk = [26])`) — create/read/update/
  delete; **editing a team does not delete its members** (the `REPLACE`
  gotcha, asserted explicitly); deleting a team cascades to members and
  moves; `slotIndex` identity survives an edit.
- `CustomPokemonDaoTest` — same shape.
- `TeamRepositoryTest` (Robolectric) — saving a member replaces exactly its
  own moves; clearing a slot leaves the other five untouched; a wiped
  Pokédex cache leaves every team byte-identical (assert this one directly —
  it is the denormalization invariant).
- `MappersTest` (pure) — entity ↔ domain round-trip, including the
  length-6 nullable slot list and the length-4 nullable move list.
- `Migration1To2Test` (Room testing) — a v1 database opens at v2 with its
  cache rows intact.

## Not in this phase

Coverage analysis, suggestions, the generator, Showdown import/export,
backup. The Analysis tab is a placeholder that says so.
