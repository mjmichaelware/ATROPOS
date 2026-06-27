#!/usr/bin/env bash
set -euo pipefail

NAME="${1:-GENERAL}"
STAMP="$(date +%Y%m%d_%H%M%S)"
DL="$HOME/storage/downloads"
mkdir -p "$DL"

OUT="$DL/ATROPOS_CONTEXT_${NAME}_${STAMP}.txt"
TGZ="$DL/ATROPOS_CONTEXT_${NAME}_${STAMP}.tar.gz"

{
  echo "=== ATROPOS CONTEXT EXPORT ==="
  date
  pwd

  echo
  echo "=== GIT STATUS ==="
  git status --short || true

  echo
  echo "=== FILE TREE ==="
  find src/main/kotlin/atropos -maxdepth 7 -type f -name '*.kt' | sort || true

  echo
  echo "=== COMPILE CHECK ==="
  TMP="$(mktemp -d)"
  mapfile -d '' SOURCES < <(find src/main/kotlin -type f -name '*.kt' -print0)
  kotlinc -include-runtime -d "$TMP/check.jar" "${SOURCES[@]}" && echo "COMPILE_OK" || echo "COMPILE_FAIL"
  rm -rf "$TMP"

  echo
  echo "=== SYMBOL SEARCH ==="
  rg -n "ProviderDescriptor|RoutePolicy|QuotaLedger|FreeModeGuard|StatusEndpointRenderer|StatusProviderDescriptorRenderer|ViewportLayout|LandingRenderer|HeaderRenderer|StatusBarRenderer|CommandRouter|ProviderDecisionEngine|ProviderCascadeRouter|renderNotice|renderError|renderAssistant|/status|/providers|/route" src/main/kotlin/atropos || true

  echo
  echo "=== COMMAND ROUTER ==="
  sed -n '1,420p' src/main/kotlin/atropos/cli/CommandRouter.kt 2>/dev/null || true

  echo
  echo "=== UI FILES ==="
  for f in $(find src/main/kotlin/atropos/cli/ui -type f -name '*.kt' | sort); do
    echo
    echo "===== $f ====="
    sed -n '1,420p' "$f"
  done

  echo
  echo "=== PROVIDER CORE FILES ==="
  for f in $(find src/main/kotlin/atropos/core -type f -name '*.kt' | sort | rg 'Provider|Route|Quota|endpoint|provider|Config'); do
    echo
    echo "===== $f ====="
    sed -n '1,420p' "$f"
  done
} > "$OUT" 2>&1

tar -czf "$TGZ" src/main/kotlin/atropos scripts 2>/dev/null || true

termux-media-scan "$OUT" >/dev/null 2>&1 || true
termux-media-scan "$TGZ" >/dev/null 2>&1 || true

echo "TEXT_CONTEXT=$OUT"
echo "SOURCE_BUNDLE=$TGZ"
