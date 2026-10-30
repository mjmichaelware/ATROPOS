#!/usr/bin/env bash
cd "$(git rev-parse --show-toplevel)" || exit 1

TMP=$(mktemp)
trap 'rm -f "$TMP"' EXIT

git ls-files 'src/main' 'app/src/main' 'apps/specgraph-foundry/src' 'apps/web' 'apps/atropos-web' 'packages' | while IFS= read -r f; do
  [ -f "$f" ] || continue

  case "$f" in
    src/main/*) ;;
    app/src/main/*) ;;
    apps/specgraph-foundry/src/*) ;;
    apps/web/*) ;;
    apps/atropos-web/*) ;;
    packages/*) ;;
    *) continue ;;
  esac

  case "$f" in
    *.kt|*.kts|*.java|*.py|*.ts|*.tsx|*.js|*.jsx|*.css) ;;
    *) continue ;;
  esac

  case "$f" in
    */node_modules/*|*/build/*|*/dist/*|*/.next/*|*generated.ts) continue ;;
  esac

  lines=$(wc -l < "$f" | tr -d ' \t')
  case "$lines" in
    ''|*[!0-9]*) continue ;;
  esac

  area=other
  case "$f" in
    apps/specgraph-foundry/src/specgraph_foundry/http_api/*) area=specgraph-http-api ;;
    apps/specgraph-foundry/src/*) area=specgraph-core ;;
    apps/web/*|apps/atropos-web/*) area=web-frontend ;;
    src/main/*|app/src/main/*|packages/*) area=atropos-engine ;;
  esac

  lang=other
  case "$f" in
    *.kt|*.kts) lang=kotlin ;;
    *.java) lang=java ;;
    *.py) lang=python ;;
    *.ts|*.tsx) lang=typescript ;;
    *.js|*.jsx) lang=javascript ;;
    *.css) lang=css ;;
  esac

  printf '%s\t%s\t%s\t%s\n' "$lines" "$f" "$area" "$lang"
done > "$TMP"

echo "=== PRODUCTION ONLY (engine + SpecGraph src + web) ==="
echo "repo: $(pwd)"
echo "commit: $(git rev-parse --short HEAD)"
echo "date: $(date -u +%Y-%m-%dT%H:%M:%SZ)"
echo "excluded: docs tests scripts build_*.py jars node_modules"
echo

echo "=== By area ==="
awk -F'	' '{ a[$3]+=$1; t+=$1 } END { for (k in a) printf "%8d  %s\n", a[k], k; printf "%8d  TOTAL\n", t+0 }' "$TMP" | sort -nr

echo
echo "=== By language ==="
awk -F'	' '{ a[$4]+=$1; t+=$1 } END { for (k in a) printf "%8d  %s\n", a[k], k; printf "%8d  TOTAL\n", t+0 }' "$TMP" | sort -nr

echo
echo "=== Top 40 files ==="
awk -F'	' '{ printf "%6d  %s\n", $1, $2 }' "$TMP" | sort -nr | head -40

echo
echo "=== ATROPOS engine top 25 ==="
awk -F'	' '$3=="atropos-engine" { printf "%6d  %s\n", $1, $2 }' "$TMP" | sort -nr | head -25
