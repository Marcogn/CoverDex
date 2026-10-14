---
name: self-review
description: Independent review of the work on this branch before it is committed as finished or turned into a pull request - verify, correctness review, project-rules review, fixes. Runs automatically when a session ends with unreviewed code (Stop hook) or when a PR is about to be opened; also use it whenever the author says "rivedi", "review", or a task is done.
---

# Review the branch's work

Generic for the author's Android projects (docs/claude.md). The review comes **after the code is
done and before the PR**: a PR opened without it is the failure this skill exists to prevent
(`.claude/hooks/review-gate.sh` stops the session end and the PR creation until it has run).
`/close-phase` runs this skill as its step 3; sessions that aren't a phase (a fix, a Dependabot
PR, an issue) run it on their own.

## 1. Scope

The diff of the branch against the base (`git diff $(git merge-base origin/HEAD HEAD)`, plus
untracked files). Only docs (`*.md`, `docs/`) changed: nothing to review, say so and stop.

## 2. Checks first

Run the `verify` skill. Red means the code isn't done: fix it before asking anyone to read it.

## 3. Two independent passes, in parallel

- `/code-review` (bundled skill) on the diff: correctness bugs.
- The `architecture-reviewer` agent: the project's own rules (CLAUDE.md "Rules that aren't obvious",
  REVIEW.md, ADRs). It sees the diff in a fresh context, which is the point: it doesn't share the
  assumptions you made while writing it.

## 4. Act on the findings

For each: check it against the code (file:line). Real and in proportion: fix it, with a test where a
JVM test can reach it. Wrong or not worth its code: leave it and keep the reason for the report.
Findings that cite CLAUDE.md, REVIEW.md or an ADR weigh more than style. If you changed code, run
`verify` again; if the fixes were more than a few lines, run the two passes again on them.

## 5. Record it

```bash
bash .claude/hooks/review-gate.sh done
```

It stores a fingerprint of the diff; any later change to the code makes the review stale and the
gate asks again. Run it only when the passes above really ran.

## 6. Report

In the final message: what was reviewed, the findings fixed, the ones left and why (one line each).
