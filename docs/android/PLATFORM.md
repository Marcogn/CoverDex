# Android Platform Invariants

Stable architectural rules for the Android shell, moved out of
[`CLAUDE.md`](../../CLAUDE.md) to keep that file short. For operational
detail (local builds, signing, CI, Firebase distribution, icon
generation, troubleshooting), see [`BUILD.md`](BUILD.md) instead — that
file is where a Capacitor/AGP/Gradle version bump gets recorded; this
one is where an architectural decision does.

CoverDex ships a native Android shell via Capacitor, additive to the PWA
— it must never carry business logic of its own.

- `android/` is a **committed native project**, not build output. Its
  `res/`, Gradle config, and Java sources are source of truth; only build
  artifacts, local SDK paths, and signing secrets are gitignored (see
  `.gitignore`).
- `@capacitor/cli` requires **Node >=22** — stricter than the web app's
  Node 18+. Any command that shells out to `cap` (`npx cap sync android`,
  `npm run android:build`, CI) needs Node 22+, even though `deploy.yml`
  and `pr-check.yml` stay on Node 20 for the unrelated web pipeline.
- `capacitor.config.ts`: `appId: "com.marcogn.coverdex"`,
  `appName: "CoverDex"`, `webDir: "dist-android"`. Never change `appId`
  post-release — see `CLAUDE.md` → "What NOT to Change Without
  Discussion". The GitHub repository itself has already been renamed to
  `CoverDex` (`deploy.yml`'s `VITE_BASE_URL` is `/CoverDex/`, matching the
  current repo name). The one remaining leftover of the old
  `poke-team-builder` name is `package.json`'s internal `name` field,
  which is cosmetic (it's never read for the Pages base path or the
  Android `appId`) and can be renamed opportunistically.
- Run `npx cap sync android` (or `npm run android:build`, which also runs
  the Android web build first) after every change and before any native
  build — it copies `dist-android/` into
  `android/app/src/main/assets/public`. A native build against a stale
  sync serves an outdated WebView.
- The Workbox service worker must not register when
  `Capacitor.isNativePlatform()` is true (`src/utils/registerServiceWorker.ts`).
  Native assets are bundled locally, not served over the network there.
- `.github/workflows/android-build.yml` and `release-android.yml` require
  GitHub Secrets, values never documented here: `ANDROID_KEYSTORE_BASE64`,
  `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`,
  `FIREBASE_APP_ID`, `FIREBASE_SERVICE_ACCOUNT`. Android build outputs
  (debug and signed release alike) must never go through
  `actions/upload-artifact` — this is a public repo, and Actions artifacts
  are downloadable by any signed-in GitHub user. They go to Firebase App
  Distribution (or, for a tagged release, a GitHub Release, which is
  meant to be public) instead.
  `.github/workflows/android-debug-apk-artifact.yml` is a deliberate,
  clearly-commented, temporary exception to this rule (manual-dispatch
  only) — check whether it still exists before assuming the rule above is
  absolute; see its header comment and `docs/STATUS.md`.

## Two build outputs: `dist/` (PWA) vs `dist-android/` (Android)

`vite build` (plain, `npm run build`) produces `dist/` exactly as before —
the PWA is untouched. `vite build --mode android` (`npm run build:android`)
produces a separate `dist-android/`, built by `vite-plugins/androidPlatformResolve.ts`:
a Vite plugin, active only in `--mode android`, that resolves any local
import to its `<Name>.android.tsx`/`.android.ts` sibling when one exists,
otherwise falls through to the default file. This is what lets the
Material layer below exist only in the Android build — MUI/Emotion never
appear in the `dist/` bundle (verified by grepping the built output; there
is no automated bundle-size CI check, so re-verify by hand — `grep -c
emotion dist/assets/*.js` should print `0` — after any change to
`vite.config.ts` or the plugin). `vite-plugin-pwa` is also android-mode-only
(the WebView never needs the service worker or its manifest).

## Shared logic / platform-presentation file convention

Every restyled component follows the same pattern: the default file
(`Component.tsx`) holds the **shared** hooks/state/handlers and is also
the **web** presentation (Tailwind), consumed unchanged by the PWA. A
sibling `Component.android.tsx`, when present, is picked up automatically
by the platform-resolve plugin and holds the **Android** presentation
(MUI) — imported with the exact same props contract (exported from the
default file, e.g. `TeamsPageProps`) and, wherever the component has
non-trivial logic, driven by hooks pulled out of the default file (e.g.
`useAppShell` in `src/hooks/useAppShell.ts`, consumed identically by
`App.tsx` and `App.android.tsx`) or by pure helper functions re-exported
from the default file (e.g. `collectAttackingTypes`, `multLabel` from
`CoverageGrid.tsx`). Business logic is never forked between the two files.

One subtlety worth knowing before touching the plugin: an `.android.tsx`
file importing a *type* from its own base sibling (`import type { XProps }
from './X'`) is invisible to the plugin — TypeScript strips type-only
imports before Rollup ever resolves them. But a *value* import from the
base sibling (a shared pure helper) is a real import the plugin sees, and
naively redirecting it would loop the file back to itself; the plugin
special-cases this (see its own comments and
`src/test/androidPlatformResolve.test.ts`).

Screens restyled for Android (all of them, as of this writing): top-level
nav/shell (`App.android.tsx`), Teams list, Settings, Custom Pokémon
roster, Team Builder (Pokémon tab — slots, moves, ability picker, export/
delete dialogs), and the Analysis tab (including the offensive/defensive
grids as MUI `Table`/`TableContainer` with a manually-sticky first column,
and suggestion cards). The shared `SearchableDropdown`/`AbilityDropdown`
Autocomplete implementations cover every picker (Pokémon, move, ability,
and the Surprise Me anchor picker) from one file each. `SurpriseMeModal`
was not restyled either — it renders the same Tailwind markup on Android,
just without an MUI treatment. Two components are dead code, not imported
anywhere on either platform, and were left alone rather than restyled: the
roster's old `CustomRoster.tsx` (superseded by `CustomPkmnPage.tsx`) and
`ImportExport.tsx` (superseded by the Showdown import/export flow now
living in `ExportModal.tsx`/`NewTeamModal.tsx`).

The platform-resolve convention isn't limited to components: `useAppShell`
imports `useUserDataStorage`, and on Android that resolves to
`useUserDataStorage.android.ts` the same way any `Component.android.tsx`
would resolve for a component import — see that module's entry in
[`docs/MODULES.md`](../MODULES.md) and "Storage isolation" below for why
storage is the one place logic itself (not just presentation) is
currently forked per platform.

## Storage isolation (PWA vs Android)

`teamdex_userdata` (teams, custom Pokémon, settings) is persisted
differently per platform — see `useUserDataStorage.ts`/`.android.ts` in
[`docs/MODULES.md`](../MODULES.md):
- **PWA**: `localStorage`, in the mobile/desktop browser's storage
  partition for the GitHub Pages origin.
- **Android**: `@capacitor/preferences`, native SharedPreferences-backed
  storage in the app's own OS-sandboxed, app-private storage — chosen
  because WebView `localStorage` isn't guaranteed durable under storage
  pressure the way native storage is. The WebView's `localStorage` is only
  touched once more on Android, as a one-time migration source for devices
  that had the app installed before this changed (see the module entry
  above); once migrated, Android no longer reads or writes it for user
  data.

`teamdex_pokeapi_cache` is unaffected by any of this — see "PokéAPI
download is identical on both platforms" below, it still uses
`localStorage` on both platforms.

Regardless of mechanism, **the two releases never share data**: installing
the Android app does not surface a user's PWA teams, and vice versa. Any
future cross-device sync feature is a deliberate, separate project —
neither release should grow one implicitly.

## PokéAPI download is identical on both platforms

`usePokemonData`/`pokeApiFetch.ts` (see `CLAUDE.md` → "Key Data Flows"
and [`docs/MODULES.md`](../MODULES.md)) run the exact same way on the PWA
and inside the Capacitor WebView — same mirror, same batching, same
`localStorage` cache key. There is no Android-specific data step and no
build-time generation script; the dataset is never bundled into either
`dist/` or `dist-android/`. The Android WebView needs the standard
Capacitor `android.permission.INTERNET` (already required for sprite
`<img>` URLs, present in the generated `AndroidManifest.xml`) for this to
work on device. `src/test/noRuntimePokeApiFetch.test.ts` guards against
ever calling the **live** `pokeapi.co` REST API (as opposed to the static
mirror) from either platform's runtime code — it fails if an actual
`pokeapi.co` URL is constructed anywhere under `src/` outside the test
itself; a comment merely *mentioning* the host (to explain why the mirror
is used instead) does not trip it.
