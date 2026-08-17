# Android Build Guide

Operational, volatile detail for the Capacitor Android build lives here so
`CLAUDE.md` can stay limited to stable invariants. If a Capacitor/AGP/Gradle
upgrade changes a version number below, update this file, not `CLAUDE.md`.

## Prerequisites

- Node.js **22+** and npm. Stricter than the plain web app (which only
  needs 18+): `@capacitor/cli` 8.x refuses to run on Node 20, failing
  `cap sync`/`cap add` with `The Capacitor CLI requires NodeJS >=22.0.0`.
- JDK 21 (Temurin recommended). Matches the AGP 8.13 / Gradle 8.14 toolchain
  `@capacitor/android` generated into `android/`.
- Android SDK (via Android Studio, or `sdkmanager` standalone) with at least
  platform 36 and the matching build-tools installed. Android Studio's SDK
  Manager is the easiest path.
- An `android/local.properties` pointing `sdk.dir` at your SDK — Android
  Studio writes this automatically on first open; it's gitignored.

## Building locally (debug)

```bash
npm run android:build   # builds dist-android/ (Android web bundle) + cap sync android
cd android
./gradlew assembleDebug # debug-signed APK at app/build/outputs/apk/debug/
```

Or open `android/` in Android Studio (`npm run android:open`) and run from
there — this also gets you the emulator, Logcat, and layout inspector.

## Generating a release keystore

Only needed once per signing identity. Keep the resulting `.keystore`/`.jks`
file and its passwords out of git — `.gitignore` already excludes them.

```bash
keytool -genkeypair -v \
  -keystore coverdex-release.keystore \
  -alias coverdex \
  -keyalg RSA -keysize 2048 -validity 10000
```

`keytool` prompts for a store password, a key password, and identity fields
(name/org/etc. — any values are fine for a sideload-only app).

## Signed local build

1. Create `android/keystore.properties` (gitignored) pointing at the
   keystore you generated:

   ```properties
   storeFile=/absolute/path/to/coverdex-release.keystore
   storePassword=...
   keyAlias=coverdex
   keyPassword=...
   ```

2. Build:

   ```bash
   cd android
   ./gradlew assembleRelease bundleRelease
   ```

   Signed APK: `app/build/outputs/apk/release/app-release.apk`
   Signed AAB: `app/build/outputs/bundle/release/app-release.aab`

   Without `keystore.properties`, `assembleRelease`/`bundleRelease` still run
   but produce an **unsigned** release artifact.

## GitHub Secrets used by CI (`.github/workflows/android-build.yml`)

Names only — set the values in the repo's Settings → Secrets and variables →
Actions. Never commit them.

| Secret | Contents |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | `base64 -w0 coverdex-release.keystore` output |
| `ANDROID_KEYSTORE_PASSWORD` | keystore's store password |
| `ANDROID_KEY_ALIAS` | key alias used when generating it (`coverdex` above) |
| `ANDROID_KEY_PASSWORD` | key password (often equal to the store password) |
| `FIREBASE_APP_ID` | the Android app's App ID from Firebase project settings |
| `FIREBASE_SERVICE_ACCOUNT` | full JSON content of a Firebase service account key with App Distribution access |

CI decodes the base64 secret to `android/release.keystore` and writes
`android/keystore.properties` from the other three, both gitignored and
deleted at the end of the job.

**Set `ANDROID_KEYSTORE_BASE64` via the `gh` CLI, not copy-paste into the
GitHub web UI.** Pasting a multi-KB base64 blob by hand is exactly the
kind of thing that silently drops a character or picks up a shell prompt
artifact (zsh's trailing `%` when output doesn't end in a newline is a
classic one) — the result decodes to garbage or an empty file, and CI
fails on "Decode release keystore" with `base64: invalid input`. Pipe it
straight from the encoder into the secret instead, so nothing passes
through a clipboard:

```bash
base64 -w0 coverdex-release.keystore | gh secret set ANDROID_KEYSTORE_BASE64 -R marcogn/CoverDex
```

If CI ever does fail with that error, the fix is to re-run the command
above (against the same keystore file — never regenerate the keystore
itself, see above) and re-run the workflow; both `android-build.yml` and
`release.yml` fail fast with a pointer back to this section if the
decoded file is invalid or empty.

These secrets are optional at the workflow level: if `ANDROID_KEYSTORE_BASE64`
is unset, the decode/write steps are skipped entirely and the build still
succeeds, producing an **unsigned** release APK/AAB (with a CI warning
annotation) — debug always uses Android's default debug signing regardless.
Add the four signing secrets whenever you want CI-built release artifacts
to be signed — no workflow change needed once they're set. Same story for
`FIREBASE_APP_ID`/`FIREBASE_SERVICE_ACCOUNT`: without them, the Firebase
distribution steps are skipped with a warning instead of failing the build.

## Firebase App Distribution setup

CI never uploads Android build output as a GitHub Actions artifact — this
repo is public, and Actions artifacts are downloadable by any signed-in
GitHub user with read access, debug builds included. Firebase App
Distribution is the replacement: free, supports both debug and release
APKs, and only ever reaches testers you've explicitly invited by email.

1. **Create (or reuse) a Firebase project** at
   [console.firebase.google.com](https://console.firebase.google.com/).
   Any Google account can create one; this doesn't require a paid plan.
2. **Add an Android app** to the project with application ID
   `com.marcogn.coverdex` (must match `capacitor.config.ts`'s `appId`
   exactly). You don't need to download/commit `google-services.json` —
   this project doesn't use any other Firebase service, only App
   Distribution, which authenticates via the service account below instead.
3. **Find the App ID**: Project settings → General → your Android app →
   "App ID" (format `1:1234567890:android:abcdef...`). This is
   `FIREBASE_APP_ID`.
4. **Create a service account** with App Distribution access: Project
   settings → Service accounts → Generate new private key (or, more
   narrowly scoped, create one via Google Cloud IAM with the
   `Firebase App Distribution Admin` role). This downloads a JSON key
   file — its full contents become the `FIREBASE_SERVICE_ACCOUNT` secret.
5. **Create two tester groups** in Firebase console → App Distribution →
   Testers & Groups: `internal-debug` and `internal-release` (the exact
   names the workflow's `groups:` inputs reference). Add your own email to
   both to start; add others later only if you want wider testing.
6. **Add the two secrets** to the repo (Settings → Secrets and variables →
   Actions): `FIREBASE_APP_ID`, `FIREBASE_SERVICE_ACCOUNT`.

Testers receive an email invite the first time a build reaches their
group, with a link to install the Firebase App Tester app (or open the APK
directly on Android) — no Play Store involved.

## CI job structure and one-shot debug builds

`.github/workflows/android-build.yml` has three jobs:

- **`build`** — runs on every push to the Android dev branch, and on manual
  `workflow_dispatch`. Web tests, `gradle lint`/`testDebugUnitTest`
  (required gate), and a signed release APK + AAB. This is the only job
  that runs automatically.
- **`debug-build`** — `workflow_dispatch` only, never runs on push. Builds
  and distributes a debug APK (default Android debug signing, no keystore
  secret needed). Trigger it from **Actions → Android Build → Run
  workflow** whenever you actually want a debug build on a device — it
  depends on `build` passing first, so it never ships a debug APK from a
  red build.
- **`instrumented-test`** — `workflow_dispatch` only, the Espresso smoke
  test on an emulator (see below).

Because `debug-build` only ever runs as part of a manually-triggered
workflow run, you get a debug APK exactly when you ask for one — never as
a side effect of ordinary pushes. If you don't want a particular debug
build's record kept around, delete that workflow run from **Actions →
Android Build → (the run) → ⋯ → Delete workflow run** — this removes the
run and its logs from the repository entirely. It does not touch whatever
Firebase already distributed to testers; remove a release from testers'
view in Firebase console → App Distribution if you don't want it
reachable there either.

## Cutting a public release

`.github/workflows/release.yml` is the one workflow that publishes
anywhere public — everything above (Firebase App Distribution) only ever
reaches invited testers. It's manual-only (**Actions → Release → Run
workflow**) and, given a version like `1.0.0` (or a blank input, which
reuses whatever version is already in `package.json` — the right choice
for the first release):

1. Validates the version and checks a `vX.Y.Z` tag doesn't already exist.
2. Writes that version into `package.json`, `package-lock.json`, and
   `android/app/build.gradle`'s `versionName`/`versionCode` (derived
   deterministically from the semver: `major*10000 + minor*100 + patch`).
3. Runs the web test suite, builds the plain web app (for GitHub Pages)
   and the Android bundle, and builds a **signed** release APK — signing
   is required here (unlike `android-build.yml`'s optional signing for
   internal testing), since an unsigned APK can't be installed at all and
   this release is public.
4. Only once both builds have actually succeeded, it publishes
   everything: opens a small `chore(release): vX.Y.Z` PR against `main`
   with the version bump and merges it (`main` requires pull requests —
   see "Branch protection and the version-bump PR" below), publishes a
   GitHub Release tagged `vX.Y.Z` with the matching
   [`CHANGELOG.md`](../../CHANGELOG.md) section (`## [X.Y.Z]` up to the
   next version heading) as the release notes and the signed APK attached
   directly (as a plain file, not zipped, not through
   `actions/upload-artifact`), and redeploys GitHub Pages from the same
   build — all in the same run, so the Android release and the live site
   always carry the same version. See
   [`docs/DEVELOPMENT.md`](../DEVELOPMENT.md) → "Keeping the web and
   Android releases in sync".

Requires the same `ANDROID_KEYSTORE_BASE64`/`ANDROID_KEYSTORE_PASSWORD`/
`ANDROID_KEY_ALIAS`/`ANDROID_KEY_PASSWORD` secrets as `android-build.yml`
(see above) — no Firebase secrets needed, this workflow never touches
Firebase. Before running it for a version after `1.0.0`, add a new
`## [X.Y.Z]` entry to `CHANGELOG.md` first so the release has real notes
instead of the generic fallback text.

### Branch protection and the version-bump PR

`main` has "Require a pull request before merging" turned on (repo
Settings → Branches), so `release.yml` can't just `git push` the version
bump directly — a straight push gets rejected with `GH006: Protected
branch update failed`. Instead it opens a real PR (`release/vX.Y.Z` →
`main`), approves it, and merges it with `gh pr merge --squash --auto`,
all as the workflow's own `GITHUB_TOKEN`. Two repo settings make this
possible without weakening branch protection at all:

- **Settings → Actions → General → Workflow permissions**: "Read and
  write permissions", plus "Allow GitHub Actions to create and approve
  pull requests" — without the second one, `gh pr review --approve`
  fails with "GitHub Actions is not permitted to approve pull requests".
- **Settings → General → Pull Requests → "Allow auto-merge"** — without
  this, `gh pr merge --auto` errors out. This isn't strictly required:
  the step only emits a `::warning::` and moves on if the merge can't be
  automated (e.g. auto-merge is off, or a required status check is still
  pending), since the GitHub Release, APK, and Pages deploy don't depend
  on the PR having landed yet — only on the files already built on disk.
  A stray unmerged `release/vX.Y.Z` PR just needs a manual merge
  afterward if that happens.

No bypass list, no PAT, no loosening of "require pull request" — the
workflow follows the same rule everyone else does.

## Running the Espresso smoke test locally

`android/app/src/androidTest/java/com/marcogn/coverdex/MainActivitySmokeTest.java`
asserts `MainActivity` reaches `RESUMED`, its `WebView` is displayed, and the
Capacitor bridge has loaded a URL. It needs a running emulator or a
connected device:

```bash
cd android
./gradlew connectedAndroidTest
```

This test is **not** part of the required `build` job in CI — see
`ROADMAP.md` and the `instrumented-test` job in `android-build.yml`, which
runs it on an emulator only via manual `workflow_dispatch`.

## Icon and splash generation

PWA icons are generated at build time by `scripts/generate-icons.mjs`
using the `sharp` package. They're gitignored and regenerated on every
deploy — don't commit icon PNG files. To regenerate locally:
`npm run generate-icons`.

The same script also writes `assets/icon.png` and `assets/splash.png`
(gitignored), the source images `@capacitor/assets` reads to generate the
Android launcher icon densities and splash screens under
`android/app/src/main/res/` (those generated `res/` files **are**
committed — see [`PLATFORM.md`](PLATFORM.md)). Regenerate both together
with `npm run android:icons`, which chains three steps:

1. `node scripts/generate-icons.mjs` — regenerates the PWA icons and the
   `assets/` source images.
2. `npx capacitor-assets generate --android --iconBackgroundColor '#5b21b6'
   --iconBackgroundColorDark '#5b21b6'` — regenerates the Android launcher
   icon and splash resources. The background color flags matter: without
   them `@capacitor/assets` defaults the adaptive icon's background layer
   to plain white.
3. `node scripts/fix-android-adaptive-icon.mjs` — patches
   `mipmap-anydpi-v26/ic_launcher.xml` and `ic_launcher_round.xml` after
   every regeneration. `@capacitor/assets` hardcodes a 16.7% `<inset>`
   around *both* adaptive-icon layers (see its own
   `dist/platforms/android/index.js` template, no CLI flag controls this);
   that's correct for the foreground safe zone but wrong for the
   background, which needs to bleed to the full 108dp canvas. Left
   unpatched, the inset background leaves a thin ring uncovered by any
   layer at all, which showed up as a visible white/transparent border
   around the installed app icon. The script rewrites just the
   `<background>` element to a plain, uninset drawable reference; it
   errors out loudly if `@capacitor/assets`'s generated XML no longer
   matches the pattern it expects, so a future tool upgrade that changes
   this template won't fail silently.

Run `npm run android:icons` (not the three commands individually) whenever
the icon or splash source changes — `capacitor-assets` writes straight into
`android/app/src/main/res/`, so unlike `dist-android/` this doesn't also
need a `cap sync` pass.

## Common Capacitor / Gradle / AGP troubleshooting

- **"Unsupported class file major version"** — your JDK doesn't match what
  AGP/Gradle expect. Confirm `java -version` reports 21 and that
  `JAVA_HOME` points at it (Android Studio has its own bundled JDK setting
  under Settings → Build Tools → Gradle that can silently override this).
- **AGP/Gradle mismatch after upgrading Capacitor** — `npx cap add android`
  and `npx cap sync` don't rewrite `android/build.gradle` or
  `android/gradle/wrapper/gradle-wrapper.properties` on their own once the
  project exists. After bumping `@capacitor/android`/`@capacitor/cli`, diff
  a fresh scaffold (`npx cap add android` in a scratch dir) against the
  committed `android/` to see what version bumps you need to port over by
  hand.
- **`SDK location not found`** — `android/local.properties` is gitignored
  and machine-specific. Create it with `sdk.dir=/path/to/Android/sdk`, or
  open the project in Android Studio once and let it generate the file.
- **Gradle can't find compileSdk 36 / build-tools** — install the matching
  SDK Platform and Build-Tools via Android Studio's SDK Manager (or
  `sdkmanager "platforms;android-36" "build-tools;36.0.0"`). AGP can
  auto-download these in CI (`android-actions/setup-android`), but a local
  SDK install won't fetch them without an explicit `sdkmanager` install or
  accepting the Android Studio prompt.
- **`assembleRelease` produces an unsigned APK** — `keystore.properties` is
  missing or not found at `android/keystore.properties`; see "Signed local
  build" above.
- **Stale WebView content after a web-only change** — you edited `src/` but
  forgot `npx cap sync android` (or `npm run android:build`), which is what
  copies `dist/` into `android/app/src/main/assets/public`.
