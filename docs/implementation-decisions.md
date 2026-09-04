# Implementation decisions

Non-obvious choices made while executing the native Android migration plan
(`docs/plan/`), and why. Add an entry here whenever a phase requires a
judgment call the plan didn't already settle. One section per phase.

## Phase 0 — Foundation

- **`minSdk` stays 24, not Hall of Memories' 26.** The Capacitor build
  shipped `minSdk 24` and nothing in this app needs `java.time` (the only
  reason Hall of Memories requires 26 without desugaring). Raising it would
  drop real API 24–25 devices for no benefit. If a later phase genuinely
  needs `java.time`, the right fix is raising `minSdk` then, with a
  changelog note — not adding desugaring now against a need that doesn't
  exist yet.
- **`versionCode` starts at 2, `versionName` at "2.0.0".** The Capacitor
  build shipped `versionCode 1` / `versionName "1.0"`; the native app must
  be installable over it, so `versionCode` has to increase. The major
  version bump (`1.x` → `2.0.0`) is honest: this release drops the web app
  entirely and does not carry user data forward (see the next entry).
- **No data migration from the Capacitor build — decided, not deferred.**
  The old app persisted `teamdex_userdata` in `@capacitor/preferences`
  (native Android SharedPreferences under a Capacitor-specific key/format).
  Reading and converting that format into Room was considered and rejected
  in the planning session (see `docs/plan/native-spec.md`, "Storage"): the
  user explicitly chose a clean break over the added complexity and risk of
  a one-shot migration path that only ever runs once per device and is
  otherwise dead code forever after. Consequence: anyone upgrading in place
  loses their saved teams and custom roster silently unless warned first.
  Phase 6's release notes must lead with this, in plain language, with the
  concrete mitigation (export each team to Showdown format from the old
  app before updating) — not buried under feature bullets.
- **The launcher icon is a verbatim copy of the Capacitor build's mipmap
  set, not a re-laid-out vector.** The phase file's literal instruction was
  to "recover the foreground drawable and re-lay it out as
  `mipmap-anydpi-v26/ic_launcher.xml` + the density buckets" (implicitly
  assuming a vector source, as in Hall of Memories). Inspection showed the
  Capacitor asset is already a complete, correct Android launcher icon set
  — raster `ic_launcher_foreground.png`/`ic_launcher_background.png` per
  density (mdpi through xxxhdpi, 108dp), a legacy raster
  `ic_launcher.png`/`ic_launcher_round.png` fallback per density for
  pre-API-26 devices (needed here since `minSdk` is 24, unlike Hall of
  Memories' 26), and `mipmap-anydpi-v26/ic_launcher{,_round}.xml` using an
  inset foreground. Copying it unchanged is strictly more faithful to "the
  app's visual identity does not change in this rewrite" than reconstructing
  it as a hand-authored vector would have been, and it already handles the
  pre-26 fallback the vector approach doesn't. `./gradlew lintDebug`
  confirms it's a valid, complete adaptive icon (only a benign "missing
  monochrome tag" advisory, an Android-13+ nicety absent from the original
  Capacitor asset too — not a regression, not fixed here).
- **Settings' three-way language picker (System/Italian/English) has no
  PWA equivalent.** `legacy-web`'s i18next-based switcher
  (`src/components/Settings/SettingsPage.tsx`) exposes only two bare
  buttons, `EN`/`IT`, no spelled-out names, and no "follow the system
  locale" option — i18next has no concept of tracking the OS locale
  automatically the way `AppCompatDelegate.setApplicationLocales(empty)`
  does. Since Rule 4 ("port the text, do not reinvent it") has no PWA
  wording to port for this native-only feature, the three option labels
  ("Predefinito sistema"/"System default", "Italiano"/"Italian",
  "Inglese"/"English") are ported from Hall of Memories' Settings screen
  instead, which already solved the identical UI problem.
- **The theme picker's three labels ARE ported from `legacy-web`**, unlike
  the language picker above — `settings.systemDefault`/`light`/`dark`
  already exist in `legacy-web/src/i18n/locales/{it,en}.json` for exactly
  this picker (`SettingsPage.tsx`'s theme `<select>`), so those exact
  strings were used rather than Hall of Memories' shorter "Sistema"/"Tema".
- **Theme colour roles are seeded from the brand purple `#5B21B6`**, not a
  new palette. That hex is the Capacitor adaptive-icon background
  (`android:icons` npm script's `--iconBackgroundColor`) and the PWA's own
  accent color — it already sits almost exactly at Material 3's
  conventional "tone 40" lightness for a light-scheme primary color (42%
  lightness, measured), so it was used directly as `primary` rather than
  algorithmically adjusted. `primaryContainer`/`onPrimaryContainer` and the
  dark-scheme roles were derived by holding hue and saturation constant and
  shifting only lightness (a plain HSL adjustment, not a full Material
  color-utilities HCT computation — no such dependency is in the pinned
  catalogue and Phase 0 doesn't add one). Material You dynamic colour
  (API 31+) overrides all of this anyway; the manual scheme only matters on
  older devices and when the user has dynamic colour off.
- **`Destination.TeamDetail`'s route exists in the sealed interface but has
  no `composable<>` registered in the `NavHost` yet.** Nothing navigates to
  it until Phase 2 gives Teams real CRUD and TeamDetail a screen to show;
  registering a route with no reachable screen would mean inventing a
  placeholder Phase 0 doesn't need. The type's shape is what needs to be
  stable for Phase 2, not its wiring.
- **Verified locally with a temporary Android SDK, not committed.** The
  sandboxed session had no `ANDROID_HOME` by default (matching
  `docs/plan/README.md`'s documented default), but `dl.google.com` was
  reachable, so `cmdline-tools` + `platform-36` + `build-tools` were
  installed to `/tmp/android-sdk` (outside the repo, `.gitignore`d either
  way via `local.properties`) specifically to run `assembleDebug`,
  `lintDebug` and `testDebugUnitTest` for real rather than only trusting
  `android-ci.yml` to catch a problem. All three were green. This is not an
  assumption future sessions can rely on — check `$ANDROID_HOME` and
  `command -v sdkmanager` per `docs/plan/README.md` before assuming a local
  build is possible, and fall back to CI if it isn't.
