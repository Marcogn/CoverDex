# Project Status

A snapshot of what's implemented, what's known to be missing, and any loose
ends — written for whoever (human or agent) picks this project up next.
Last verified 2026-09-04, at the end of Phase 0 of the native Android
migration. Re-verify anything here before relying on it — this file goes
stale the moment someone ships a change without updating it. It complements,
not replaces, the other docs: [`CLAUDE.md`](../CLAUDE.md) for rules and
invariants, [`docs/plan/README.md`](plan/README.md) for the phase-by-phase
build, [`docs/plan/native-spec.md`](plan/native-spec.md) for what the
finished app must do, [`ROADMAP.md`](../ROADMAP.md) for the deferred
backlog.

## What CoverDex is right now

A native Android skeleton: a themed, empty Teams screen behind a
three-item navigation drawer (Your Teams / Custom Pokémon / Settings), a
working theme picker (System/Light/Dark, persisted) and a working language
picker (System/Italiano/English, persisted, switches every visible string
immediately). No team can be created yet, there is no Pokémon data, and
there is no network activity anywhere in the app.

That is the intended state at the end of Phase 0 of
[`docs/plan/README.md`](plan/README.md) — not a partial feature, a
completed one. The functional app (teams, analysis, suggestions, the
generator, import/export, backup) does not exist yet; it is built out
phase by phase starting with Phase 1.

## What's implemented

- **The Gradle project.** Single-module (`:app`), Kotlin DSL, the version
  catalogue and Gradle wrapper copied verbatim from Hall of Memories (agp
  8.13.0, kotlin 2.0.21, compose BOM 2024.12.01, room 2.6.1, hilt 2.52,
  …). `./gradlew assembleDebug`, `lintDebug` and `testDebugUnitTest` are
  all green (verified locally with a temporary SDK this session — see
  `docs/implementation-decisions.md`).
- **App identity.** `applicationId com.marcogn.coverdex`, `minSdk 24`,
  `targetSdk`/`compileSdk 36`, `versionCode 2` / `versionName "2.0.0"` —
  chosen so the native APK can install *over* the old Capacitor build (same
  applicationId, higher versionCode), even though no user data carries
  over (see "Known regressions" below, and
  `docs/implementation-decisions.md`).
- **Theme.** Material 3, seeded from the app's existing brand purple
  (`#5B21B6`), Material You dynamic colour on API 31+. Persisted via a
  Preferences DataStore (`settings_prefs`) that every later setting will
  share.
- **Language.** `AppCompatDelegate.setApplicationLocales()`-based picker,
  System/Italian/English, persisted automatically
  (`AppLocalesMetadataHolderService`).
- **Navigation.** Type-safe Navigation-Compose routes behind a
  `ModalNavigationDrawer`. `Destination.TeamDetail`'s route type exists
  (Phase 2 needs it) but nothing navigates to it yet.
- **The launcher icon** is the Capacitor build's own icon, ported
  unchanged.
- **CI.** `.github/workflows/android-ci.yml`: JDK 17 + Android SDK, lint +
  unit tests + `assembleDebug` on every push/PR to `main`.
- **`legacy-web/`** — the parked React/Capacitor PWA, kept as the
  behavioural reference for the rest of the migration. `npm test` is green
  (23 files, 175 tests) as of the move. It is not part of CI and is deleted
  in Phase 6.

## What's known to be missing

Everything the app is supposed to do. In order, per
[`docs/plan/README.md`](plan/README.md):

- **Phase 1** — the dataset sync (species/moves/abilities/types from
  PokéAPI's CSV source data, ~208 KB total — see
  [`reference-pokedata.md`](plan/reference-pokedata.md)), the Room cache,
  sprite rendering.
- **Phase 2** — actual teams: creating one, the six-slot editor, type
  overrides, abilities, moves, the custom roster.
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
./gradlew testDebugUnitTest   # 6 tests as of Phase 0
./gradlew lintDebug
./gradlew assembleDebug
```

`docs/test-plan.md` has the on-device manual steps this doesn't cover —
locale switching, dynamic colour, the launcher icon, install-over-upgrade.

`legacy-web/` has its own, separate health check:

```bash
cd legacy-web && npm ci && npm test   # 175 tests
```
