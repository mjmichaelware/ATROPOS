#!/usr/bin/env bash
# Get the ATROPOS APK onto this phone and open the installer. One command.
#
#   bash scripts/apk.sh            download the latest successful build
#   bash scripts/apk.sh --build    build from current main first, then download
#
# There is nothing to substitute and no filename to copy: the script resolves
# the run, downloads the artifact, verifies it, and hands the exact path to
# Android's installer itself.
#
# Download and verification are delegated to pull-apk.sh, which owns that
# responsibility; this script owns the end-to-end convenience flow.
#
# Requires: gh, authenticated once with `gh auth login`.
set -euo pipefail

SideloadApk() {

cd "$(dirname "$0")/.."
WORKFLOW="android-apk.yml"

command -v gh >/dev/null 2>&1 || { echo "gh is required: pkg install gh" >&2; exit 1; }
gh auth status >/dev/null 2>&1 || { echo "gh is not authenticated: gh auth login" >&2; exit 1; }

if [ "${1:-}" = "--build" ]; then
    echo "==> triggering a build of current main"
    gh workflow run "$WORKFLOW"

    # The run does not appear instantly; poll briefly for it to register.
    echo "==> waiting for the run to register"
    RUN_ID=""
    for _ in $(seq 1 20); do
        sleep 3
        RUN_ID="$(gh run list --workflow "$WORKFLOW" --limit 1 --json databaseId --jq '.[0].databaseId' 2>/dev/null || true)"
        [ -n "$RUN_ID" ] && [ "$RUN_ID" != "null" ] && break
    done
    [ -n "$RUN_ID" ] || { echo "could not find the triggered run" >&2; exit 1; }

    echo "==> building (run $RUN_ID). This takes about two minutes."
    # --exit-status makes a failed build fail this script rather than fall
    # through and silently install a stale APK.
    gh run watch "$RUN_ID" --exit-status
    echo
fi

echo "==> downloading and verifying"
OUTPUT="$(bash scripts/pull-apk.sh)"
echo "$OUTPUT"

# pull-apk.sh prints the destination on a line beginning "OK: ".
APK="$(printf '%s\n' "$OUTPUT" | sed -n 's/^OK: //p' | tail -1)"
[ -n "$APK" ] && [ -f "$APK" ] || {
    echo >&2
    echo "could not determine the downloaded APK path" >&2
    exit 1
}

echo
echo "==> opening the installer"
echo "    $APK"
if command -v termux-open >/dev/null 2>&1; then
    termux-open "$APK"
    echo
    echo "The Android installer should now be on screen. Tap Install."
    echo
    echo "NOTHING APPEARED? Termux has not been allowed to install apps yet."
    echo "That is a one-time system permission. Grant it from here:"
    echo
    echo "  am start -a android.settings.MANAGE_UNKNOWN_APP_SOURCES -d package:com.termux"
    echo
    echo "Turn the switch on, press back, then run this script again."
else
    echo
    echo "termux-open is unavailable: pkg install termux-tools"
    echo "Or open this file from your Files app:"
    echo "  $APK"
fi
}

SideloadApk "$@"
