# Working with Claude Code (and how to reuse the setup)

How this repository is set up for Claude Code, why each piece exists, and how the same setup (the
"kit") reaches all the author's Android projects. The kit's source is
[claude-skill-android-kit](https://github.com/Marcogn/claude-skill-android-kit); this page is part
of the kit and is copied unchanged into every project that uses it.
Facts about Claude Code come from its documentation (links at the end), checked on 2026-10-08;
the product changes quickly, so re-check a link before relying on a detail.

## The model: five places Claude gets its behaviour from

| Layer | File(s) | Loaded | Use it for | Why not elsewhere |
|---|---|---|---|---|
| **Memory** | `CLAUDE.md` | Always, every turn | Facts and rules that hold for every task: layout, commands, invariants, status | It costs context on every turn: keep it lean, move procedures out |
| **Skills** | `.claude/skills/<name>/SKILL.md` | Only the description, until used; the body when invoked (`/name`) or when the description matches the request | Procedures: "start a phase", "verify", "close a phase", "fix CI" | Written once, followed the same way every time; no context cost when unused |
| **Subagents** | `.claude/agents/<name>.md` | When delegated to, in a **separate context** | Work that benefits from fresh eyes or that would flood the main context (reviews, wide searches) | A reviewer that didn't write the code doesn't share its assumptions |
| **Hooks** | `.claude/settings.json` → `hooks` | Run by the harness at events (session start, before/after a tool…) | Things that must **always** happen, deterministically | Instructions can be forgotten; a hook can't |
| **Settings** | `.claude/settings.json` → `permissions` | Session start | Commands Claude may run without asking; files it must not read | Fewer prompts locally, explicit limits |

Rule of thumb: *knowledge* → CLAUDE.md, *procedure* → skill, *second opinion* → subagent,
*guarantee* → hook.

Two more files feed reviews: `REVIEW.md` (read by Claude Code Review on GitHub and by the
`architecture-reviewer` agent here) and `.github/pull_request_template.md`.

## What the kit puts in a project

| File | What it does | Generic? |
|---|---|---|
| `.claude/hooks/android-sdk.sh` | Installs the Android SDK (cmdline-tools, licences, platform-tools, the `compileSdk` platform), writes `local.properties`, caps Gradle workers, sets `LC_ALL`. Cloud only; idempotent (≈0.5 s when done) | Yes |
| `.claude/settings.json` | Runs that script at `SessionStart`; allows `./gradlew` and read-only git without prompts; denies reading keystores | Yes |
| `.claude/skills/verify` | Lint + unit tests + debug build, what counts as transient, how to report. Claude Code (v2.1.286+) also runs a project skill named `verify` on its own before each commit that changes code | Yes |
| `.claude/skills/new-plan` | Plans new work as phases in the project's format, after asking the decisive questions and considering the open issues; proposes it as a draft PR to adjust and merge. Writes no code | Yes |
| `.claude/skills/next-phase` | The CLAUDE.md session protocol as steps: model check, reading order, prerequisites, open bugs, scope | Yes, for projects with a phase plan |
| `.claude/skills/close-phase` | "Done when", checks, two independent reviews, docs, commit, draft PR, device checks | Yes |
| `.claude/skills/steward` | How to read this CI's failures and handle review findings. Cloud sessions that watch a PR read it before acting on CI or review events | Yes |
| `.claude/agents/architecture-reviewer.md` | Read-only reviewer of a diff against REVIEW.md, CLAUDE.md rules and ADRs. Always runs on Opus, whatever the session's model: a stricter second look at the end of a phase, for a little more usage | Yes (rules come from the repo) |
| `REVIEW.md` | What a review must always check here, severity, what to skip | **No**: per project |
| `.github/workflows/*.yml` | Short callers of the kit's reusable workflows: CI, Build APK, Release, cleanup, `@claude`, PR review (`docs/ci.md`) | Yes (`with:` values per project) |
| `.github/dependabot.yml` | Weekly grouped dependency PRs (Gradle) and monthly (Actions) | Yes |
| `.github/pull_request_template.md`, `ISSUE_TEMPLATE/` | Same structure for every PR; issues written so they can be handed to Claude as they are | Yes |
| `docs/claude.md`, `docs/ci.md` | This page and the CI page | Yes |
| `.claude/kit-version` | The kit version the project was last aligned with | Written by the skill |

## How the kit reaches every project

Three channels, each used for what it is good at:

| What | Where it lives | How a project gets it | How an update arrives |
|---|---|---|---|
| CI, Build APK, Release, Claude workflows | the kit's `.github/workflows/` (reusable) | short callers in the project | automatically at the next run (callers pin `@v1`) |
| `.claude/`, templates, Dependabot, these docs | the kit's `template/` | **copied** by the `android-kit` skill | run `/android-kit` again in the project |
| The `android-kit` skill | the author's **claude.ai account** (source: the kit's `skill/`) | uploaded once on claude.ai | re-upload only if the launcher itself changes |

The skill on claude.ai is only a launcher: it clones the kit and follows its `INSTALL.md`, where
all the logic is. So a change to the procedure is a commit to the kit, with nothing to re-upload.

Per repository: start a cloud session on it (Sonnet is enough) and type `/android-kit`. It copies
the generic files, replaces the workflows with callers (keeping this project's values and secret
names), writes `REVIEW.md` from the project's own rules (checked against its code), runs the checks
and opens a draft PR. It records the kit version in `.claude/kit-version`.

To improve the kit, change it in the kit repository (a PR there), then run `/android-kit` in each
project; workflow changes need no step in the projects.

Why copying and not a plugin: Claude Code can package skills, agents and hooks as a **plugin** in a
marketplace repository, which is the cleanest way to share them, but **cloud sessions don't load
plugins** a repository enables, nor anything in `~/.claude` on your machine. They see only what is
committed in the repository (CLAUDE.md, `.claude/skills`, `.claude/agents`, `.claude/settings.json`)
plus skills enabled on the claude.ai account. Hence the copies of `.claude/` in each project, and the
account for the one cross-project procedure. Claude Code's "run `verify` before
each commit" also works only for a project skill, another reason `verify` lives in the repository.

The **cloud environment** is shared too: use one environment for all Android projects and paste
`.claude/hooks/android-sdk.sh` into its *Setup script* (claude.ai/code → environment menu → Edit).
It is then cached with the SDK installed (the cache needs the script to finish in about five
minutes; this one takes ~15 s), and the SessionStart hook in each repository only writes
`local.properties`.

## One-time setup (by the author)

1. **Cloud environment**: setup script as above; network access "Trusted" (Google's Maven and
   `dl.google.com` must be reachable).
2. **Claude GitHub App** on the repository (github.com/apps/claude). Needed for the Action, for
   Code Review and for auto-fix of PRs from cloud sessions.
3. Optional, **secret** `CLAUDE_CODE_OAUTH_TOKEN`, needed only by `claude.yml` (`@claude`) and
   `claude-review.yml`; until it exists both skip themselves, and cloud sessions don't need it.
   - Generate the token **once**, on a computer with the Claude Code CLI: `claude setup-token` opens
     the browser login (if the browser can't return to the terminal, it shows a code to paste) and
     prints a token valid for one year, tied to the Claude subscription. It isn't saved anywhere:
     treat it as a password, and don't generate it inside a cloud session (it would stay in the
     transcript).
   - Add it to **each repository** (a personal account has no secrets shared across repositories;
     only organisations do): Settings → Secrets and variables → Actions → New repository secret,
     or `gh secret set CLAUDE_CODE_OAUTH_TOKEN --repo Marcogn/<repo>`. Same token everywhere.
   - After a year: generate a new one and replace the secrets.
4. Optional, **Claude Code Review** (managed, multi-agent): Team/Enterprise plans only, billed per
   review (≈15–25 $ each per the docs). On other plans `claude-review.yml` does the job with the
   subscription.

## Issues: bugs and ideas, not the plan

The plan stays in the docs (the plan files and the phase table CLAUDE.md points to): Claude reads them
every session and they are versioned with the code. Repeating it as issues or epics (parent issues
with sub-issues) would give two sources that drift apart, so issues hold only what has no place in
the plan:

- **Bugs** (template "Bug", label `bug`), typically found during device checks. `/new-plan` asks
  which open issues a new plan includes and assigns them to phases; `/next-phase` lists the open bugs
  and asks which to include; `/close-phase` writes `Fixes #n` in the PR, so merging closes them.
- **Ideas and small tasks** outside the phases (template "Task"): "work on issue #n" in a session.
- Things found during a phase and left out of scope: Claude proposes them as issues instead of
  notes in CLAUDE.md.

Epics and a Projects board are worth adding only if the work stops being one person's, or a
progress view is missed.

## A typical cycle

**New work that isn't planned yet**
1. A cloud session on the repo, `/new-plan <the idea>` (or an issue number). Claude reads the
   project, asks the questions that decide the design and which open issues to include, then opens
   a **draft PR "Piano: …"** with the plan in the project's format, the new rows of the phase table
   and the decisions taken; in the chat, a short table of the phases.
2. Adjust it in the chat or with comments on the PR; each change updates the same PR. No code is
   written while planning.
3. Merge the PR: the plan is official.

**Planned work**
1. A cloud session **with the phase's model**, `/next-phase <N>`. The model check runs first; open
   bugs (and those the plan assigned to the phase) are offered before any work.
2. Claude works with `verify` as it goes, then `/close-phase`: reviews, docs, draft PR.
3. The session watches the PR: CI failures and review comments wake it (`steward` rules).
4. Mark the PR ready → `claude-review.yml` reviews it (if the token is set). Device checks on the
   phone, merge.

Any time: `@claude` on an issue or PR for small things (needs the token), or open an issue for a bug.

## Habits that pay off

- **Say what "done" is.** Tests to add, device checks, what is out of scope. The issue template
  asks for exactly that.
- **Keep CLAUDE.md short and current.** It is read every turn; long files dilute the rules. Move
  procedures into skills, rationale into `docs/decisions.md` and ADRs.
- **Turn repeated instructions into skills.** If you type the same advice twice, it's a skill.
- **Turn rules Claude broke into checks.** A CI step, a lint rule, a test or a hook beats a
  sentence in CLAUDE.md.
- **Ask for a review in a fresh context** (`/code-review`, the reviewer agent) before merging
  anything non-trivial.
- **Match the model to the work** (the table in CLAUDE.md): Opus for design-heavy steps, Sonnet for
  well-specified ones.

## Coverage

`./gradlew testDebugUnitTest createDebugUnitTestCoverageReport -Pcoverage` writes a JaCoCo report to
`app/build/reports/coverage/test/debug/`; Android CI uploads it as `coverage-report`. It shows where
JVM tests are thin, which is where Claude's changes are least protected. Use it to choose what to
test, not as a percentage to chase.

## What was considered and left out

- **detekt**: the only line that supports Kotlin 2.4 and AGP 9 built-in Kotlin is 2.0.0, still
  alpha, and the project takes stable versions only (`docs/decisions.md`). Revisit at 2.0.0.
- **Hooks that run Gradle after edits or at the end of a turn**: a build takes minutes and would slow
  every turn; `verify` before commits gives the same safety.
- **Scheduled routines** (e.g. a weekly health check): useful only with steady activity; they
  spend the subscription even when nothing changed. Easy to add later from a session.

## Sources

- Claude Code docs: [memory](https://code.claude.com/docs/en/memory),
  [skills](https://code.claude.com/docs/en/skills), [subagents](https://code.claude.com/docs/en/sub-agents),
  [hooks](https://code.claude.com/docs/en/hooks), [settings](https://code.claude.com/docs/en/settings),
  [cloud environments](https://code.claude.com/docs/en/cloud-environments) (what carries over,
  setup scripts, caching), [cloud sessions and auto-fix](https://code.claude.com/docs/en/claude-code-on-the-web),
  [GitHub Actions](https://code.claude.com/docs/en/github-actions),
  [Code Review](https://code.claude.com/docs/en/code-review),
  [plugin marketplaces](https://code.claude.com/docs/en/plugin-marketplaces).
- [claude-code-action security](https://github.com/anthropics/claude-code-action/blob/main/docs/security.md).
- GitHub: [Dependabot supported ecosystems](https://docs.github.com/en/code-security/dependabot/ecosystems-supported-by-dependabot/supported-ecosystems-and-repositories).
- detekt: [2.0.0 changelog](https://detekt.dev/changelog-2.0.0/).
