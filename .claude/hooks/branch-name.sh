#!/bin/bash
# PreToolUse hook: a branch Claude creates must have a name that says what it is for.
# Generic: copy it unchanged into any project (docs/claude.md).
#
# Checks `git checkout -b|-B|--orphan`, `git switch -c|-C|--create`, `git branch <name>`,
# `git worktree add -b` (Bash; only real commands, not quoted text) and mcp__github__create_branch. Accepted: <type>/<what-and-where>, lowercase, hyphens, at least
# three words in all, e.g. fix/crash-on-rotation, feat/export-pdf-share, deps/gradle-minor-bumps.
# Branches named `claude/...` or `ccr-...` are the cloud environment's own and always pass. Fails open.

set -u
INPUT=$(cat)

if command -v jq >/dev/null 2>&1; then
  TOOL=$(echo "$INPUT" | jq -r '.tool_name // empty')
  CMD=$(echo "$INPUT" | jq -r '.tool_input.command // empty')
  MCP=$(echo "$INPUT" | jq -r '.tool_input.branch // empty')
else
  exit 0
fi

# The name a git command in one shell segment creates, if any: `git [-C dir] checkout -b|-B|--orphan
# <name>`, `switch -c|-C|--create|--force-create <name>`, `branch <name>`, `worktree add ... -b|-B <name>`.
branch_in_segment() {
  local -a w
  read -ra w <<< "$1"
  local i=1
  [ "${w[0]:-}" = git ] || return 0
  while [ "$i" -lt "${#w[@]}" ] && [[ "${w[$i]}" == -C || "${w[$i]}" == -c ]]; do i=$((i + 2)); done
  local sub="${w[$i]:-}" j
  i=$((i + 1))
  case "$sub" in
    checkout|switch|worktree)
      for ((j = i; j < ${#w[@]} - 1; j++)); do
        case "$sub:${w[$j]}" in
          checkout:-b|checkout:-B|checkout:--orphan|switch:-c|switch:-C|switch:--create|switch:--force-create|worktree:-b|worktree:-B)
            echo "${w[$((j + 1))]}"; return 0 ;;
        esac
      done ;;
    branch)
      [[ -n "${w[$i]:-}" && "${w[$i]}" != -* ]] && echo "${w[$i]}" ;;
  esac
  return 0
}

NAME=""
if [ "$TOOL" = "Bash" ]; then
  # Quoted text (commit messages, echo) is not a command: drop it, then look at each segment
  # between ; && || | and newlines, so `git branch | head` or `git commit -m "git branch x"` pass.
  STRIPPED=$(printf '%s' "$CMD" | sed -e "s/'[^']*'//g" -e 's/"[^"]*"//g')
  while IFS= read -r seg; do
    seg="${seg#"${seg%%[![:space:]]*}"}"
    NAME=$(branch_in_segment "$seg")
    [ -n "$NAME" ] && break
  done < <(printf '%s\n' "$STRIPPED" | sed -e 's/&&/\n/g' -e 's/||/\n/g' -e 's/[;|&]/\n/g')
else
  NAME="$MCP"
fi
[ -z "$NAME" ] && exit 0
case "$NAME" in claude/*|ccr-*) exit 0 ;; esac

WORDS=$(echo "$NAME" | tr '/_.-' '\n\n\n\n' | grep -Ec '^[a-z]{3,}$')
if echo "$NAME" | grep -Eq '^[a-z0-9]+([/_.-][a-z0-9]+)*$' && [ "$WORDS" -ge 3 ] \
   && ! echo "$NAME" | grep -Eq '^(tmp|temp|test|wip|new|patch|branch)([/_.-]|$)'; then
  exit 0
fi

MSG="Branch name '$NAME' doesn't say what the branch is for. Use <type>/<what-and-where> in lowercase with hyphens, at least three words, e.g. fix/crash-on-rotation, feat/export-pdf-share, deps/gradle-minor-bumps, docs/ci-release-guide (types: feat, fix, refactor, docs, chore, deps, plan, phase). Rename and retry."
jq -n --arg r "$MSG" '{hookSpecificOutput:{hookEventName:"PreToolUse",permissionDecision:"deny",permissionDecisionReason:$r}}'
exit 0
