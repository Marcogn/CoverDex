# Project Status

A snapshot of what's implemented, what's known to be missing, and any loose
ends — written for whoever (human or agent) picks this project up next.
Last verified 2026-09-04, at the end of Phase 1 of the native Android
migration. Re-verify anything here before relying on it — this file goes
stale the moment someone ships a change without updating it. It complements,
not replaces, the other docs: [`CLAUDE.md`](../CLAUDE.md) for rules and
invariants, [`docs/plan/README.md`](plan/README.md) for the phase-by-phase
build, [`docs/plan/native-spec.md`](plan/native-spec.md) for what the
finished app must do, [`ROADMAP.md`](../ROADMAP.md) for the deferred
backlog.

## What CoverDex is right now

A native Android app that downloads and caches the full Pokémon catalogue
in the background — a few seconds, ~208 KB, never blocking the UI — and
shows an empty Teams screen behind a three-item navigation drawer (Your
Teams / Custom Pokémon / Settings), a working theme picker, a working
language picker, and a Settings → Data section showing the cache's real
status. No team can be created yet and nothing in the app renders a
sprite or a species name — the catalogue is fetched and cached, but
nothing consumes it visibly until Phase 2.

That is the intended state at the end of Phase 1 of
[`docs/plan/README.md`](plan/README.md) — not a partial feature, a
completed one. The functional app (teams, analysis, suggestions, the
generator, import/export, backup) does not exist yet; it is built out
phase by phase starting with Phase 2.

## What's implemented

- **Everything from Phase 0** — Gradle project, theme, language, drawer
  navigation, CI, `legacy-web/` parked as the behavioural reference. See
  git history for that phase's detail; not repeated here.
- **The dataset sync.** `PokeDataClient` fetches 8 pinned CSV files from a
  specific `PokeAPI/pokeapi` commit (`d4f9a4a`) — measured at ~208 KB and
  8 requests, against the old PWA's ~426 MB and ~3875 requests (see
  [`reference-pokedata.md`](plan/reference-pokedata.md)). `DatasetSyncManager`
  assembles them and writes the whole cache — species, moves, abilities,
  the 18×18 type chart — in one Room transaction; a failed sync leaves the
  previous cache (if any) completely untouched.
- **The cache.** Room schema v1: `poke_species`, `poke_move`,
  `poke_ability`, `type_efficacy`, `poke_cache_meta`. 1351 Pokémon forms,
  1025 species, 919 of 937 moves (18 legacy "Shadow" moves from Pokémon
  Colosseum/XD are outside the app's 18-type model and excluded by design
  — see `docs/implementation-decisions.md`), a complete 324-cell type
  chart. `PokedexRepository` exposes search (blank query → empty, per
  the dropdown UX contract), lookups, and the full species list for later
  phases' suggestion/generator engines.
- **Settings → Data.** Cache summary, last-synced timestamp, the short
  dataset revision, re-sync, and "Clear cached data" behind a confirmation
  that says plainly it doesn't touch saved teams (there are none yet, but
  the wording is already correct for Phase 2 onward).
- **The Teams screen shows a non-blocking sync banner** while a sync runs,
  instead of the PWA's full-screen `LoadingScreen` gate — the whole point
  of this phase.
- **`PokemonSprite` and `TypeBadge`** composables exist, built and ready,
  with no call site yet — nothing renders a sprite until Phase 2 has a
  team slot to show one in.
- **`./gradlew testDebugUnitTest lintDebug assembleDebug`** all green in
  one invocation — 69 unit tests, 0 failures (verified locally with a
  temporary, non-persistent SDK this session, same as Phase 0 — see
  `docs/implementation-decisions.md`).

## What's known to be missing

Everything the app is supposed to do beyond the dataset. In order, per
[`docs/plan/README.md`](plan/README.md):

- **Phase 2** — actual teams: creating one, the six-slot editor, type
  overrides, abilities, moves, the custom roster. This is also where
  `PokemonSprite`/`TypeBadge` get their first real call site.
- **Phase 3** — the coverage analysis screen (the ported coverage engine).
- **Phase 4** — suggestions and the "Surprise Me" generator.
- **Phase 5** — Showdown import/export, the rest of Settings, local backup.
- **Phase 6** — signing, the release pipeline, `legacy-web/` deleted.

[`ROADMAP.md`](../ROADMAP.md) points at
[`docs/plan/native-spec.md`](plan/native-spec.md)'s "Explicitly out of
scope" for what's deliberately never planned (Play Store submission, iOS,
a backend).

## Known regressions

None yet — nothing user-facing has shipped to regress. The one
deliberate, non-regression gap: **upgrading from the old Capacitor build
loses saved teams and the custom roster.** This is a decided trade-off
(see `docs/implementation-decisions.md`), not a bug, but it will read as
one to a real user with existing data unless Phase 6's release notes
say so plainly before they update.

## Verifying project health

```bash
export ANDROID_HOME=...    # if a local SDK is available; otherwise rely on CI
./gradlew testDebugUnitTest   # 69 tests as of Phase 1
./gradlew lintDebug
./gradlew assembleDebug
```

`docs/test-plan.md` has the on-device manual steps this doesn't cover —
locale switching, dynamic colour, the launcher icon, install-over-upgrade,
and (new this phase) the real first-launch sync, offline handling, and
clearing the cache.

`legacy-web/` has its own, separate health check:

```bash
cd legacy-web && npm ci && npm test   # 175 tests
```
