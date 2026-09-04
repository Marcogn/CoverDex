# Phase 6 — Signing, release pipeline and the docs rewrite

**Goal:** a signed APK published as a GitHub Release, the repository's docs
describing the app that now exists, and the last of the web app removed.

**Depends on:** Phase 5. Do not start it before the app is feature-complete
— a release pipeline for an incomplete app is wasted work.

---

## 1. Signing

Follow `../Hall-Of-Memories/docs/release-signing.md` step for step; it is
the same keystore procedure, and copying it is faster than rederiving it.

The Gradle wiring already exists — Phase 0 put the conditional `release`
`signingConfig` in `app/build.gradle.kts`, reading
`RELEASE_KEYSTORE_PATH`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`
and `RELEASE_KEY_PASSWORD` from the environment and skipping signing when
`RELEASE_KEYSTORE_PATH` is blank. This phase is the keystore, the four
repository secrets (plus `ANDROID_KEYSTORE_BASE64`) and the documentation.

`docs/release-signing.md` in this repo documents: generating the keystore,
base64-encoding it, the exact secret names, and — importantly — that
**losing the keystore means the app can never be updated in place again**.

> The sibling project's first three release attempts all failed on
> `ANDROID_KEYSTORE_BASE64` not being valid base64. Make the decode step
> fail with an actionable message rather than a bare `base64: invalid
> input`, and verify the secret decodes before the first real run.

## 2. Workflows

Three, mirroring Hall of Memories. Copy them and change the project name and
artifact names.

| Workflow | Trigger | Does |
|---|---|---|
| `android-ci.yml` | push / PR | test + lint + `assembleDebug`. Already exists from Phase 0. |
| `build-apk.yml` | `workflow_dispatch` | a debug APK as a run artifact, for ad-hoc testing |
| `release.yml` | `workflow_dispatch` with an `x.y.z` input | validate the version, cut the changelog, bump `versionName`/`versionCode`, build, sign, publish a GitHub Release with the APK attached, then push the bump |

There is **no** Pages deploy, **no** web build and **no** Firebase App
Distribution. All three are gone deliberately; the release is a signed APK
on a GitHub Release and nothing else.

`release.yml` extracts the release body from `CHANGELOG.md`: the **bold
lead-ins** of the top-level bullets in the matching `## [x.y.z]` section,
followed by a link back to that section. That is the whole reason the
changelog convention in `CLAUDE.md` exists — a version whose bullets have no
bold lead-ins falls back to publishing the section verbatim.

## 3. Cut `[Unreleased]` to `[2.0.0]`

Phases 0–5 have been accumulating bullets. Turn them into a real release
entry and lead with what actually changed for a user upgrading from the
Capacitor build:

- **CoverDex is now a native Android app.** Kotlin, Jetpack Compose,
  Material 3. The WebView is gone.
- **First launch is effectively instant.** The catalogue download went from
  ~3875 requests and ~426 MB to **8 requests and ~208 KB** — measured; see
  `docs/plan/reference-pokedata.md`.
- **The web app and its GitHub Pages site are discontinued.**
- **Saved teams do not carry over.** This is the one that will actually hurt
  someone, so say it first, say it plainly, and tell them what to do:
  export each team to Showdown format from the old app *before* updating.
  Do not bury it under the features.
- The generation filter now uses each species' real introduction
  generation, so alternate forms appear under the right generation.

## 4. Docs

- **`README.md`** — full rewrite. A product page for an Android app:
  what it does, screenshots, how to install the APK, that it is sideload-only
  and offline-first. Every reference to the PWA, the live site, Vite,
  Capacitor and `npm run` is removed.
- **`CLAUDE.md`** — tick the phase status to done and re-read it end to end
  against the code that now exists. Anything in it that was aspirational in
  Phase 0 is now either true or must be deleted.
- **`docs/STATUS.md`** — a real snapshot: what shipped, what is deferred,
  what has never been verified on a device.
- **`ROADMAP.md`** — what is deliberately not done. Honest candidates: a
  Play Store listing, iOS, per-slot EV/IV tracking, importing from other
  team formats.
- **`docs/plan/`** — keep it. It is the record of how the app was built and
  the phase files carry decisions that are not written down anywhere else.
  Add a line at the top of `README.md` there saying every phase is done.

## 5. Delete `legacy-web/`

Last commit of the phase, on its own, so it is trivially revertable:

```bash
git rm -r legacy-web
```

Before you do, confirm every one of these is genuinely ported, by grepping
the Kotlin for each: the coverage engine, the suggestion engine, the team
generator, `STARTER_FINALS`, the ability-effects map, the Showdown parser,
and **both** i18n string files. The React code is in git history if
something turns up missing later, but the point of this check is not to need
it.

## Deliverables

- [ ] Keystore generated, secrets set, decoding verified.
- [ ] `docs/release-signing.md` written.
- [ ] `build-apk.yml` and `release.yml` in place; `android-ci.yml` still green.
- [ ] `CHANGELOG.md` `[2.0.0]` cut, leading with the data-loss warning.
- [ ] `README.md`, `CLAUDE.md`, `docs/STATUS.md`, `ROADMAP.md` rewritten.
- [ ] `legacy-web/` deleted, after the port checklist above.
- [ ] One successful end-to-end `release.yml` run: the Release exists, the
      APK is attached, it installs on a device, and the notes match the
      changelog.

## The one thing that is easy to skip

**Install the signed APK on a real device over an existing Capacitor
install** and confirm it upgrades rather than failing with a signature
mismatch. Same `applicationId`, `versionCode 2` — but if the release
keystore is not the one the Capacitor build was signed with, the upgrade
fails and every existing user has to uninstall first. Find that out now, and
if it is the case, say so in the release notes. This is exactly the class of
problem that only appears on hardware, and it is the reason
`docs/test-plan.md` exists.
