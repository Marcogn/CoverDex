# Project Status

A snapshot of what's implemented, what's known to be missing, and any
loose ends — written for whoever (human or agent) picks this project up
next. Last verified 2026-08-04, during a documentation and Android-tooling
pass. Re-verify anything here that actually matters before relying on
it — this file goes stale the moment someone ships a change without
updating it. It complements, not replaces, the other docs:
[`CLAUDE.md`](../CLAUDE.md) for rules and invariants,
[`ARCHITECTURE.md`](ARCHITECTURE.md) for how the app fits together,
[`ROADMAP.md`](../ROADMAP.md) for the intentionally-deferred backlog.

## What's implemented

The full feature list lives in [`README.md`](../README.md); this is the
short version plus what isn't obvious from a feature list.

- Team building, coverage analysis, suggestions, the "Surprise Me"
  generator, the custom roster, Showdown import/export, and i18n (English/
  Italian) are all shipped and covered by tests — 21 test files, 164 tests
  as of this session (`npm run test`).
- The PWA is installable and works offline after the first PokéAPI fetch,
  deployed to GitHub Pages on every push to `main` via `deploy.yml`.
- The Android app is a native Capacitor shell wrapping the same React
  code, MUI-restyled for every screen except `SurpriseMeModal` (left
  intentionally unrestyled — same Tailwind markup on both platforms). CI
  builds a signed release APK/AAB automatically on the Android dev branch;
  the debug APK is manual-only. Distribution is normally through Firebase
  App Distribution, plus a temporary Actions-artifact workflow — see
  "Loose ends" below. It's sideload-only: no Play Store listing.

## What's known to be missing or deferred

[`ROADMAP.md`](../ROADMAP.md) is the authoritative list — read that for
the Android backlog (migrating `teamdex_userdata` to
`@capacitor/preferences`, promoting `android-build.yml` off
manual-dispatch, Play Store submission, iOS via Capacitor). Everything
there was still accurate as of this session: no `@capacitor/preferences`
usage anywhere in `src/`, and `android-build.yml` still triggers only on
`workflow_dispatch` and pushes to the Android dev branch.

Found and fixed during this session's documentation review (listed here
so nobody re-discovers the same thing from scratch):

- There was no `LICENSE` file despite the README claiming MIT. Added
  (`LICENSE`, repo root).
- `package.json`'s `name` field still said `poke-team-builder`, and
  README/CLAUDE.md carried three stale `/poke-team-builder/` references to
  the GitHub Pages base path — the repo and `deploy.yml` had already moved
  to `/CoverDex/`. Fixed; if you find another leftover reference somewhere
  this pass missed, it's safe to just fix it the same way.
- Two source files are dead code, not imported anywhere on either
  platform: `src/components/CustomRoster/CustomRoster.tsx` (superseded by
  `CustomPkmnPage.tsx`) and `src/components/ImportExport/ImportExport.tsx`
  (superseded by `ExportModal.tsx`/`NewTeamModal.tsx`). Left in place —
  deleting dead code wasn't in scope for this pass, but a future cleanup
  could remove both; CLAUDE.md's "Android Platform" section now documents
  them so they don't get restyled by mistake either.
- `public/icons/icon-192x192.png` and `icon-512x512.png` were committed to
  git despite being listed in `.gitignore` and despite CLAUDE.md saying
  not to commit them. Untracked with `git rm --cached`; they're still
  regenerated at build/deploy time, nothing functional changed.
- `android/app/src/main/res/drawable/ic_launcher_background.xml`,
  `drawable-v24/ic_launcher_foreground.xml`, and
  `values/ic_launcher_background.xml` were leftover default Capacitor
  scaffold files (a generic teal Android robot icon) that nothing in the
  project referenced — the real adaptive icon lives entirely under
  `mipmap-anydpi-v26/` and the `mipmap-*dpi/` PNGs. Deleted.

## Loose ends from this session (2026-08-04)

- **`.github/workflows/android-debug-apk-artifact.yml` is temporary.** It
  uploads the debug APK as a plain GitHub Actions artifact — a deliberate,
  narrow, explicitly-approved exception to the "never
  `actions/upload-artifact` for Android output" rule (this repo is
  public). It was added as a stopgap at the user's request, and the plan
  is to delete it once it's no longer needed. If you're reading this in a
  future session and the file is already gone, the exception is resolved:
  go ahead and delete this paragraph and the matching notes in
  `CLAUDE.md`, `README.md`, and `docs/android/BUILD.md`. If it's still
  there, don't assume it's meant to stay forever, and don't build
  anything further on top of it without checking first.
- **The app icon was rotated and the Android adaptive-icon background was
  fixed.** The pokeball glyph in `scripts/generate-icons.mjs` now renders
  dark navy on top / white on the bottom (previously the reverse).
  Regenerating the Android launcher icons should now go through
  `npm run android:icons`, not a raw `capacitor-assets generate` call — it
  also runs `scripts/fix-android-adaptive-icon.mjs`, which works around a
  `@capacitor/assets` limitation (see `CLAUDE.md` → "PWA Icons" for the
  full explanation of why the fix is needed). If you touch the icon design
  again, use `npm run android:icons` and don't ignore it if the fix script
  errors out — that means `@capacitor/assets`'s generated XML no longer
  matches what the script expects to patch, and the adaptive-icon
  background could silently regress back to a white border.

## Verifying project health

```bash
npm install
npm run test           # 21 files, 164 tests as of this session
npm run build           # type-check + PWA production build
npm run build:android    # type-check + Android web bundle
```

The Android native build/lint (`cd android && ./gradlew lint
testDebugUnitTest assembleDebug`) needs a JDK 21 + Android SDK toolchain —
see [`docs/android/BUILD.md`](android/BUILD.md) for the full local setup,
including the emulator-based Espresso smoke test.
