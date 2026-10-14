---
name: close-phase
description: Closes the current sub-phase or task as CLAUDE.md requires - checks green, independent review of the diff (self-review), README/CLAUDE.md/CHANGELOG/decisions updated, commit, push, draft PR, device checks listed. Use when the work of a sub-phase is done, or when the author says "chiudi", "close the phase", "wrap up".
---

# Close a sub-phase

Generic for the author's Android projects (docs/claude.md); the project's own rules are in
CLAUDE.md ("Fixed rule", "Session protocol") and win over this file.

## 1. "Done when" holds

Re-read the sub-phase row and the plan's items. For each one, point to the code or test that
satisfies it. Anything missing: finish it, or ask the author whether it moves to a later phase.

## 2. Checks

Run the `verify` skill. Red means not done.

## 3. Independent review

Run the `self-review` skill (checks, `/code-review`, the `architecture-reviewer` agent, fixes, and
the record the review-gate hook looks for). Its report goes into the PR body and the hand-back.

## 4. Documents (what CLAUDE.md asks to update; section names vary by project)

- `CHANGELOG.md`: every user-visible change under `## [Unreleased]` as `- **Summary.** detail`.
- `CLAUDE.md`: "Current status" (done, date, PR, test count, next sub-phase), "Where things are"
  and "Rules that aren't obvious" if the code changed them, technical limits found.
- An **a** sub-phase: the handoff (at most 15 lines) for its **b**.
- `README.md` if features or commands changed; the decisions file for new decisions.
- Keep the docs' language and style; trim, don't pile up.

## 5. Commit, push, PR

- One commit per coherent step, message `<sub-phase>: <what>` like the history (`git log`).
- Push to the session's branch (named for what it does, `docs/claude.md` "Names"); open a **draft** PR with the repository's template
  (`.github/pull_request_template.md`), then subscribe to its activity.
- Issues: for each bug included at the start (`next-phase` 3b) and fixed, a line `Fixes #<n>` in
  the PR body, one per issue, so GitHub closes it on merge. A bug fixed only in part gets
  `Refs #<n>` and a comment on the issue saying what is left.
- Bugs or ideas found during the work and left out of scope: propose them as new issues (title and
  body following `.github/ISSUE_TEMPLATE/`, label `bug` for bugs) and open them once the author
  agrees, instead of adding notes to CLAUDE.md.

## 6. Hand back to the author

End with the sub-phase's **device checks** (phase table and plan) as a short checklist the
author can run on the phone, and what is left open. Don't start the next sub-phase.
