#!/usr/bin/env bash
# Formats a single file after an agent edits it. Reads the Claude Code PostToolUse hook payload
# from stdin and formats only the file that changed, so agent output matches the project style
# without a full-project format pass.
set -euo pipefail

file=$(python3 -c 'import json,sys; print(json.load(sys.stdin).get("tool_input",{}).get("file_path",""))' 2>/dev/null || true)
[ -z "$file" ] && exit 0
[ -f "$file" ] || exit 0

repo_root=$(git -C "$(dirname "$file")" rev-parse --show-toplevel 2>/dev/null || exit 0)

case "$file" in
  *.java|*.sql)
    (cd "$repo_root/backend" && ./mvnw -q -o spotless:apply -DspotlessFiles="$(printf '%s' "$file" | sed 's|[].[^$*\\]|\\&|g')") >/dev/null 2>&1 || true
    ;;
  *.md)
    # Prettier is authoritative only where CI enforces it, which is `frontend/src/**/*.{ts,html,scss}`
    # and nothing else. Markdown under docs/ has never been prettier-clean, so reformatting a file on
    # the way past turns a one-line edit into a two-hundred-line diff — and the agent that made the
    # one-line edit gets blamed for the churn. Format markdown only inside frontend/, where the
    # project style is actually settled; leave docs/ to whoever is writing it.
    case "$file" in
      "$repo_root"/frontend/*)
        (cd "$repo_root/frontend" && npx --no-install prettier --write "$file") >/dev/null 2>&1 || true
        ;;
    esac
    ;;
  *.ts|*.html|*.scss|*.json)
    (cd "$repo_root/frontend" && npx --no-install prettier --write "$file") >/dev/null 2>&1 || true
    ;;
esac
exit 0
