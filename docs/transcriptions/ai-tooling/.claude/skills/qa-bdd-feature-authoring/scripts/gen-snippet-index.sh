#!/usr/bin/env bash
# Generates snippet indexes under .claude/skills/qa-bdd-feature-authoring/references
# Usage: gen-snippet-index.sh <api|ui>
# [Transcription note, not in the original: transcribed from photos, lines 1-74 as shown. Archival source only.]

set -euo pipefail

SKILL_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
mkdir -p "${SKILL_DIR}/references"

mode="${1:-}"

case "$mode" in
  api)
    index_title="API Snippet Index"
    out_file="${SKILL_DIR}/references/api-snippet-index.md"
    snippet_dir="${E2E_DIR}/src/main/resources/snippet/api"
    recursive=false
    ;;
  ui)
    index_title="UI Snippet Index"
    out_file="${SKILL_DIR}/references/ui-snippet-index.md"
    snippet_dir="${E2E_DIR}/src/main/resources/snippet/ui/flows"
    recursive=true
    ;;
  *)
    echo "Usage: $0 <api|ui>" >&2
    exit 1
    ;;
esac

if [ ! -d "$snippet_dir" ]; then
  echo "[gen-snippet-index] SKIP: $snippet_dir not found" >&2
  exit 0
fi

if [ "$recursive" = true ]; then
  shopt -s nullglob globstar
  snippet_files=("$snippet_dir"/**/*.snippet)
else
  shopt -s nullglob
  snippet_files=("$snippet_dir"/*.snippet)
fi

{
  echo "# ${index_title}"
  echo ""
  printf '> Auto-generated from `%s`. Do not edit manually.\n' "$snippet_dir"
  echo ""

  for file in "${snippet_files[@]}"; do
    [ -f "$file" ] || continue

    if [ "$mode" = "ui" ]; then
      rel="${file#"$snippet_dir"/}"
      name="${rel%.snippet}"
    else
      name="$(basename "$file" .snippet)"
    fi

    steps=$(grep -n '@Given\|@When\|@Then' "$file")
    [ -z "$steps" ] && continue

    echo "## $name"
    echo ""
    echo '```'
    while IFS= read -r line; do
      echo "$line"
    done <<< "$steps"
    echo '```'
    echo ""
  done
} > "$out_file"

echo "[gen-snippet-index] ($mode) written: $out_file"
