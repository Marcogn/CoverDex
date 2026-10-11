# Phase 8 — In-app updates and an Info screen

**Status:** planned (2026-10-11), not started.
**Model:** Sonnet — a port of a reviewed design; the expensive decisions were made in the pilot's Opus
stage.
**Read first:** [`../../CLAUDE.md`](../../CLAUDE.md), [`README.md`](README.md) (working rules), and
the pilot's plan, which holds the shared design, the checked facts and their sources:
[`ThePatientGamerHelper/docs/plan-phase-9-updates.md`](https://github.com/Marcogn/ThePatientGamerHelper/blob/main/docs/plan-phase-9-updates.md).

**Prerequisites:** Phase 7 merged (done), and **ThePatientGamerHelper Phase 9 merged and checked on the
device**: this phase copies its `update` package, following the "copy the sibling's patterns" rule.

Requested by the author on 2026-10-11 for all six of their apps; decisions in
[`../implementation-decisions.md`](../implementation-decisions.md), "In-app updates and Info screen,
planned". Today the only version shown is the "App" row of Settings ("CoverDex v…"), and the app shows
no disclaimer at all, although the README has one.

---

## 1. Update package (copied from the pilot)

- `domain/update/` (pure, JVM tests copied with it): `AppVersion`, `ReleaseInfo` parser, APK asset
  picker, release-body cleanup, `UpdateFrequency` + `UpdateCheckPolicy.isDue`.
- `data/update/`: `GitHubReleaseClient` (`HttpURLConnection` like `PokeDataClient`, wired in
  `di/NetworkModule.kt`; **no `Accept-Encoding`**), `ApkDownloader` (SHA-256 against the asset
  `digest`), `ApkVerifier`, `ApkInstaller` (`PackageInstaller` session, system confirmation),
  `UpdatePreferences` in `SettingsPreferences` (the one file for app-wide settings), `UpdateManager`.
- Adaptations to this app:
  - owner/repo `Marcogn/CoverDex` as `BuildConfig` fields;
  - **`minSdk` 24**: `ApkVerifier` uses `GET_SIGNATURES` below API 28; below API 26 there is no per-app
    "install unknown apps" permission (`canRequestPackageInstalls()` does not exist), the global
    "Unknown sources" setting applies — the installer still asks, the app only explains it;
  - the versionCode chain from the Capacitor build stays as it is (the check compares versions, the
    system enforces the higher `versionCode`).
- Manifest: `REQUEST_INSTALL_PACKAGES` (`INTERNET` is already there). No new dependency.
- Startup check only (no WorkManager, not a dependency here). Debug builds never check automatically.

## 2. Update UI

- Dialog on a newer release: version, the Release body headlines, "Full changelog", Download /
  Not now; progress with Cancel; then the system installer; errors with their reason.
- Settings: an "Updates" entry with the frequency Never / Every start / Daily / Weekly (**default
  Weekly**). "Not now" closes; the next due check asks again.

## 3. Info screen

- `ui/about/AboutScreen`, `Destination.About`, an entry in the drawer; the Settings "App" row becomes a
  link to it. Explicit `BackHandler` (Known gotchas).
- Content: app name, `BuildConfig.VERSION_NAME` (+ `VERSION_CODE`), "Check for updates" with its
  result, "Fatto con ❤ da marcogn" / "Made with ❤ by marcogn", GitHub link, MIT licence.
- Disclaimers (wording reviewed by the author in the PR), starting from the README's "About this
  project": Pokémon and all related media are trademarks of Nintendo, Game Freak and The Pokémon
  Company; CoverDex is unofficial, non-commercial, fan-made software with no affiliation to any of
  them; data and sprites from PokéAPI; the export/import format is Pokémon Showdown's, which this app
  is not affiliated with; not a damage calculator or battle simulator.
- Strings in `values/` and `values-en/`, same commit.

## 4. Done when

- JVM tests of `domain/update` (copied, green) and Robolectric tests (`@Config(sdk = [26])`) for the
  dialog/ViewModel state mapping and the frequency preference; plain-ASCII test names.
- `./gradlew lintDebug testDebugUnitTest assembleDebug` green; CI green.
- `docs/test-plan.md` "Phase 8" section; CHANGELOG `[Unreleased]`; README "Updates" and "Info";
  `CLAUDE.md` and this folder's `README.md` progress lists ticked; `docs/implementation-decisions.md`
  "Phase 8" for anything decided while porting.

## 5. Device checks

Install an older signed release, start: dialog with the newer version; download; "install unknown
apps" the first time; system installer; the app reopens on the new version, teams intact. Airplane
mode at startup: nothing shown, no crash. "Never": no prompt at startup, "Check now" works. Info shows
the installed version; both languages; dark theme.

## 6. Out of scope

Silent install, pre-releases, background checks, a third-party licences screen; and everything in
`native-spec.md`, "Explicitly out of scope" (no backend: GitHub's public API is not a backend of this
app, it is a release download).

## Open questions

- Disclaimer wording (proposed above).
- Release number for the build that carries this (proposed: 2.2.0).

## Risks

Same as the pilot's: GitHub API or asset changes (parser fails closed), `digest` missing (size +
signature check), OEM differences in the unknown-apps screen; API 24–25 devices untested unless one is
at hand.
