---
name: verify
description: Runs the Android checks CI runs (Android Lint, JVM unit tests, debug build) and reports what failed with the relevant lines. Use before every commit that changes code or resources, before opening or updating a pull request, and when the user asks whether the build is green.
allowed-tools: Bash(./gradlew *) Bash(LC_ALL=C.UTF-8 ./gradlew *)
---

# Verify the Android build

Generic: the same file in every Android project of the author (docs/claude.md). Project-specific
commands, if any, are in the project's CLAUDE.md under "Commands" and take precedence.

## Run

From the repository root, in one command so Gradle configures once:

```bash
LC_ALL=C.UTF-8 ./gradlew lintDebug testDebugUnitTest assembleDebug --console=plain
```

- Cloud session: the SessionStart hook has already installed the SDK and capped Gradle workers. If
  the build says the SDK is missing, run `bash .claude/hooks/android-sdk.sh` and retry.
- A build takes minutes: run it in the background and keep working, don't poll it.
- Only the module you changed? Prefix the tasks (`:app:testDebugUnitTest`).
- One test class while iterating: `./gradlew testDebugUnitTest --tests '*ClassName*'`, then the
  full run before the commit.

## Transient failures (retry)

These come from the network, not from the change: plugin "was not found in any of the following
sources", `Could not resolve` / `Could not GET`, HTTP 429 from Maven Central, a failed download of
Robolectric's `android-all-instrumented` (CLAUDE.md says how to fetch it by hand). Retry in a loop
that stops at the first other error (about 10 attempts, 15 s apart): on a cold Gradle cache several
attempts are normal, and each one downloads more. Anything else is real.

## Report

- Green: say so in one line, with the number of tests run (from
  `app/build/test-results/testDebugUnitTest/*.xml`) and lint's error/warning count.
- Red: the failing task, the first error with file:line, and for a test the assertion message
  (from the XML report, not the console summary). Fix it if it is in the code you changed;
  otherwise say what is failing and why it is not yours.
- Never skip, `@Ignore` or delete a test to get green, and never raise lint baselines or add
  `tools:ignore` without saying why in the commit message.
