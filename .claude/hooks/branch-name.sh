#!/bin/bash
# PreToolUse hook: a branch Claude creates must have a name that says what it is for.
# Generic: copy it unchanged into any project (docs/claude.md).
#
# Checks `git checkout -b|-B`, `git switch -c|-C`, `git branch <name>` (Bash) and
# mcp__github__create_branch. Accepted: <type>/<what-and-where>, lowercase, hyphens, at least
# three words in all, e.g. fix/crash-on-rotation, feat/export-pdf-share, deps/gradle-minor-bumps.
# Branches named `claude/...` are the cloud environment's own and always pass. Fails open.

set -u
INPUT=$(cat)

if command -v jq >/dev/null 2>&1; then
  TOOL=$(echo "$INPUT" | jq -r '.tool_name // empty')
  CMD=$(echo "$INPUT" | jq -r '.tool_input.command // empty')
  MCP=$(echo "$INPUT" | jq -r '.tool_input.branch // empty')
else
  exit 0
fi

NAME=""
if [ "$TOOL" = "Bash" ]; then
  # Take the name from the first create-branch command found in the line.
  NAME=$(echo "$CMD" | grep -Eo 'git[[:space:]]+(checkout[[:space:]]+-[bB]|switch[[:space:]]+-[cC]|branch)[[:space:]]+[^-[:space:]][^[:space:]]*' | head -1 | awk '{print $NF}')
  case "$CMD" in *"git checkout -b "*|*"git checkout -B "*|*"git switch -c "*|*"git switch -C "*)
    NAME=$(echo "$CMD" | grep -Eo 'git[[:space:]]+(checkout[[:space:]]+-[bB]|switch[[:space:]]+-[cC])[[:space:]]+[^[:space:]]+' | head -1 | awk '{print $NF}') ;;
  esac
else
  NAME="$MCP"
fi
[ -z "$NAME" ] && exit 0
case "$NAME" in claude/*) exit 0 ;; esac

WORDS=$(echo "$NAME" | tr '/_.-' '\n\n\n\n' | grep -Ec '^[a-z]{3,}$')
if echo "$NAME" | grep -Eq '^[a-z0-9]+([/_.-][a-z0-9]+)*$' && [ "$WORDS" -ge 3 ] \
   && ! echo "$NAME" | grep -Eq '^(tmp|temp|test|wip|new|patch|branch)([/_.-]|$)'; then
  exit 0
fi

MSG="Branch name '$NAME' doesn't say what the branch is for. Use <type>/<what-and-where> in lowercase with hyphens, at least three words, e.g. fix/crash-on-rotation, feat/export-pdf-share, deps/gradle-minor-bumps, docs/ci-release-guide (types: feat, fix, refactor, docs, chore, deps, plan, phase). Rename and retry."
printf '{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"deny","permissionDecisionReason":"%s"}}\n' "$MSG"
exit 0
