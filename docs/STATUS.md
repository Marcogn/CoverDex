# Project Status

A snapshot of what's implemented, what's known to be missing, and any
loose ends — written for whoever (human or agent) picks this project up
next. Last verified 2026-08-17, during a docs/release-automation pass
following the first release-worthy APK build (see "Loose ends from this
session (2026-08-17)" below). Re-verify anything here that actually
matters before relying on it — this file goes stale the moment someone
ships a change without updating it. It complements, not replaces, the other docs:
[`CLAUDE.md`](../CLAUDE.md) for rules and invariants,
[`ARCHITECTURE.md`](ARCHITECTURE.md) for how the app fits together,
[`ROADMAP.md`](../ROADMAP.md) for the intentionally-deferred backlog.

## What's implemented

The full feature list lives in [`README.md`](../README.md); this is the
short version plus what isn't obvious from a feature list.

- Team building, coverage analysis, suggestions, the "Surprise Me"
  generator, the custom roster, Showdown import/export, and i18n (English/
  Italian) are all shipped and covered by tests — 23 test files, 175 tests
  as of this session (`npm run test`).
- The PWA is installable and works offline after the first PokéAPI fetch,
  deployed to GitHub Pages on every push to `main` via `deploy.yml`. User
  data (`teamdex_userdata`) is persisted in `localStorage`, unchanged.
- The Android app is a native Capacitor shell wrapping the same React
  code, MUI-restyled for every screen except `SurpriseMeModal` (left
  intentionally unrestyled — same Tailwind markup on both platforms). CI
  builds a signed release APK/AAB automatically on the Android dev branch;
  the debug APK is manual-only. Distribution is normally through Firebase
  App Distribution, plus a temporary Actions-artifact workflow — see
  "Loose ends" below. It's sideload-only: no Play Store listing. As of
  this session, Android user data is persisted through
  `@capacitor/preferences` (native storage) instead of the WebView's
  `localStorage` — see `useUserDataStorage.ts`/`.android.ts` in
  `docs/MODULES.md` and `docs/android/PLATFORM.md` → "Storage isolation
  (PWA vs Android)". Devices that had the app
  installed before this change get their existing teams migrated
  automatically on next launch.
- The app is versioned from a single source of truth: `package.json`'s
  `version` field, shown in Settings → App (`__APP_VERSION__`, injected
  at build time by `vite.config.ts`). `.github/workflows/release-android.yml`
  (manual-dispatch only) bumps that field, builds a signed release APK,
  and publishes it as a GitHub Release with `CHANGELOG.md`'s matching
  section as release notes — see [`docs/android/BUILD.md`](android/BUILD.md)
  → "Cutting a public release" and [`docs/DEVELOPMENT.md`](DEVELOPMENT.md)
  → "Keeping the web and Android releases in sync" for why this also
  keeps the GitHub Pages deploy on the same version.
- Documentation was restructured this session: `CLAUDE.md` was cut from
  ~34KB to ~16KB by moving per-file invariants to
  [`docs/MODULES.md`](MODULES.md) and Android architectural invariants to
  [`docs/android/PLATFORM.md`](android/PLATFORM.md); local dev setup and
  GitHub Pages deployment moved to [`docs/DEVELOPMENT.md`](DEVELOPMENT.md).
  `README.md` was rewritten as a market-facing product page (features,
  "why CoverDex", how to get it) rather than a developer-first document.
  `CHANGELOG.md` was added, starting at `[1.0.0]`.

## What's known to be missing or deferred

[`ROADMAP.md`](../ROADMAP.md) is the authoritative list — read that for
the remaining Android backlog (promoting `android-build.yml` off
manual-dispatch, Play Store submission, iOS via Capacitor). The
`teamdex_userdata` → `@capacitor/preferences` migration that used to be
the first item there is done as of this session and has been removed from
that file.

A "move calculation/filtering into a backend, Android-only" idea was
proposed and explicitly turned down this session — see CLAUDE.md's
"Architecture Overview" → the "No backend" bullet for the reasoning (no
real performance problem to fix at this data scale, and it would fork
business logic between platforms). If this comes up again, read that
reasoning before re-implementing it; it hasn't changed.

## Loose ends from this session (2026-08-17)

- **`release-android.yml` has never actually been run.** It's been
  reviewed carefully (version validation, tag-exists check, build-before-
  commit ordering, signing required) and the repo's other workflows build
  fine locally, but the workflow itself — the version bump commit landing
  on `main`, the tag/release creation, the changelog extraction, the
  asset upload — has not been exercised end to end in CI. Trigger it once
  for the real `1.0.0` release and confirm: the release appears at
  `github.com/marcogn/CoverDex/releases`, the attached APK installs and
  matches what's in Settings → App, and the release notes match
  `CHANGELOG.md`'s `[1.0.0]` section.
- **`CHANGELOG.md` needs a new `## [X.Y.Z]` entry before every release
  after `1.0.0`.** The workflow falls back to a generic "see CHANGELOG.md
  / README.md" note if it can't find a matching heading — better than
  failing the release, but not a substitute for real notes.

## Loose ends from the previous session (2026-08-04)

- **`.github/workflows/android-debug-apk-artifact.yml` is still
  temporary**, unchanged from the earlier pass this date. It uploads the
  debug APK as a plain GitHub Actions artifact — a deliberate, narrow,
  explicitly-approved exception to the "never `actions/upload-artifact`
  for Android output" rule (this repo is public), meant to be deleted once
  no longer needed. If it's gone by the time you read this, the exception
  is resolved — delete the matching notes in `CLAUDE.md`, `README.md`, and
  `docs/android/BUILD.md` too.
- **The Android storage migration needs a real-device/emulator check
  before it can be fully trusted.** Everything here was verified by unit
  tests against a mocked `@capacitor/preferences` (see
  `src/hooks/__tests__/useUserDataStorage.android.test.ts`) and by
  confirming the Android production bundle actually pulls in the
  `.android.ts` file (grepped `dist-android` for the Preferences plugin
  string and the `teamdex_userdata` key) — there was no Android
  SDK/emulator available in this session to run `./gradlew assembleDebug`
  and install it on a real device or emulator. Before this ships to real
  users, actually install a build and confirm: (1) a fresh install starts
  with one empty default team and no crash, (2) creating/editing teams
  persists across an app restart, (3) if you have a build from before this
  migration installed with saved teams, upgrading in place shows those
  same teams afterward (the migration path) rather than an empty default.

## Verifying project health

```bash
npm install
npm run test            # 23 files, 175 tests as of this session
npm run build            # type-check + PWA production build
npm run build:android    # type-check + Android web bundle
```

The Android native build/lint (`cd android && ./gradlew lint
testDebugUnitTest assembleDebug`) needs a JDK 21 + Android SDK toolchain —
see [`docs/android/BUILD.md`](android/BUILD.md) for the full local setup,
including the emulator-based Espresso smoke test. That native build is the
one thing this session couldn't run — see "Loose ends" above.
