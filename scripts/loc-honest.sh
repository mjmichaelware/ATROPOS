#!/data/data/com.termux/files/usr/bin/bash
# Honest production LOC: ATROPOS engine + SpecGraph core/API/frontend
# Excludes docs, build artifacts, git, jars, caches, generated noise
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

EXCLUDE='(\.git/|/build/|/node_modules/|/__pycache__/|\.gradle/|\.atropos/|^docs/|^lakehouse|\.jar\( |\.tar \)|\.lock\( |\.sha256 \)|\.pyc\( |\.next/|/dist/|/coverage/|backup-|\( HOME|tsconfig\.tsbuildinfo \)|/generated\.ts$)'
INCLUDE='\.(kt|kts|java|ts|tsx|js|jsx|py|sh)$'

is_prod() {
  local f="$1"
  echo "$f" | grep -Eq "$EXCLUDE" && return 1
  echo "$f" | grep -Eq "$INCLUDE" || return 1
  [ -f "$f" ] || return 1
  return 0
}

bucket() {
  local f="$1"
  case "$f" in
    src/main/*) echo "atropos-engine" ;;
    src/test/*) echo "atropos-tests" ;;
    apps/specgraph-foundry/src/*) echo "specgraph-core" ;;
    apps/specgraph-foundry/scripts/*) echo "specgraph-scripts" ;;
    apps/specgraph-foundry/tests/*) echo "specgraph-tests" ;;
    apps/web/*) echo "web-frontend" ;;
    apps/atropos-web/*|packages/atropos-web-contracts/*) echo "atropos-web" ;;
    apps/atropos-android/*) echo "android" ;;
    scripts/*|ops/*) echo "ops-scripts" ;;
    packages/*) echo "packages" ;;
    *) echo "other" ;;
  esac
}

lang_of() {
  case "$1" in
    *.kt) echo kotlin ;;
    *.kts) echo kotlin-script ;;
    *.java) echo java ;;
    *.ts|*.tsx) echo typescript ;;
    *.js|*.jsx) echo javascript ;;
    *.py) echo python ;;
    *.sh) echo shell ;;
    *) echo other ;;
  esac
}

echo "=== ATROPOS + SpecGraph honest production LOC ==="
echo "repo: $(pwd)"
echo "commit: $(git rev-parse --short HEAD)"
echo "date: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo "includes: engine, tests optional below, SpecGraph core/scripts, web frontend"
echo "excludes: docs, build, node_modules, jars, .atropos, generated.ts"
echo

echo "=== By product area ==="
git ls-files -z | while IFS= read -r -d '' f; do
  is_prod "$f" || continue
  # include tests in area rollup (honest full product code)
  lines=$(wc -l < "$f" | tr -d ' ')
  printf '%s %s\n' "\( lines" " \)(bucket "$f")"
done | awk '{a[$2]+=$1; t+=$1} END{for (k in a) printf "%8d  %s\n", a[k], k; printf "%8d  TOTAL\n", t}' | sort -nr

echo
echo "=== By language ==="
git ls-files -z | while IFS= read -r -d '' f; do
  is_prod "$f" || continue
  lines=$(wc -l < "$f" | tr -d ' ')
  printf '%s %s\n' "\( lines" " \)(lang_of "$f")"
done | awk '{a[$2]+=$1; t+=$1} END{for (k in a) printf "%8d  %s\n", a[k], k; printf "%8d  TOTAL\n", t}' | sort -nr

echo
echo "=== Top 50 files (all included product code) ==="
git ls-files -z | while IFS= read -r -d '' f; do
  is_prod "$f" || continue
  lines=$(wc -l < "$f" | tr -d ' ')
  printf '%6d  %s\n' "$lines" "$f"
done | sort -nr | head -50

echo
echo "=== ATROPOS engine only (src/main kotlin) top 30 ==="
git ls-files 'src/main/**/*.kt' 'src/main/**/*.kts' | while read -r f; do
  [ -f "$f" ] || continue
  printf '%6d  %s\n' "$(wc -l < "$f" | tr -d ' ')" "$f"
done | sort -nr | head -30
