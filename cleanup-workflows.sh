#!/usr/bin/env bash
set -euo pipefail
if [[ ! -d .git || ! -f .github/workflows/android-apk.yml ]]; then
  echo 'Run this from the root of the coloring Git repository.' >&2
  exit 1
fi
for path in .github/workflows/ci.yml .github/workflows/guide.yml .github/workflows/release.yml; do
  if [[ -f "$path" ]]; then
    git rm -- "$path"
  else
    echo "Already absent: $path"
  fi
done
echo 'Review staged deletions with git diff --cached --stat, then commit and push.'
