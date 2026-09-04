# Changelog

All notable changes to CoverDex are recorded here, one entry per
release. Format loosely follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/);
versions follow [Semantic Versioning](https://semver.org/).

## [Unreleased]

CoverDex is being rewritten as a native Android app; see
[`docs/plan/README.md`](docs/plan/README.md). This section accumulates
phase by phase and is cut to a real `## [2.0.0]` entry in Phase 6.

- **The web app and GitHub Pages are discontinued.** The React/Capacitor
  codebase is parked in `legacy-web/` as a behavioural reference for the
  rewrite (its test suite stays green throughout) and is deleted once the
  native app has everything it needs from it.
- **The Android app is now fully native** — Kotlin, Jetpack Compose,
  Material 3, Room, Hilt — instead of a Capacitor WebView shell. Same
  `applicationId` (`com.marcogn.coverdex`) so it installs over the old
  build.
- **Saved teams do not carry over from the old app.** A deliberate, clean
  break — see `docs/implementation-decisions.md`. Export your teams to
  Showdown format from the old app before updating, if you still have it
  installed.
- Theme (System/Light/Dark) and language (System/Italiano/English)
  settings, both persisted.

## [1.0.0]

First public release, shipped simultaneously as the web app (PWA, on
GitHub Pages) and as a native Android app.

This is the initial release, so there is no prior version to diff
against — every feature described in [`README.md`](README.md) is new as
of this version. See that file for the full feature list, and
[`docs/STATUS.md`](docs/STATUS.md) for the current implementation
snapshot.
