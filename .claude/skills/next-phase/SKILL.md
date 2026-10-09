---
name: next-phase
description: Starts the next development sub-phase following the Session protocol in CLAUDE.md (model check, prerequisites, scope). Use when the author says "go on", "next phase", "prossima fase", "vai avanti" or names a sub-phase to start.
argument-hint: "[sub-phase, e.g. 9 or 10a; default: the next one]"
---

# Start a sub-phase

Generic for the author's phased projects (docs/claude.md): everything project-specific is read
from CLAUDE.md, the plan files it points to and the spec. Names differ between projects: the phase
table may be in CLAUDE.md or in a file it points to (e.g. `docs/roadmap.md`, `docs/plan/`), the
decisions file may be `docs/decisions.md` or `docs/decisioni.md`, the docs may be in Italian.
Follow CLAUDE.md's pointers. If there is no session protocol or phase plan at all, say so and stop.

Requested sub-phase: `$ARGUMENTS` (empty = the next one in CLAUDE.md "Current status").

## 1. Model check (before anything else)

1. Find the sub-phase in the phase table (CLAUDE.md or the plan it points to) and its model.
2. Find the model this session runs on. In a cloud session call the `get_session` tool
   (claude-code-remote) without `session_id` and read `session_context.model` and
   `external_metadata.last_served_model`; locally use the model named in the system prompt.
3. If it differs from the row (Opus/Sonnet family is what matters, not the minor version), or you
   can't tell, **stop** and reply in one line: sub-phase, model needed, how to switch
   (`/model opus`, `/model sonnet`, or a new session). Continue only if the author says so.

## 2. Read, in this order

1. The row (scope, "Done when", device checks).
2. The plan section it points to, then every spec section that section cites.
3. The decisions file and the ADRs the plan names. Decisions there are in force.
4. The code the plan names. Use the Explore agent for broad searches, so file dumps stay out of
   this conversation.

## 3. Prerequisites

- The previous sub-phase is merged (`git log origin/main`), or CLAUDE.md says it is done.
- Open questions the plan lists for this sub-phase have an answer in the decisions file or
  CLAUDE.md. If not, ask them all at once (AskUserQuestion), with a recommended option each.

## 3b. Open bugs

The plan lives in the docs; GitHub issues hold only bugs and ideas (docs/claude.md). Issues the
plan already assigns to this sub-phase (written by `/new-plan`, "closes #n") are in scope: list them
first, as settled. Then list the repository's other **open issues labelled `bug`** with the GitHub tools (skip this step if there are none
or the tools aren't available). For each, one line: number, title, and whether it touches code this
sub-phase changes anyway. Ask the author which to include (AskUserQuestion, multi-select; recommend
the ones in the same area, never more than the sub-phase can absorb). Included bugs become tasks
of this sub-phase, each with a test that fails before the fix where a JVM test can reach it. Ask
together with the open questions above, so the author answers once.

## 4. Plan, then work

- Session title `<sub-phase> <Name>`; the branch is the one the environment gives, else the
  sub-phase in kebab-case.
- Write a task list (TaskCreate) from the plan's items and "Done when". Keep it current.
- Only this sub-phase. Anything else you notice goes into CLAUDE.md "Current status" as a note.
- Unclear or infeasible requirement: stop and ask, as CLAUDE.md says.
- Use the `verify` skill while working; when "Done when" holds, run `/close-phase`.
