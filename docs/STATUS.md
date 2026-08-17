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
- The PWA is installable and works offline after the first PokéAPI fetch.
  GitHub Pages deploys only as part of `release.yml` (see below), not on
  every push to `main` — deliberate, so the live site always matches a
  tagged release. User data (`teamdex_userdata`) is persisted in
  `localStorage`, unchanged.
- The Android app is a native Capacitor shell wrapping the same React
  code, MUI-restyled for every screen except `SurpriseMeModal` (left
  intentionally unrestyled — same Tailwind markup on both platforms). CI
  builds a signed release APK/AAB automatically on the Android dev branch;
  the debug APK is manual-only. Distribution is through Firebase App
  Distribution only (the old temporary Actions-artifact workflow was
  deleted this session — see "Loose ends" below). It's sideload-only: no
  Play Store listing. As of
  this session, Android user data is persisted through
  `@capacitor/preferences` (native storage) instead of the WebView's
  `localStorage` — see `useUserDataStorage.ts`/`.android.ts` in
  `docs/MODULES.md` and `docs/android/PLATFORM.md` → "Storage isolation
  (PWA vs Android)". Devices that had the app
  installed before this change get their existing teams migrated
  automatically on next launch.
- The app is versioned from a single source of truth: `package.json`'s
  `version` field, shown in Settings → App (`__APP_VERSION__`, injected
  at build time by `vite.config.ts`). `.github/workflows/release.yml`
  (manual-dispatch only) is the single workflow that publishes anything
  public: given a version, it bumps that field (and Android's
  `versionName`/`versionCode`), builds and publishes a signed release APK
  as a GitHub Release with `CHANGELOG.md`'s matching section as release
  notes, and in the same run redeploys GitHub Pages — see
  [`docs/android/BUILD.md`](android/BUILD.md) → "Cutting a public
  release" and [`docs/DEVELOPMENT.md`](DEVELOPMENT.md) → "Keeping the web
  and Android releases in sync".
- Actions workflows were consolidated down to three this session:
  `ci.yml` (tests + build check on every PR/push to `main`, no
  publishing — replaces the old `pr-check.yml`), `release.yml` (the
  public-release workflow above — replaces `deploy.yml` and
  `release-android.yml`, folding the GitHub Pages deploy that used to be
  its own always-on workflow into the manual release), and
  `android-build.yml` (unchanged: internal test builds via Firebase App
  Distribution, kept separate from the public release path). The
  temporary `android-debug-apk-artifact.yml` was deleted, resolving the
  "still temporary" loose end from the previous session.
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

- **`release.yml` has never actually been run.** It's been reviewed
  carefully (version validation, tag-exists check, build-before-publish
  ordering, signing required) and the repo's other workflows build fine
  locally, but the workflow itself — the version bump commit landing on
  `main`, the tag/release creation, the changelog extraction, the asset
  upload, and the Pages deploy at the end — has not been exercised end to
  end in CI. Trigger it once for the real `1.0.0` release and confirm:
  the GitHub Release appears with the APK attached and installable, the
  live site at `marcogn.github.io/CoverDex` shows the same version in
  Settings → App, and the release notes match `CHANGELOG.md`'s `[1.0.0]`
  section.
- **`CHANGELOG.md` needs a new `## [X.Y.Z]` entry before every release
  after `1.0.0`.** The workflow falls back to a generic "see CHANGELOG.md
  / README.md" note if it can't find a matching heading — better than
  failing the release, but not a substitute for real notes.
- **GitHub Pages no longer redeploys on every push to `main`** — only
  `release.yml` deploys it now (see "What's implemented" above). If a
  docs-only or urgent web fix needs to go live without a full Android
  release, either trigger `release.yml` anyway (it's a no-op version bump
  if `package.json` is unchanged, still rebuilds and redeploys Pages) or
  do a one-off manual deploy per `docs/DEVELOPMENT.md` → "Manual
  deployment".

## Loose ends from the previous session (2026-08-04)

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
