# CI and releases (GitHub Actions)

Every project of the author runs the same workflows. Their logic lives once, as **reusable
workflows**, in [claude-skill-android-kit](https://github.com/Marcogn/claude-skill-android-kit)
(`.github/workflows/`); each project's `.github/workflows/` only holds short callers with its own
values (`with:`) and secrets (`secrets:`). A fix in the kit reaches every project at the next run.
This page is part of the kit and is copied unchanged into each project.

| Workflow (caller) | Runs | Does | Produces |
|---|---|---|---|
| `android-ci.yml` (Android CI) | every pull request to `main`, every push to `main`; a new push cancels the run still going | lint, unit tests (with coverage if enabled), release build with R8 (unsigned), forbidden-permissions check on the packaged release manifest | lint, test and coverage reports (14 days). **No APK to install** |
| `build-apk.yml` (Build APK) | by hand: Actions → Build APK → Run workflow, on any branch | release build signed with the persistent key, signature check | the APK and R8's `mapping.txt` (30 days) |
| `release.yml` (Release) | by hand, with the version (`x.y.z`) | cuts `CHANGELOG.md`'s `[Unreleased]` and bumps the version, signed build, GitHub Release with the APK and `mapping.txt`, then commits the bump to `main` | the GitHub Release |
| `cleanup-runs.yml` (Clean up runs) | by hand | deletes every completed run except the latest of each workflow (logs and artifacts); releases are untouched | — |
| `claude.yml`, `claude-review.yml` | `@claude` mentions; PRs opened or marked ready | Claude on GitHub (see `docs/claude.md`); skip themselves without `CLAUDE_CODE_OAUTH_TOKEN` | comments, commits |

Callers pin the kit at `@v1`, a branch of the kit that moves (fast-forward) with compatible changes;
a change that would break callers gets `@v2`, and projects move to it on purpose.

## Values and secrets per project

The caller's `with:` sets `app-module` (usually `app`), `java-version`, and for Android CI
`forbidden-permissions` (space separated, empty to skip), `coverage` and `release-build-args`
(extra Gradle arguments for its release build only, e.g. `-PofflineSeed`; not secret, so not in
`BUILD_ENV`). Secrets are passed by name
in `secrets:` (a reusable workflow in another repository doesn't receive the caller's secrets on its
own). A project whose secrets still have other names maps them there, e.g.
`RELEASE_KEYSTORE_BASE64: ${{ secrets.ANDROID_KEYSTORE_BASE64 }}`, until they are renamed.

Extra environment variables the build needs **from secrets** (an API client id, say) go in the
optional `BUILD_ENV` secret as `KEY=VALUE` lines, e.g.
`BUILD_ENV: "API_CLIENT_ID=${{ secrets.API_CLIENT_ID }}"`: the workflows export them before Gradle.

## Which APK to install

Always the one from **Build APK** (or a Release). It is the build users get (R8 on) and it is signed
with the same key every time, so it installs over the app already on the phone and keeps its data.
CI deliberately uploads no APK: a debug APK is signed with the runner's throwaway key (installing it
means uninstalling the app and losing its data) and is not minified, so it doesn't test what ships.

## Reading a crash in a release build

R8 renames classes and methods, so a stack trace from a release build looks like `a.b.c(Unknown
Source)`. The `mapping.txt` of *that exact build* turns it back into readable code:

```bash
# retrace ships with the Android SDK command-line tools
retrace mapping.txt stacktrace.txt
```

Build APK uploads it next to the APK; a Release attaches it as `<repo>-x.y.z-mapping.txt`.

## Setting up a new project

1. Run `/android-kit` in a Claude Code session on the repository (see `docs/claude.md`): it copies
   the callers and this page. By hand: copy `template/` from the kit and set the `with:` values.
2. Coverage: the app module needs
   `debug { enableUnitTestCoverage = providers.gradleProperty("coverage").isPresent }`.
3. The app module's `build.gradle.kts` must:
   - have `versionCode = N` and `versionName = "x.y.z"` as plain literals (Release edits them with `sed`);
   - read the signing data from the environment, signing only when it is there:

     ```kotlin
     signingConfigs {
         create("release") {
             val keystorePath = System.getenv("RELEASE_KEYSTORE_PATH")
             if (!keystorePath.isNullOrBlank()) {
                 storeFile = file(keystorePath)
                 storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                 keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                 keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
             }
         }
     }
     buildTypes {
         release {
             if (!System.getenv("RELEASE_KEYSTORE_PATH").isNullOrBlank()) {
                 signingConfig = signingConfigs.getByName("release")
             }
         }
     }
     ```
4. `CHANGELOG.md` with a `## [Unreleased]` section whose entries are top-level bullets starting
   with a bold summary (`- **Summary.** detail`): Release publishes only the bold summaries.
5. Repository secrets (Settings → Secrets and variables → Actions):

   | Secret | Content | Used by |
   |---|---|---|
   | `RELEASE_KEYSTORE_BASE64` | the keystore file in base64 (`base64 -w0 release.keystore`) | Build APK, Release |
   | `RELEASE_KEYSTORE_PASSWORD` | keystore password | Build APK, Release |
   | `RELEASE_KEY_ALIAS` | key alias | Build APK, Release |
   | `RELEASE_KEY_PASSWORD` | key password | Build APK, Release |
   | `RELEASE_PUSH_TOKEN` | fine-grained PAT, this repository only, Contents read/write: lets Release push the version bump to a protected `main` | Release |
   | `CLAUDE_CODE_OAUTH_TOKEN` | optional: `claude setup-token` (one token for all repositories, valid a year) | Claude, Claude review |
   | project-specific | optional: whatever the build reads from the environment, passed through `BUILD_ENV` | the caller decides |

   Same names in every project. Build APK and Release check them first and name the missing ones.
6. One keystore per app, kept outside the repository and backed up: losing it means users can't
   update the app any more without uninstalling it.

## Notes

- Android CI runs on pull requests without secrets, so its release build is unsigned: it checks that
  R8 and resource shrinking work, not the signature. Build APK checks the signature.
- Build APK and Release print the certificate's SHA-256: it must be the same on every build.
- Android CI's lint and tests run on the debug variant (the usual Gradle tasks), the build on release.
