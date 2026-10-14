---
name: deps-triage
description: Gives a verdict on each open Dependabot pull request of this repository - safe to merge, to look at, or to close - with the reason, reading CI, the update type and the release notes. Never approves, merges or closes. Use when the author types /deps-triage or asks about the Dependabot PRs ("le PR di dependabot", "aggiornamenti delle dipendenze").
argument-hint: "[optional: a PR number to look at only that one]"
---

# Triage the Dependabot pull requests

Generic for the author's Android projects (docs/claude.md). The result is a **verdict per PR**; the
author decides. Never approve, merge, close or push here; if the author then asks for an action on
a PR, that is a new request.

Request: `$ARGUMENTS` (empty: every open PR by `dependabot[bot]`).

## 1. Collect

With the GitHub tools: the open PRs whose author is `dependabot[bot]`. For each: title, the
dependencies and versions (from the body: "Bumps x from a to b", or the group's table), the group
(`build-toolchain`, `androidx`, `other`, `actions`), CI status on the head commit, merge conflicts,
age.

## 2. Judge each PR

**Update type** per dependency: major, minor or patch (the version numbers). A group takes its worst.

**Release notes**: open the changelog or release notes the Dependabot body links for each version
crossed. Look for: breaking changes, removed or deprecated APIs the project uses (Grep the code),
new requirements (`minSdk`, `compileSdk`, JDK, AGP, Kotlin version), behaviour changes.

Verdict:

- **Safe** - all of: CI green on the head; patch or minor; stable version; nothing in the notes that
  touches the project; not `build-toolchain`.
- **To look at** - any of: major; `build-toolchain` group (AGP, Kotlin, KSP, Compose must stay
  compatible with each other, and a green CI doesn't prove runtime behaviour or R8); notes mention a
  change in an API the project uses; CI red for a reason that looks fixable (say which); a library
  that touches what the spec restricts (permissions, network, storage).
- **Close** - any of: alpha, beta, RC or other pre-release (the project takes stable versions only,
  unless CLAUDE.md or the decisions file pins one); superseded by a newer Dependabot PR for the same
  dependency; a dependency the project no longer uses.

A green CI is necessary, not sufficient: it runs lint, JVM tests and an R8 build, not the app on a
device.

## 3. Report

In Italian, short enough for a phone. A table: PR, what it updates (from -> to), type, CI, verdict,
reason in one line. Then:
- for **Safe** ones: that they can be merged as they are, and a device check worth doing if the
  library is user-facing;
- for **To look at**: what exactly to check, and the offer to take it to green in this session
  (`steward` rules) if the author wants;
- for **Close**: whether to add an `ignore` entry in `.github/dependabot.yml` so it doesn't come back.
