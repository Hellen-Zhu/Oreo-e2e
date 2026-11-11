#!/usr/bin/env bash
# Lists the step patterns oreo-e2e already has, one section per file, so that a feature reuses a step before
# inventing a synonym.
#   api   the steps of snippet/api (what an @api scenario calls)
#   ui    the steps a @playwright scenario calls: snippet/ui/flows, and the business-worded UI provisioning steps of
#         PlaywrightTradeProvisioningSteps (Java, "... exists in the blotter")
#   glue  the Java steps of glue/api and glue/ui (building blocks for snippets; of them a feature file calls only the
#         UI provisioning steps listed by ui)
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
    title="UI steps (snippet/ui/flows and the UI provisioning steps: what a @playwright scenario calls)"
    ;;
  glue)
    dir="$root/src/main/java/com/example/module/oreo/glue"
    title="Java glue steps (for composing snippets; a feature file calls snippet steps and the UI provisioning steps)"
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
  echo "> Each line is a regular expression: \`([^']*)\` is a parameter, \`(a|b)\` a choice, \`(?:a|b)\` a choice of words."
  if [ "$mode" = api ]; then
    echo ">"
    echo "> **Shared steps first.** The response outcomes of \`common_api.snippet\` are the only outcome wording:"
    echo "> \`is retrieved / created / updated / approved / processed / executed successfully\` (200),"
    echo "> \`is saved / published / re-published / imported successfully\` (201),"
    echo "> \`is rejected with a validation error\` (400) / \`with permission denied\` (403) / \`as not found\` (404) /"
    echo "> \`with a conflict\` (409). Older domain steps below that say \`should be ... successfully\` are not a model."
  fi

  # common_*.snippet first (shared vocabulary), then the rest, each group sorted
  { find "$dir" -name "$pattern" -name 'common_*' | LC_ALL=C sort
    find "$dir" -name "$pattern" ! -name 'common_*' | LC_ALL=C sort; } | while IFS= read -r file; do
    steps="$(sed -n -E "$extract" "$file" | sed -E 's/\$$//')"
    [ -n "$steps" ] || continue
    echo ""
    echo "## ${file#"$dir"/}"
    echo ""
    echo '```text'
    printf '%s\n' "$steps"
    echo '```'
  done

  if [ "$mode" = ui ]; then
    provisioning="$root/src/main/java/com/example/module/oreo/glue/ui/PlaywrightTradeProvisioningSteps.java"
    steps="$(sed -n -E 's/^[[:space:]]*@(Given|When|Then|And)\("\^?(.*)"\).*$/\1 \2/p' "$provisioning" \
      | sed -E 's/\$$//' | grep 'exists in the blotter' || true)"
    if [ -n "$steps" ]; then
      echo ""
      echo "## UI provisioning (Java: glue/ui/PlaywrightTradeProvisioningSteps.java, called by the feature directly)"
      echo ""
      echo '```text'
      printf '%s\n' "$steps"
      echo '```'
    fi
  fi
} > "$out"

echo "[gen-snippet-index] $mode: $out"
