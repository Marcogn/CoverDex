---
name: steward
description: Repository rules for driving a pull request to green - how to read this project's CI failures, what counts as transient, how to answer review findings. Use when handling CI results, review comments or merge conflicts on a pull request of this repository.
---

# Driving a PR to green (Android projects)

Generic for the author's Android projects (docs/claude.md). Cloud sessions that watch a pull
request read this file from the PR's head branch before acting on CI or review events.

## CI here

`android-ci.yml` runs, in order: `lintDebug`, `testDebugUnitTest`, `assembleRelease` (R8), then a
check that the packaged release manifest declares none of `FORBIDDEN_PERMISSIONS`. Reports are
uploaded as artifacts (lint HTML, test XML), 14 days. `docs/ci.md`, where present, describes all workflows.

## Reading a failure

1. Fetch the failing job's log and find the first `FAILED` task and its `What went wrong`.
2. Reproduce locally with the same task before changing anything (`verify` skill, or just that
   task). A fix you could not reproduce first is a guess: say so.
3. Classify:
   - **Network/infra** (the job died before a test ran: plugin not found, `Could not resolve`,
     429, runner lost): one re-run, once. A second failure is real.
   - **Lint error**: fix the code. A `tools:ignore` or a baseline entry only with the reason in
     the commit message, and only when the warning is a false positive.
   - **Test failure**: never a flake by default. Robolectric tests are deterministic; a test that
     fails only in CI usually depends on locale, time zone or file order: fix that.
   - **R8 only** (`assembleRelease` fails, debug works): missing keep rule or reflection; add the
     narrowest rule to `proguard-rules.pro` with a comment saying which class needs it and why.
   - **Forbidden permission**: a dependency merged it into the manifest. Remove it with
     `tools:node="remove"` only if the feature doesn't need it; otherwise stop and ask, because
     the rule comes from the spec.
4. Never skip, disable or delete a test, never lower `minSdk`/`targetSdk` or a dependency to dodge
   an error without asking.

## Review findings

- Check each one against the code (file:line) before acting. Real and in proportion: fix it.
  Wrong or not worth its code: reply on the thread with the reason.
- Findings that cite CLAUDE.md, REVIEW.md or an ADR weigh more than style.
- Dependabot PRs: read the library's release notes for breaking changes; the project uses latest
  **stable** versions only (no alpha/beta/RC unless CLAUDE.md or the decisions file pins one).

## Before each push

`verify` green, CHANGELOG updated if the change is user-visible, and a commit message that says
what failed and why the fix is right.
