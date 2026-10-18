---
name: issues-triage
description: Reads the open GitHub issues of this repository, collects what each one says and what it touches in the code, and proposes how to resolve them - grouped into sessions, each with the model to use (Sonnet or Opus) and the command to start it. Writes no code and changes no issue. Use when the author types /issues-triage or asks about the open issues ("le issue aperte", "cosa c'è da sistemare", "piano per le issue").
argument-hint: "[optional: issue numbers like #4 #9, or a label like bug]"
---

# Triage the open issues

Generic for the author's Android projects (docs/claude.md). The result is a **resolution plan in
the chat**: what each issue needs, how to group them into sessions, which model each session wants.
The author decides. **Never write code, never comment on, label, assign or close an issue here**;
if the author then asks for one of those, that is a new request. The plan of record stays in the
docs (docs/claude.md, "Issues"): an issue too big for one session goes to `/new-plan`, not into a
plan written here.

Request: `$ARGUMENTS` (empty: every open issue; numbers: only those; a label: only issues with it).

## 1. Collect

With the GitHub tools, the open issues of this repository. The issues API also returns pull
requests: skip them (and Dependabot's, which are `/deps-triage`'s). None open: say so in one line
and stop.

For each issue: number, title, labels, template (Bug or Task, from the labels and the body's
headings), author, age, last activity, and the comments, which often correct the body. From the body:
- **Bug**: steps, expected and actual, version and phone, stack trace;
- **Task**: goal, context, "Done when", out of scope.

Then, for each, the state around it:
- **In progress**: an open PR that mentions it (`Fixes #n`, `#n` in the title or body).
- **Planned**: the plan files or the phase table (CLAUDE.md and what it points to) already assign
  it to a phase (Grep `#n` there). Name the phase.
- **Duplicate**: another open issue describes the same thing.

## 2. Look at the code

For each issue not in progress, find where it lands, without fixing anything: the classes and
functions in the stack trace, the screen or feature the steps name, the files the "Context" cites.
Use the Explore agent when the search is wide, so file dumps stay out of this conversation. Read
the decisions file, the ADRs and the spec sections that apply.

Note per issue:
- the **area** (module, package, screen) and the files likely to change;
- whether the **cause is known** (a line you can point at) or still to find;
- whether it needs a **decision** the docs don't settle (a behaviour, a format, a library);
- what the spec restricts there (permissions, network, storage, `minSdk`);
- whether a JVM test can reach it (a test that fails before the fix), or only a device check.

An issue that doesn't say enough to act on (a bug with no steps or version, a task with no "Done
when"): mark it **needs info** and write the exact questions to ask its author. An issue about code
that no longer exists, or already fixed on `main` (`git log` mentions it, or the code shows it):
mark it **probably obsolete**, with the evidence.

## 3. Size, model, sessions

**Size**:
- **S**: local change, cause known, a file or two plus a test.
- **M**: several files in one area, or the cause needs some digging; still one PR.
- **L**: more than one PR's worth, or a design to agree first. Goes to `/new-plan #n`.

**Model**. If CLAUDE.md or the phase table maps areas or kinds of work to models, that mapping
wins. Otherwise the kit's rule (docs/claude.md: Opus for design-heavy steps, Sonnet for
well-specified ones), made concrete:
- **Sonnet**: cause known or quickly found, the change follows patterns already in the code, "Done
  when" is clear, no decision left open. Most S and many M issues.
- **Opus**: cause unknown and not obvious (a crash with no reliable steps, lifecycle, threading,
  state lost on rotation or process death), a change across modules or in the cores the plan
  assigns to Opus, persistence or migration of saved data, permissions or security, or a decision
  to take first.
- **Mixed**: when the hard part is finding the cause or the design and the rest is routine, an
  Opus session for that part, ending in a short handoff (cause, approach, files), then a Sonnet
  session to implement it: the projects' **a**/**b** split.

When unsure between the two, say so and say what would decide it; don't present a guess as
certain.

**Sessions**: one session ends in one PR (`/close-phase` or `self-review` before it).
- Group issues that touch the same files or area and would be reviewed together; keep apart those
  one would want to revert separately. A group takes the stronger model of its issues.
- Order: crashes and data loss first, then what blocks other issues, then the rest.
- Planned issues stay with their phase (`/next-phase <phase>` offers them); in-progress ones stay
  with their PR; needs-info and obsolete ones get no session until answered.
- L issues count as one `/new-plan` session (Opus or Sonnet, as `/new-plan` itself judges the
  design), and their phases are counted later by that plan, not here.

## 4. Report

In Italian, short enough for a phone. First a table: issue, title (short), type, state (ready /
needs info / planned / in progress / duplicate / obsolete), size, model, session. Then the
sessions, in order, one block each:

- **Sessione N - <model>**: the issues, what changes and where in one or two lines, the tests to
  add, the device checks if any;
- how to start it: a new cloud session with that model, named `<project> <what>` (docs/claude.md,
  "Names"), and the first message (e.g. "Lavora sulle issue #4 e #9: <one-line summary>",
  `/new-plan #12`, or `/next-phase 10` for a planned one).

Then: the total (N sessions: X Sonnet, Y Opus), the questions to ask for each needs-info issue, the
duplicates and obsolete ones with the evidence (closing them is the author's call), and what you
couldn't establish.

If the author wants to start one of the sessions here, check this session's model first (as
`next-phase` step 1 does) and say if it isn't the one the plan recommends.
