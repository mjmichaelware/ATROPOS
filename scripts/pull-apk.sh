#!/usr/bin/env bash
# Download the most recent signed APK produced by the Android workflow.
#
# The earlier version of this script failed with "no artifact matches any of
# the names or patterns provided" because it searched for a fixed filename
# while the workflow published under a different one. It now resolves the run
# and the artifact by name, and verifies what it downloaded before reporting
# success, so a broken artifact is caught here rather than by the installer.
#
# Requires the GitHub CLI, authenticated:  gh auth login
set -euo pipefail

WORKFLOW="android-apk.yml"
ARTIFACT="atropos-android-signed"
BRANCH="${1:-main}"
OUT_DIR="${2:-$HOME/storage/downloads}"

command -v gh >/dev/null 2>&1 || { echo "gh (GitHub CLI) is required: pkg install gh" >&2; exit 1; }

echo "resolving latest successful '$WORKFLOW' run on '$BRANCH'..."
RUN_ID="$(gh run list \
    --workflow "$WORKFLOW" \
    --branch "$BRANCH" \
    --status success \
    --limit 1 \
    --json databaseId \
    --jq '.[0].databaseId')"

if [ -z "${RUN_ID:-}" ] || [ "$RUN_ID" = "null" ]; then
    echo "no successful run found. Most recent runs:" >&2
    gh run list --workflow "$WORKFLOW" --branch "$BRANCH" --limit 5 >&2
    exit 1
fi
echo "run id: $RUN_ID"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "downloading artifact '$ARTIFACT'..."
gh run download "$RUN_ID" --name "$ARTIFACT" --dir "$TMP"

APK="$(find "$TMP" -name '*.apk' | head -1)"
[ -n "$APK" ] || { echo "artifact contained no .apk" >&2; ls -R "$TMP" >&2; exit 1; }

# Verify before handing it over. An APK missing compiled code or a launcher
# activity installs nowhere, and the installer's only feedback is the
# unhelpful "app wasn't installed".
echo "verifying..."
if ! unzip -l "$APK" | grep -q "classes.dex"; then
    echo "REJECTED: no classes.dex — this APK contains no compiled code" >&2
    exit 1
fi
if ! unzip -l "$APK" | grep -q "resources.arsc"; then
    echo "REJECTED: no resources.arsc — the resource table is missing" >&2
    exit 1
fi

# Version-stamped filename so a stale copy in the downloads folder can never be
# mistaken for the new build. That masked two earlier fixes.
STAMP="$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT_DIR"
DEST="$OUT_DIR/ATROPOS-$STAMP.apk"
cp "$APK" "$DEST"

echo
echo "OK: $DEST"
echo "size: $(du -h "$DEST" | cut -f1)"
echo
echo "install with:"
echo "  adb install -r \"$DEST\""
echo "or open it from the Downloads app."
