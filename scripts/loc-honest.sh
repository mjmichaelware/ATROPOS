#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

EXCLUDE='(\.git/|/build/|/node_modules/|/__pycache__/|\.gradle/|\.atropos/|^docs/|^lakehouse|\.jar\( |\.tar \)|\.lock\( |\.sha256 \)|\.pyc$|\.next/|/dist/|/coverage/|backup-|\( HOME)'
INCLUDE='\.(kt|kts|java|ts|tsx|js|jsx|py|sh)$'

list_prod() {
  git ls-files -z | while IFS= read -r -d '' f; do
    echo "$f" | grep -Eq "$EXCLUDE" && continue
    echo "$f" | grep -Eq "$INCLUDE" || continue
    echo "$f" | grep -Eq '(^|/)src/test/|(/|^)tests?/|e2e/|fixtures/' && continue
    [ -f "$f" ] || continue
    printf '%s\n' "$f"
  done
}

echo "=== ATROPOS honest production LOC ==="
echo "repo: $(pwd)"
echo "commit: $(git rev-parse --short HEAD)"
echo "date: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo

echo "=== By language / type (production, tests excluded) ==="
list_prod | while read -r f; do
  lines=$(wc -l < "$f" | tr -d ' ')
  case "$f" in
    *.kt)  lang=kotlin ;;
    *.kts) lang=kotlin-script ;;
    *.java) lang=java ;;
    *.ts|*.tsx) lang=typescript ;;
    *.js|*.jsx) lang=javascript ;;
    *.py) lang=python ;;
    *.sh) lang=shell ;;
    *) lang=other ;;
  esac
  printf '%s %s\n' "$lines" "$lang"
done | awk '{a[$2]+=$1; t+=$1} END{for (k in a) printf "%8d  %s\n", a[k], k; printf "%8d  TOTAL\n", t}' | sort -nr

echo
echo "=== Top 50 production files by LOC ==="
list_prod | while read -r f; do
  lines=$(wc -l < "$f" | tr -d ' ')
  printf '%6d  %s\n' "$lines" "$f"
done | sort -nr | head -50

echo
echo "=== Kotlin only (src/main) top 50 ==="
git ls-files 'src/main/**/*.kt' 'src/main/**/*.kts' | while read -r f; do
  [ -f "$f" ] || continue
  lines=$(wc -l < "$f" | tr -d ' ')
  printf '%6d  %s\n' "$lines" "$f"
done | sort -nr | head -50

echo
echo "=== Kotlin totals ==="
git ls-files 'src/main/**/*.kt' 'src/main/**/*.kts' | while read -r f; do
  [ -f "$f" ] || continue
  wc -l < "$f"
done | awk '{s+=$1} END{printf "src/main kotlin: %d lines\n", s+0}'

git ls-files 'src/test/**/*.kt' | while read -r f; do
  [ -f "$f" ] || continue
  wc -l < "$f"
done | awk '{s+=$1} END{printf "src/test  kotlin: %d lines\n", s+0}'
