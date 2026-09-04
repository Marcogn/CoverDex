# Phase 0 — Foundation

**Goal:** a real, buildable, installable native CoverDex app that starts,
shows a themed empty Teams screen, switches theme and language, and is
verified by CI. No data, no network, no features. The web app is parked and
every web-publishing path is gone.

**Depends on:** nothing. This is the first commit of Kotlin.

---

## 1. Clear the ground

Do this first, in its own commit, so the rest of the phase lands in a clean
tree.

### Park the web app

```bash
mkdir legacy-web
git mv src public index.html package.json package-lock.json tsconfig.json \
       vite.config.ts vite-plugins postcss.config.js tailwind.config.js \
       scripts capacitor.config.ts legacy-web/
```

Add `legacy-web/README.md` saying, in three sentences: this is the React PWA
CoverDex was before the native rewrite; it is kept only as the behavioural
reference for the port and its Vitest suite is the oracle for expected
values; it is never edited and it is deleted in Phase 6.

`legacy-web/` must keep working: `cd legacy-web && npm ci && npm test` runs
the 175 tests. Confirm that before moving on — if the move broke a path in
`vite.config.ts` or `tsconfig.json`, fix it now, because Phases 3–5 depend
on being able to run it.

### Delete what is being replaced

```bash
git rm -r android                              # the generated Capacitor project
git rm .github/workflows/ci.yml \
       .github/workflows/release.yml \
       .github/workflows/android-build.yml
git rm -r docs/android docs/ARCHITECTURE.md docs/MODULES.md docs/DEVELOPMENT.md
```

`docs/STATUS.md` is rewritten, not deleted — see §7.

Everything above is recoverable from git history; the deletions are
deliberate and the PR description must list them.

## 2. Project setup

Root of the repository. Mirror Hall of Memories' layout exactly: a
single-module Gradle build with `:app`, Kotlin DSL, a version catalogue.

### Files

```
settings.gradle.kts            rootProject.name = "CoverDex"; include(":app")
build.gradle.kts               plugin aliases, all `apply false`
gradle/libs.versions.toml      the catalogue below
gradle.properties              android.useAndroidX=true, org.gradle.jvmargs=-Xmx2048m,
                               kotlin.code.style=official, android.nonTransitiveRClass=true
gradlew, gradlew.bat, gradle/wrapper/*   Gradle 8.13 wrapper (generate, commit, chmod +x gradlew)
.gitignore                     Android template + local.properties + /app/build + .gradle,
                               keeping legacy-web/node_modules ignored
app/build.gradle.kts
app/proguard-rules.pro         empty default
app/src/main/AndroidManifest.xml
```

### `gradle/libs.versions.toml`

Copy `../Hall-Of-Memories/gradle/libs.versions.toml` **verbatim**. Every
entry in it is used by this app too, and the version numbers are a
known-good combination that has shipped six phases. Nothing is added and
nothing is removed in this phase.

Pinned, for the record: `agp 8.13.0`, `kotlin 2.0.21`, `ksp 2.0.21-1.0.28`,
`coreKtx 1.13.1`, `lifecycle 2.8.7`, `activityCompose 1.9.3`,
`composeBom 2024.12.01`, `navigationCompose 2.8.5`, `room 2.6.1`,
`hilt 2.52`, `hiltNavigationCompose 1.2.0`, `kotlinxSerializationJson 1.7.3`,
`kotlinxCoroutines 1.9.0`, `coil 2.7.0`, `junit 4.13.2`,
`androidxTestExtJunit 1.3.0`, `espresso 3.6.1`, `robolectric 4.16.1`,
`datastorePreferences 1.1.1`, `appcompat 1.7.0`.

**This catalogue is closed.** Any later phase that thinks it needs an
addition stops and flags it in its PR description instead.

### `app/build.gradle.kts`

```
namespace / applicationId  com.marcogn.coverdex
compileSdk 36, minSdk 24, targetSdk 36
versionCode 2, versionName "2.0.0"
JavaVersion.VERSION_17 / jvmTarget "17"
buildFeatures { compose = true; buildConfig = true }
testOptions { unitTests { isIncludeAndroidResources = true } }
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
```

`versionCode 2` and `versionName "2.0.0"`: the Capacitor build shipped
`versionCode 1` / `1.0.0`, and the native app must be installable over it.
The major bump is honest — this release drops the web app and does not carry
user data forward.

Build types:

- `debug`: `buildConfigField("boolean", "SEED_DEBUG_DATA", "true")`.
- `release`: `isMinifyEnabled = false`, `SEED_DEBUG_DATA = false`, and the
  **same conditional signing block** as Hall of Memories — a `release`
  `signingConfig` populated from `RELEASE_KEYSTORE_PATH`,
  `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`
  environment variables, applied only when `RELEASE_KEYSTORE_PATH` is
  non-blank so a local `assembleRelease` with no secrets still builds
  (unsigned) instead of failing. The keystore itself is Phase 6; the wiring
  goes in now so Phase 6 is only secrets and docs.

Dependencies: core-ktx, lifecycle (runtime-ktx, viewmodel-compose,
runtime-compose), activity-compose, the Compose BOM (ui, ui-graphics,
ui-tooling-preview, material3, material-icons-extended, debug ui-tooling and
ui-test-manifest), navigation-compose, Room (runtime, ktx, ksp compiler),
Hilt (android, ksp compiler, hilt-navigation-compose),
kotlinx-serialization-json, kotlinx-coroutines-android, coil-compose,
datastore-preferences, appcompat. Tests: junit, kotlinx-coroutines-test,
robolectric, androidx-test-ext-junit; androidTest: ext-junit, espresso,
compose ui-test-junit4.

Room and serialization are wired in this phase but unused until Phase 1;
that is deliberate, so Phase 1 is only schema and network.

## 3. Application, Activity, manifest

```
app/src/main/java/com/marcogn/coverdex/
  CoverDexApplication.kt     @HiltAndroidApp, nothing else yet
  MainActivity.kt            @AndroidEntryPoint, AppCompatActivity
```

`MainActivity` **must extend `AppCompatActivity`, not `ComponentActivity`** —
`AppCompatDelegate.setApplicationLocales()` (the in-app language picker) is
silently ignored otherwise, with no error. This was a real, hard-to-diagnose
bug in the sibling projects. As a consequence `res/values/themes.xml` must
define `Theme.CoverDex` descending from `Theme.AppCompat.DayNight.NoActionBar`.
`setContent {}` stays the only UI entry point; no XML layouts.

`MainActivity.onCreate` → `enableEdgeToEdge()` → `setContent { CoverDexApp() }`,
where `CoverDexApp` reads `ThemeViewModel.themeMode`, maps `SYSTEM/LIGHT/DARK`
onto `isSystemInDarkTheme()`, and wraps the nav graph in `CoverDexTheme` +
`Surface`.

`AndroidManifest.xml`:

- `<uses-permission android:name="android.permission.INTERNET" />` — needed
  from Phase 1 (the dataset sync) onward; declare it now with a comment
  saying why.
- `<application android:name=".CoverDexApplication"` … `android:label="@string/app_name"`,
  `android:localeConfig="@xml/locales_config"`, `android:theme="@style/Theme.CoverDex"`,
  `android:supportsRtl="true"`, launcher + round icons.
- The `AppLocalesMetadataHolderService` block with `autoStoreLocales=true`
  meta-data, copied from Hall of Memories — this is what persists the
  language choice without custom storage.
- `MainActivity` exported with the LAUNCHER intent filter.

## 4. Theme and resources

```
ui/theme/Color.kt            light + dark colour roles
ui/theme/Type.kt             Material 3 typography (defaults are fine)
ui/theme/Theme.kt            CoverDexTheme(darkTheme, content), dynamic colour ON for API 31+
ui/theme/ThemeViewModel.kt   @HiltViewModel, themeMode: StateFlow<ThemeMode> via stateIn
data/settings/ThemePreferences.kt   Preferences DataStore, name "settings_prefs"
domain/model/ThemeMode.kt    enum SYSTEM, LIGHT, DARK
```

The brand colour is CoverDex's existing purple, `#5b21b6` — it is the
Capacitor adaptive-icon background and the PWA's accent. Seed the light and
dark colour roles from it rather than inventing a new palette. Dynamic
colour (Material You) is on for API 31+, exactly as in Hall of Memories, and
overrides the seed on those devices.

`ThemePreferences` follows Hall of Memories' file of the same name: a
`by preferencesDataStore(name = "settings_prefs")` extension on `Context`, a
`stringPreferencesKey("theme_mode")`, a `Flow<ThemeMode>` that
`runCatching { ThemeMode.valueOf(it) }` and defaults to `SYSTEM`, and a
`suspend fun setThemeMode`. Use the **same DataStore name** for every
preference this app adds later, so there is one file, not five.

### Launcher icon

Port the existing Capacitor launcher icon rather than generating a new one:
it is in git history under `android/app/src/main/res/mipmap-*` before the
Phase 0 deletion, with `#5b21b6` as the adaptive-icon background. Recover
the foreground drawable and re-lay it out as
`mipmap-anydpi-v26/ic_launcher.xml` + the density buckets. The app's visual
identity does not change in this rewrite.

### Strings

`res/values/strings.xml` (Italian, default) and `res/values-en/strings.xml`
(English). Seed both from `legacy-web/src/i18n/locales/it.json` and
`en.json` respectively — the wording already exists and was written by the
author; do not re-translate it. This phase only needs the keys the skeleton
actually renders (app name, the five destination labels, the theme and
language settings rows, the empty-state text). Later phases add theirs.

`res/xml/locales_config.xml` lists `it` and `en`.

## 5. Navigation skeleton

```
ui/navigation/CoverDexNavHost.kt   type-safe routes, ModalNavigationDrawer around the NavHost
ui/navigation/Destination.kt       sealed routes: Teams, TeamDetail(teamId), Roster, Settings
ui/teams/TeamsScreen.kt            empty state only
ui/roster/RosterScreen.kt          empty state only
ui/settings/SettingsScreen.kt      theme picker + language picker only
```

Copy Hall of Memories' drawer + `NavHost` structure. `TeamDetail` takes a
team id argument now even though nothing navigates to it yet — Phase 2
should not have to touch the navigation graph's shape.

Compose Navigation's own back callback just calls `popBackStack()`, so **any
screen that later needs custom back behaviour must add an explicit
`BackHandler`**. Nothing in this phase does; the note is here so Phase 2
does not rediscover it.

## 6. CI

```
.github/workflows/android-ci.yml
```

Copy `../Hall-Of-Memories/.github/workflows/android-ci.yml`, changing only
the project name and branch triggers. It pins **JDK 17**, which is what makes
the Robolectric rule below load-bearing.

`build-apk.yml` and `release.yml` come in Phase 6. This phase ships exactly
one workflow, and its green run is the phase's proof of done.

> **Every Robolectric test class needs `@Config(sdk = [26])`.** Robolectric's
> shadow jar for `compileSdk 36` needs a newer JDK than CI runs. Without the
> pin the test passes locally and fails in CI with an
> `UnsupportedOperationException at DefaultSdkProvider.java` that does not
> name the real cause. This bit both sibling projects. Pin it from the first
> Robolectric test you write, in Phase 1.

## 7. Docs

- **`CLAUDE.md`** — rewrite. It currently describes the React PWA in detail
  and is actively misleading for the native app. Model it on
  `../Hall-Of-Memories/CLAUDE.md`: project identity table, sibling projects,
  phase status, product decisions already made, the architecture tree, code
  conventions, known gotchas, build commands, changelog process. Keep the
  domain rules (type chart, suggestion engine, Showdown contract, ability
  effects, dropdown UX) — they are still true — but move their detail to
  `docs/plan/native-spec.md` and link to it. Point the top of the file at
  `docs/plan/README.md`.
- **`docs/STATUS.md`** — rewrite as a native-app snapshot. Everything in the
  current one about Pages, Firebase, `release.yml` and the Capacitor
  storage migration is obsolete.
- **`docs/implementation-decisions.md`** — new, empty but for this phase's
  entries: why `minSdk` stays at 24, why `versionCode` starts at 2, why
  there is no data migration from the Capacitor build, why `legacy-web/`
  survives until Phase 6.
- **`docs/test-plan.md`** — new. Phase 0's section: install over an existing
  Capacitor build and confirm it replaces it; theme picker changes the theme
  and survives a restart; language picker switches every visible string and
  survives a restart; the drawer reaches all destinations.
- **`README.md`** — a short "being rewritten" note at the top pointing at
  `docs/plan/README.md` is enough for now. The real rewrite is Phase 6.
- **`ROADMAP.md`** — delete the Android section's Firebase/Play Store items,
  which describe a build that no longer exists. Keep the file.
- **`CHANGELOG.md`** — open a `## [Unreleased]` section with this phase's
  bullets.

## Deliverables

- [ ] `legacy-web/` in place, `npm test` green inside it, its README written.
- [ ] Capacitor project, the three old workflows and the obsolete docs deleted.
- [ ] Gradle project builds: `./gradlew assembleDebug` (or CI green).
- [ ] App installs, launches, shows the Teams empty state.
- [ ] Theme picker works and persists.
- [ ] Language picker switches Italian ↔ English and persists.
- [ ] Drawer navigates between Teams, Roster and Settings.
- [ ] `android-ci.yml` green on the branch.
- [ ] `CLAUDE.md`, `docs/STATUS.md`, `docs/implementation-decisions.md`,
      `docs/test-plan.md`, `README.md`, `ROADMAP.md`, `CHANGELOG.md` updated.

## Tests

Thin by design — there is almost no logic yet.

- `ThemeModeTest` — `valueOf` round-trip and the invalid-value default.
- `ThemePreferencesTest` (Robolectric, `@Config(sdk = [26])`) — write then
  read back a theme mode; an empty store yields `SYSTEM`.

## Not in this phase

Room entities, any network call, any real screen content, the dataset sync,
the release pipeline, the launcher-icon redesign. If the app shows a list of
anything, you have gone too far.
