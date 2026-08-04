# Project Status

A snapshot of what's implemented, what's known to be missing, and any
loose ends — written for whoever (human or agent) picks this project up
next. Last verified 2026-08-04, during an Android storage migration pass
(second pass this date — an earlier documentation/APK-workflow/icon pass
landed first, see git history around PR #21 for that one's detail). Re-
verify anything here that actually matters before relying on it — this
file goes stale the moment someone ships a change without updating it. It
complements, not replaces, the other docs: [`CLAUDE.md`](../CLAUDE.md) for
rules and invariants, [`ARCHITECTURE.md`](ARCHITECTURE.md) for how the app
fits together, [`ROADMAP.md`](../ROADMAP.md) for the intentionally-
deferred backlog.

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
  `localStorage` — see `useUserDataStorage.ts`/`.android.ts` and CLAUDE.md
  → "Storage isolation (PWA vs Android)". Devices that had the app
  installed before this change get their existing teams migrated
  automatically on next launch.

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

## Loose ends from this session (2026-08-04)

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
