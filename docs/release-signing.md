# Release signing

A signed release build needs one keystore, generated **once** and kept
forever. Regenerating it later changes the app's signature and makes it
impossible to upgrade any device that already has a build installed from
the previous one — Android refuses the install as a different app. This is
especially true for CoverDex: the Capacitor build already shipped with its
own signature, and the whole point of keeping `applicationId`
`com.marcogn.coverdex` and bumping `versionCode` to 2 (see
`docs/implementation-decisions.md`, "Phase 0") is so this native build can
install *over* it — that only works if this keystore's certificate matches
the one the Capacitor build was signed with. If it doesn't (e.g. the
Capacitor build's own keystore is lost), every existing installed copy has
to be uninstalled first; say so plainly in the first release's notes if
that turns out to be the case.

## 1. Generate the keystore

```bash
keytool -genkeypair -v \
  -keystore coverdex-release.keystore \
  -alias coverdex \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -storetype PKCS12
```

`keytool` prompts for a store password, a key password (can be the same as
the store password), and the certificate's distinguished-name fields (name,
org, city, etc. — these are public and cosmetic, put anything reasonable).
Note the passwords and the alias somewhere durable; they're needed for
every future signed build.

If the Capacitor build's original keystore still exists, **reuse it
instead of generating a new one** — that's the only way this release
installs over an existing copy rather than requiring a fresh install. Skip
straight to step 3 in that case.

## 2. Store it safely

- Keep `coverdex-release.keystore` in a password manager or an encrypted
  backup. **Never commit it to the repository** — `*.keystore`/`*.jks` are
  already in `.gitignore`, but that only stops an accidental `git add`, not
  a deliberate one.
- Losing this file (or forgetting its passwords) means every future
  release is signed with a *different* key, which Android treats as a
  different app — no installed copy can ever be upgraded again. There is
  no recovery from this short of publishing under a new `applicationId`.

## 3. Produce the GitHub secrets

The workflows (`build-apk.yml`, `release.yml`) read the keystore and its
credentials from repository secrets, never from a file in the repo:

```bash
base64 -w0 coverdex-release.keystore
```

Copy that output into a repository secret named `RELEASE_KEYSTORE_BASE64`.
Add these secrets under the repo's **Settings → Secrets and variables →
Actions**:

| Secret | Value |
|---|---|
| `RELEASE_KEYSTORE_BASE64` | the base64 output above |
| `RELEASE_KEYSTORE_PASSWORD` | the store password from step 1 |
| `RELEASE_KEY_ALIAS` | `coverdex` (or whatever alias was used) |
| `RELEASE_KEY_PASSWORD` | the key password from step 1 |
| `RELEASE_PUSH_TOKEN` | a fine-grained PAT scoped to this repo, Contents: Read and write — used only by `release.yml`'s final version-bump commit |

`app/build.gradle.kts`'s `signingConfigs { create("release") { ... } }`
block (written in Phase 0) reads `RELEASE_KEYSTORE_PATH`/
`RELEASE_KEYSTORE_PASSWORD`/`RELEASE_KEY_ALIAS`/`RELEASE_KEY_PASSWORD` as
environment variables — the workflows decode the base64 secret into a
temporary file at `$RUNNER_TEMP/release.keystore` and pass its path as
`RELEASE_KEYSTORE_PATH`. A local `./gradlew assembleRelease` with none of
these set still succeeds — the release build type is simply left unsigned
in that case, Android's own default.

> The sibling project's (Hall of Memories) first three release attempts all
> failed on `RELEASE_KEYSTORE_BASE64` not being valid base64. Both
> `build-apk.yml` and `release.yml` print the decoded keystore's byte size
> and SHA-256 right after decoding it, precisely so a signing failure later
> in the same run ("Given final block not properly padded", "Invalid
> keystore format", etc.) can be told apart from a corrupted/truncated
> secret without guessing — verify the secret decodes correctly (matches
> the local file's own `sha256sum`) before relying on the first real run.

## 4. Read the SHA-1 back out

```bash
./gradlew signingReport
```

Useful for verifying the keystore is the one actually in use, and is the
starting point for registering this app with any Google API that checks a
signing certificate (none are used in v1). `build-apk.yml` and
`release.yml` both print it after every signed build via the same
`signingReport` task, run against the decoded release keystore rather than
the default debug one.

## What this session could not do

Generating and custodying the actual production keystore, and setting the
five repository secrets above, are steps only the repository owner can
take responsibly — an agent session has no safe way to hold a signing key
long-term or to write GitHub repository secrets on someone else's behalf.
This document, `build-apk.yml` and `release.yml` are ready; the keystore
itself, the secrets, and the first real `release.yml` run (including
installing the result on a device already running the Capacitor build —
see `docs/plan/phase-6-release.md`, "The one thing that is easy to skip")
are still open until a human does them.
