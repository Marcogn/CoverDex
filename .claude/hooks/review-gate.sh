#!/bin/bash
# Review gate: work that changes code must be reviewed (skill `self-review`) before the session
# ends and before a pull request is opened. Generic: copy it unchanged into any project
# (docs/claude.md).
#
#   review-gate.sh stop   Stop hook. If the branch has commits with code changes that no review
#                         covers, blocks the stop once and tells Claude to run `self-review`.
#   review-gate.sh pr     PreToolUse hook on mcp__github__create_pull_request. Denies the call
#                         while the diff is unreviewed (this one cannot be talked around).
#   review-gate.sh done   Run by `self-review` when the review is finished: records what was
#                         reviewed. Any later change to the diff makes the review stale again.
#
# "Reviewed" = a marker in the git directory holding a fingerprint of the diff against the base
# branch (committed + uncommitted + untracked files). The gate guarantees the review is asked for
# at the right moments; it can't judge its quality (the skill defines that).
# Docs-only diffs (*.md, docs/) need no review. Fails open (exit 0) when git, a base branch or a
# JSON parser is missing: a broken gate must not trap a session.

set -u
MODE="${1:-}"
INPUT=""
[ "$MODE" != "done" ] && INPUT=$(cat)

git rev-parse --git-dir >/dev/null 2>&1 || exit 0
GITDIR=$(git rev-parse --git-dir)
MARKER="$GITDIR/claude-review-ok"

# Base branch: origin's default branch, else origin/main, else origin/master.
BASE=$(git symbolic-ref --quiet --short refs/remotes/origin/HEAD 2>/dev/null)
for c in "$BASE" origin/main origin/master; do
  [ -n "$c" ] && git rev-parse --verify --quiet "$c" >/dev/null && { BASE="$c"; break; }
done
git rev-parse --verify --quiet "$BASE" >/dev/null || exit 0
MB=$(git merge-base "$BASE" HEAD 2>/dev/null) || exit 0

# Files that changed against the base, committed or not, including new untracked ones.
changed() { { git diff --name-only "$MB"; git ls-files --others --exclude-standard; } | sort -u; }
needs_review() { changed | grep -Ev '(\.md$|^docs/|^LICENSE$)' | grep -q .; }
fingerprint() {
  {
    git diff "$MB"
    git ls-files --others --exclude-standard | while read -r f; do echo "$f"; git hash-object -- "$f"; done
  } | sha256sum | cut -d' ' -f1
}
reviewed() { [ -f "$MARKER" ] && [ "$(cat "$MARKER")" = "$(fingerprint)" ]; }
ahead() { [ "$(git rev-list --count "$MB"..HEAD 2>/dev/null)" -gt 0 ]; }

REASON="This branch has code changes that no review covers. Run the self-review skill now (verify, then /code-review and the architecture-reviewer agent on the diff, fix what is real), and finish it with: bash .claude/hooks/review-gate.sh done. Only then continue: commit, open the PR, report."

case "$MODE" in
  done)
    fingerprint > "$MARKER"
    echo "Review recorded for the current diff against $BASE."
    ;;
  stop)
    # Already continuing because of this hook: don't block a second time (the PR gate still holds).
    echo "$INPUT" | grep -Eq '"stop_hook_active"[[:space:]]*:[[:space:]]*true' && exit 0
    # Only finished-looking work: something committed ahead of the base. Uncommitted edits alone
    # are work in progress (a question to the author, a pause).
    ahead || exit 0
    needs_review || exit 0
    reviewed && exit 0
    printf '{"decision":"block","reason":"%s"}\n' "$REASON"
    ;;
  pr)
    needs_review || exit 0
    reviewed && exit 0
    printf '{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"deny","permissionDecisionReason":"%s"}}\n' "$REASON"
    ;;
  *)
    echo "usage: review-gate.sh stop|pr|done" >&2
    exit 0
    ;;
esac
exit 0
