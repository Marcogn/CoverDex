# Project Status

A snapshot of what's implemented, what's known to be missing, and any loose
ends — written for whoever (human or agent) picks this project up next.
Last verified 2026-09-04, at the end of Phase 4 of the native Android
migration. Re-verify anything here before relying on it — this file goes
stale the moment someone ships a change without updating it. It complements,
not replaces, the other docs: [`CLAUDE.md`](../CLAUDE.md) for rules and
invariants, [`docs/plan/README.md`](plan/README.md) for the phase-by-phase
build, [`docs/plan/native-spec.md`](plan/native-spec.md) for what the
finished app must do, [`ROADMAP.md`](../ROADMAP.md) for the deferred
backlog.

## What CoverDex is right now

A native Android app that downloads and caches the full Pokémon catalogue in
the background, lets the user build real teams (create/rename/delete a team,
fill its six slots from the synced catalogue or type a ROM-hack-only
species/ability/move by hand, override a slot's types, save any slot into a
reusable custom roster), analyses those teams (every team's Analysis tab
shows the ported coverage engine's full output — basis notice, per-Pokémon
breakdown, offensive/defensive grids, shared weaknesses, uncovered types),
and now suggests how to improve them: the Analysis tab's seventh section
ranks real candidates to add or swap in, filterable by generation/custom-
Pokémon/legendaries, and a new Surprise Me screen generates a whole team
from scratch around optional locked anchors and category constraints. That
is the full feature set through Phase 4 of
[`docs/plan/README.md`](plan/README.md); Showdown import/export and the
rest of Settings are Phase 5.

## What's implemented

- **Everything from Phase 1** — the dataset sync, the Room v1 cache, the
  Settings → Data section, the non-blocking sync banner. See git history;
  not repeated here.
- **Room schema v2** (`team`, `team_member`, `team_member_move`,
  `custom_pokemon`, `custom_pokemon_move`), reached from v1 by a hand-written
  `MIGRATION_1_2`, verified byte-for-byte against Room's own exported schema
  and exercised by `Migration1To2Test` (Robolectric + `MigrationTestHelper`).
  Getting that test running at all surfaced a real AGP/Robolectric gap — see
  `docs/implementation-decisions.md`, "Phase 2".
- **`TeamRepository` and `CustomPokemonRepository`**, both transactional,
  both built on `Team`/`TeamMember`/`PokemonMove` domain models that mirror
  `legacy-web`'s fixed-size nullable-slot shapes exactly (`Team.members`:
  length 6; `TeamMember.moves`: length 4). Species/type/ability/move data on
  every slot is a denormalized snapshot — wiping the Pokédex cache never
  touches a saved team or roster entry (asserted directly in
  `TeamRepositoryTest`).
- **The Teams screen** — real CRUD: create (a name dialog), rename, delete
  (a confirmation dialog), tap to open. A new team opens immediately after
  creation.
- **The team detail screen** — two tabs (Pokémon / Analysis, the latter a
  placeholder), a six-slot grid, and the persisted, app-wide "Enable move
  slots" toggle.
- **The slot editor** — a real navigation destination (not a dialog): a
  species picker (`SearchableDropdown`, backed by `PokedexRepository`), two
  type-override dropdowns, a free-text ability field
  (`EditableComboBox`, ported from Hall of Memories — a materially
  different contract from `SearchableDropdown`, see
  `docs/implementation-decisions.md`), four move slots (each accepting a
  cached move or a typed custom one, defaulting to Normal/Physical per the
  verified `legacy-web` behaviour), "Save as custom" and "Clear slot".
  Nothing is written until an explicit Save; back (system gesture or the
  top bar) always discards the in-progress draft.
- **The custom roster** — its own screen (list, create, edit, delete) and
  its own editor, "the same editor as a slot, minus the species picker."
- **`PokemonType.displayName()`/`DamageClass.displayName()`** — the first
  time either enum renders as text rather than a sprite; all 18 type names
  and the three damage-category names were checked individually against
  Bulbapedia rather than assumed.
- **`DebugSeeder`** now seeds two teams (one partial, one full six) and two
  custom roster entries behind `BuildConfig.SEED_DEBUG_DATA`, wired from
  `CoverDexApplication.onCreate`; a no-op once any real team exists, so it
  never re-seeds over real user data or duplicates itself on relaunch.
- **The coverage engine** (`domain/coverage/CoverageEngine.kt`) and ability
  effects (`domain/ability/AbilityEffects.kt`) — direct ports of
  `coverageEngine.ts`/`abilityEffects.ts`, same function names and
  signatures, `CoverageEngineTest` porting all 36 of the TypeScript
  oracle's cases with the same expected values.
- **The Analysis tab** — all seven sections from
  `phase-3-analysis.md` §2, in order: the coverage basis notice
  (moves/types/mixed, matching `TeamDetailPage.tsx`'s exact wording),
  per-Pokémon breakdown (expandable cards), the offensive and defensive
  18-type grids (pinned name column, independently horizontally
  scrolling, `CoverageGridTable` shared between both), shared weaknesses
  with counts, uncovered types, and — as of Phase 4 — real suggestions.
  `AnalysisViewModel` applies the "Enable move slots" gate before the
  engine ever sees a member's moves.
- **The suggestion engine** (`domain/suggestion/`) — a direct port of
  `suggestionEngine.ts`'s `computeSuggestions`/`memberFromEntry`, with the
  composite-score weights shared with the generator via `Scoring.kt`. One
  intentional deviation from the TypeScript: the generation filter uses
  each candidate's real `generationIntroduced` instead of hardcoded
  Pokédex-id ranges (`docs/plan/reference-pokedata.md` §4).
- **The Suggestions section** (section 7 of the Analysis tab) — up to five
  ranked cards (addition mode below six members, replacement mode at a
  full six), each showing sprite, types, gain, composite score, newly
  covered types and new weaknesses; a generation dropdown and "include
  custom Pokémon"/"exclude legendaries" toggles; tapping a card writes it
  into the team immediately.
- **The team generator** (`domain/generator/`) — a direct port of
  `teamGenerator.ts`'s `buildEligiblePool`/`generateTeam`/`regenerateSlot`/
  `STARTER_FINALS`, with an injectable `kotlin.random.Random` (the
  TypeScript calls `Math.random()` directly and so can only test
  probabilistically; the Kotlin tests assert the same properties
  deterministically across a fixed set of seeds).
- **The Surprise Me screen** (`ui/surprise/`, reached from Teams via the
  dice icon) — lock 0-5 anchor Pokémon, tune starter/legendary-mythical/
  Mega/Dynamax/custom constraint counters, Generate, regenerate a single
  slot or the whole team, then Keep to create a brand-new team from the
  result. One scrollable screen, not `SurpriseMeModal.tsx`'s three-step
  wizard — see `docs/implementation-decisions.md`, "Phase 4".
- **`./gradlew testDebugUnitTest lintDebug assembleDebug`** all green in one
  invocation — 191 unit tests, 0 failures (verified locally with a
  temporary, non-persistent SDK this session, same as every prior phase).

## What's known to be missing

Everything the app is supposed to do beyond teams, the roster, coverage
analysis and suggestions. In order, per [`docs/plan/README.md`](plan/README.md):

- **Phase 5** — Showdown import/export, the rest of Settings, local backup.
- **Phase 6** — signing, the release pipeline, `legacy-web/` deleted.

Also deliberately deferred (not a bug, see `docs/implementation-decisions.md`):

- **Phase 2** — the slot editor's species picker never offers the custom
  roster as a search source, unlike `legacy-web`'s own "Include saved
  custom Pokémon in search" checkbox — `phase-2-teams-and-roster.md`'s own
  description of the species picker never mentions it. "Save as custom"
  (writing a slot *into* the roster) is unaffected.

[`ROADMAP.md`](../ROADMAP.md) points at
[`docs/plan/native-spec.md`](plan/native-spec.md)'s "Explicitly out of
scope" for what's deliberately never planned (Play Store submission, iOS,
a backend).

## Known regressions

None yet. The one deliberate, non-regression gap: **upgrading from the old
Capacitor build loses saved teams and the custom roster.** This is a decided
trade-off (see `docs/implementation-decisions.md`), not a bug, but it will
read as one to a real user with existing data unless Phase 6's release notes
say so plainly before they update.

## Verifying project health

```bash
export ANDROID_HOME=...    # if a local SDK is available; otherwise rely on CI
./gradlew testDebugUnitTest   # 191 tests as of Phase 4
./gradlew lintDebug
./gradlew assembleDebug
```

`docs/test-plan.md` has the on-device manual steps this doesn't cover —
locale switching, dynamic colour, the launcher icon, install-over-upgrade,
the real first-launch sync, team/slot/roster CRUD, the slot editor's
discard-on-back behaviour, the debug seed data, the Analysis tab's basis
notice, both coverage grids' independent horizontal scrolling, type
overrides propagating into the analysis, and (new this phase) the
Suggestions section's addition/replacement modes and filters, and every
Surprise Me interaction (anchors, constraints, generate, regenerate, Keep).

`legacy-web/` has its own, separate health check:

```bash
cd legacy-web && npm ci && npm test   # 175 tests
```
