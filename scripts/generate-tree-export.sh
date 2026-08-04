#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT="$ROOT/ATROPOS_TREE_PORT_EXPORT_PATHS.md"
STAMP="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
BRANCH="$(git -C "$ROOT" branch --show-current)"
HEAD="$(git -C "$ROOT" rev-parse HEAD)"
TMP="$(mktemp)"
trap 'rm -f "$TMP"' EXIT

find "$ROOT" -type f \
  -not -path '*/.git/*' \
  -not -path '*/.gradle/*' \
  -not -path '*/build/*' \
  -not -path '*/node_modules/*' \
  -not -path '*/.next/*' \
  -not -path '*/.atropos/*' \
  -not -path '*/.venv/*' \
  -not -path '*/__pycache__/*' \
  -not -name '*.jar' \
  -printf '%P\n' | LC_ALL=C sort > "$TMP"

{
  printf '# ATROPOS Phase Tree Export\n\n'
  printf 'Purpose: context-efficient codebase tree data for external agents.\n'
  printf 'Location: repo root (`ATROPOS_TREE_PORT_EXPORT_PATHS.md`).\n'
  printf 'Update cadence: refresh after a whole canonical phase reaches its acceptance gate, or when the Human Owner requests a refresh.\n'
  printf 'Authority pointer: `AGENTS.md` and `docs/completion/ATROPOS_PHASE_PROGRESS_SNAPSHOT.md`.\n\n'
  printf '## Snapshot\n\n'
  printf '%s\n' "- Generated: $STAMP" "- Repo root: repository-relative paths" "- Git branch: $BRANCH" "- Git HEAD: $HEAD" "- Refresh trigger: explicit Human Owner request during phase-accounting audit" "- Exclusions: .git/, .gradle/, build/, node_modules/, .next/, all .atropos/ runtime state, and **/*.jar" "- File count: $(wc -l < "$TMP")"
  printf '\n## Tree\n\n```text\n'
  cat "$TMP"
  printf '```\n'
} > "$OUT"

printf 'generated=%s\nhead=%s\nfiles=%s\noutput=%s\n' "$STAMP" "$HEAD" "$(wc -l < "$TMP")" "$OUT"
