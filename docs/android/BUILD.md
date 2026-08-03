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
