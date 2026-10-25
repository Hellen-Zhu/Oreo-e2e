#!/usr/bin/env bash
# Lists the step patterns oreo-e2e already has, one section per file, so that a feature reuses a step before
# inventing a synonym.
#   api   the steps of snippet/api (what an @api scenario calls)
#   ui    the steps of snippet/ui/flows (the only steps a @playwright scenario calls)
#   glue  the Java steps of glue/api and glue/ui (building blocks for snippets, not for feature files)
# Usage: gen-snippet-index.sh <api|ui|glue> [out_file]   (default out_file: target/ai/<mode>-step-index.md)
# Runs from any folder; needs only bash, find, sed and sort (macOS, Linux, Git Bash).

set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../../.." && pwd)"
mode="${1:-}"

case "$mode" in
  api)
    dir="$root/src/main/resources/snippet/api"
    title="API steps (snippet/api)"
    ;;
  ui)
    dir="$root/src/main/resources/snippet/ui/flows"
    title="UI steps (snippet/ui/flows: the only steps a @playwright scenario calls)"
    ;;
  glue)
    dir="$root/src/main/java/com/example/module/oreo/glue"
    title="Java glue steps (for composing snippets; a feature file calls snippet steps)"
    ;;
  *)
    echo "Usage: $0 <api|ui|glue> [out_file]" >&2
    exit 1
    ;;
esac

out="${2:-$root/target/ai/$mode-step-index.md}"

if [ ! -d "$dir" ]; then
  echo "[gen-snippet-index] no folder $dir" >&2
  exit 1
fi
mkdir -p "$(dirname "$out")"

if [ "$mode" = glue ]; then
  pattern='*.java'
  # @When("^text$") -> When text
  extract='s/^[[:space:]]*@(Given|When|Then|And)\("\^?(.*)"\).*$/\1 \2/p'
else
  pattern='*.snippet'
  # @When "^text$" -> When text
  extract='s/^[[:space:]]*@(Given|When|Then|And)[[:space:]]+"\^?(.*)"[[:space:]]*$/\1 \2/p'
fi

{
  echo "# $title"
  echo ""
  echo "> Generated from \`${dir#"$root"/}\` by gen-snippet-index.sh; do not edit."
  echo "> Each line is a regular expression: \`([^']*)\` is a parameter, \`(a|b)\` a choice."

  find "$dir" -name "$pattern" | LC_ALL=C sort | while IFS= read -r file; do
    steps="$(sed -n -E "$extract" "$file" | sed -E 's/\$$//')"
    [ -n "$steps" ] || continue
    echo ""
    echo "## ${file#"$dir"/}"
    echo ""
    echo '```text'
    printf '%s\n' "$steps"
    echo '```'
  done
} > "$out"

echo "[gen-snippet-index] $mode: $out"
