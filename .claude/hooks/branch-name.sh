#!/bin/bash
# PreToolUse hook: a branch Claude creates must have a name that says what it is for.
# Generic: copy it unchanged into any project (docs/claude.md).
#
# Checks `git checkout -b|-B|--orphan`, `git switch -c|-C|--create`, `git branch <name>`,
# `git worktree add -b` (Bash) and mcp__github__create_branch. Accepted: <type>/<what-and-where>,
# lowercase, hyphens, at least three words in all, e.g. fix/crash-on-rotation,
# feat/export-pdf-share, deps/gradle-minor-bumps. The cloud environment's own `claude/...` and
# `ccr-...` names pass, and so do main, master, develop, gh-pages and release/x.y.z.
#
# Denies only when sure, so it reads only plain commands: one line of words joined by ; && || |.
# Anything with quotes, $, backticks, backslashes, parentheses, redirections, heredocs, comments
# or several lines passes unchecked (commit messages always have quotes, so they never trip it).
# That is the contract: an unusual command that passes is fine; a plain one wrongly denied is a bug.

set -u
INPUT=$(cat)

command -v jq >/dev/null 2>&1 || exit 0
TOOL=$(echo "$INPUT" | jq -r '.tool_name // empty')
CMD=$(echo "$INPUT" | jq -r '.tool_input.command // empty')
MCP=$(echo "$INPUT" | jq -r '.tool_input.branch // empty')

# The name a git command in one segment creates, if any.
branch_in_segment() {
  local -a w
  read -ra w <<< "$1"
  local i=0 j
  while [[ "${w[$i]:-}" =~ ^[A-Za-z_][A-Za-z0-9_]*= ]]; do i=$((i + 1)); done   # FOO=1 git ...
  [ "${w[$i]:-}" = git ] || return 0
  i=$((i + 1))
  while [[ "${w[$i]:-}" == -* ]]; do   # global options: -C <dir>, -c <k=v>, --no-pager ...
    case "${w[$i]}" in -C|-c) i=$((i + 2)) ;; *) i=$((i + 1)) ;; esac
  done
  local sub="${w[$i]:-}"
  i=$((i + 1))
  case "$sub" in
    checkout|switch|worktree)
      for ((j = i; j < ${#w[@]}; j++)); do
        case "$sub:${w[$j]}" in
          *:--) return 0 ;;   # pathspecs follow
          checkout:-b|checkout:-B|checkout:--orphan|switch:-c|switch:-C|switch:--create|switch:--force-create|worktree:-b|worktree:-B)
            echo "${w[$((j + 1))]:-}"; return 0 ;;
          switch:--create=*|switch:--force-create=*)
            echo "${w[$j]#*=}"; return 0 ;;
        esac
      done ;;
    branch)
      [[ -n "${w[$i]:-}" && "${w[$i]}" != -* ]] && echo "${w[$i]}" ;;
  esac
  return 0
}

NAME=""
if [ "$TOOL" = "Bash" ]; then
  case "$CMD" in *[\'\"\`\$\\\(\)\<\>\#]*|*$'\n'*) exit 0 ;; esac
  S=${CMD//&&/;}; S=${S//||/;}; S=${S//|/;}
  case "$S" in *'&'*) exit 0 ;; esac   # background jobs: not a plain command
  IFS=';' read -ra SEGS <<< "$S"
  for seg in "${SEGS[@]}"; do
    NAME=$(branch_in_segment "$seg")
    [ -n "$NAME" ] && break
  done
else
  NAME="$MCP"
fi
[ -z "$NAME" ] && exit 0
case "$NAME" in claude/*|ccr-*|main|master|develop|gh-pages) exit 0 ;; esac
echo "$NAME" | grep -Eq '^release/v?[0-9]+(\.[0-9]+)*$' && exit 0

WORDS=$(echo "$NAME" | tr '/_.-' '\n\n\n\n' | grep -Ec '^[a-z]{3,}$')
if echo "$NAME" | grep -Eq '^[a-z0-9]+([/_.-][a-z0-9]+)*$' && [ "$WORDS" -ge 3 ] \
   && ! echo "$NAME" | grep -Eq '^(tmp|temp|test|wip|new|patch|branch)([/_.-]|$)'; then
  exit 0
fi

MSG="Branch name '$NAME' doesn't say what the branch is for. Use <type>/<what-and-where> in lowercase with hyphens, at least three words, e.g. fix/crash-on-rotation, feat/export-pdf-share, deps/gradle-minor-bumps, docs/ci-release-guide (types: feat, fix, refactor, docs, chore, deps, plan, phase). Rename and retry."
jq -n --arg r "$MSG" '{hookSpecificOutput:{hookEventName:"PreToolUse",permissionDecision:"deny",permissionDecisionReason:$r}}'
exit 0
