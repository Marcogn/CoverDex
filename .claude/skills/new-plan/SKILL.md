---
name: new-plan
description: Plans a new piece of work (a feature, a refactor, an issue that is too big for one go) as phases in the project's own plan format, considering the open GitHub issues, and proposes it as a draft PR to review and adjust; writes no code. Use when the author types /new-plan or asks to plan, "pianifica", "pianifichiamo", "facciamo un piano per" something that isn't planned yet.
argument-hint: "[the idea in a few words, or an issue number like #12]"
---

# Plan new work

Generic for the author's Android projects (docs/claude.md). The result is a plan the author reviews
as a **draft pull request**; it becomes official only when they merge it, after which `/next-phase`
runs its phases. **Never write or change code in this skill**, and never start a phase.

Request: `$ARGUMENTS` (empty: ask what to plan).

## 1. Learn the project's way of planning

Read `CLAUDE.md` and what it points to: the spec, the existing plans (`docs/plan-*.md`,
`docs/roadmap.md`, `docs/plan/`…), the phase table and its columns, how phases are named and
numbered, which model goes with which kind of phase (e.g. Opus for the cores where a wrong design is
expensive, with an **a**/**b** split and a handoff), the decisions file, the docs' language. The new
plan follows that format exactly; it must look like the plans already there.

## 2. Understand the request and its context

- An issue number: read the issue and its comments.
- Read the code areas the work touches (the Explore agent for wide searches), the decisions and ADRs
  that apply, and what the spec says. Note constraints (permissions, offline rules, `minSdk`…).
- List **all open issues** (bugs and ideas) with the GitHub tools; mark the ones related to this
  work.

## 3. Ask before writing

One round of questions (AskUserQuestion, up to four per call; a second round only if an answer opens
a new question), each with options and a recommended one. Typical ones:
- scope: what is in, what is explicitly out;
- the decisions that shape the design (a library or another, a format, where data lives), with the
  trade-off of each option in one line;
- which related open issues to include (multi-select);
- priority or order, if it isn't obvious.

Don't fill gaps with plausible guesses: anything not answered is listed as an open question in the
plan.

## 4. Write the plan

On a new branch (the environment's, else `plan-<short-name>`):

- **The plan file**, where the project keeps plans and in its format. For each phase: goal, scope,
  prerequisites, model, "Done when" (including the tests to add and the CI being green), device
  checks, the issues it closes (`Fixes #n` at its close), out of scope. Then the open questions and
  the risks. Phases sized for one session each.
- **The phase table** (CLAUDE.md or the file it lives in): the new rows, marked as planned.
- **The decisions file**: one dated entry per decision taken in the questions, with its reason.
- No CHANGELOG entry: nothing user-visible yet.

Commit, push, open a **draft PR** titled "Piano: …" (or "Plan: …", in the docs' language) with the
summary table in its body; subscribe to it.

## 5. Show it

Reply in Italian, short enough to read on a phone:
- a table: phase, what it delivers, model, issues it closes;
- the decisions taken and the open questions;
- the PR link, and how to ask for changes: here in the chat, or as comments on lines of the PR.

## 6. Adjust until approved

Each request (chat or PR comment) changes the same PR: edit, push, answer the PR thread, and send the
updated table. Keep going until the author merges the PR (approved) or closes it (dropped). On merge,
say which command starts the work: `/next-phase <first phase>`, in a session with that phase's model.
