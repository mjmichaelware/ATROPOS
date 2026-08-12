#!/usr/bin/env bash
# Download the most recent signed APK produced by the Android workflow.
#
# Failure here used to be opaque: "no artifact matches any of the names or
# patterns provided", with no indication of what the run *did* publish or
# whether the run was even the one you expected. The most common cause is that
# the newest *successful* run predates a workflow change, so it published under
# a different artifact name — the script then downloads nothing while looking
# like it found the right build. Every failure path below reports what it saw.
#
# Requires the GitHub CLI, authenticated:  gh auth login
set -euo pipefail

WORKFLOW="android-apk.yml"
ARTIFACT="atropos-android-signed"
BRANCH="${1:-main}"
OUT_DIR="${2:-$HOME/storage/downloads}"

command -v gh >/dev/null 2>&1 || { echo "gh (GitHub CLI) is required: pkg install gh" >&2; exit 1; }

REPO="$(gh repo view --json nameWithOwner --jq .nameWithOwner)"
echo "repo: $REPO   branch: $BRANCH"
echo

# Show the most recent runs regardless of status first. If the newest run
# failed, the "latest successful" run below is a stale build and the APK you
# get will not contain your latest commit.
echo "recent runs:"
gh run list --workflow "$WORKFLOW" --branch "$BRANCH" --limit 5 \
    --json databaseId,status,conclusion,headSha,createdAt \
    --jq '.[] | "  \(.databaseId)  \(.status)/\(.conclusion // "-")  \(.headSha[0:8])  \(.createdAt)"'
echo

NEWEST_ID="$(gh run list --workflow "$WORKFLOW" --branch "$BRANCH" --limit 1 --json databaseId --jq '.[0].databaseId')"
NEWEST_CONCLUSION="$(gh run list --workflow "$WORKFLOW" --branch "$BRANCH" --limit 1 --json conclusion --jq '.[0].conclusion // "in_progress"')"

RUN_ID="$(gh run list --workflow "$WORKFLOW" --branch "$BRANCH" --status success --limit 1 \
    --json databaseId --jq '.[0].databaseId')"

if [ -z "${RUN_ID:-}" ] || [ "$RUN_ID" = "null" ]; then
    echo "No successful run exists for $WORKFLOW on $BRANCH." >&2
    echo "The newest run ($NEWEST_ID) concluded: $NEWEST_CONCLUSION" >&2
    echo >&2
    echo "See why it failed:" >&2
    echo "  gh run view $NEWEST_ID --log-failed" >&2
    exit 1
fi

if [ "$RUN_ID" != "$NEWEST_ID" ]; then
    echo "WARNING: the newest run ($NEWEST_ID) concluded '$NEWEST_CONCLUSION'."
    echo "         Falling back to the last successful run ($RUN_ID), which is OLDER"
    echo "         and will NOT contain your most recent commits."
    echo "         Inspect the failure with: gh run view $NEWEST_ID --log-failed"
    echo
fi

echo "using run: $RUN_ID"

# Enumerate what the run actually published before asking for a specific name.
mapfile -t NAMES < <(gh api "repos/$REPO/actions/runs/$RUN_ID/artifacts" --jq '.artifacts[].name' 2>/dev/null || true)

if [ "${#NAMES[@]}" -eq 0 ]; then
    echo "Run $RUN_ID published no artifacts at all." >&2
    echo "That usually means the build succeeded but the upload step did not run," >&2
    echo "or the artifacts have expired (default retention is 90 days)." >&2
    echo "  gh run view $RUN_ID" >&2
    exit 1
fi

echo "artifacts published by this run:"
printf '  %s\n' "${NAMES[@]}"
echo

# Prefer the expected name; otherwise take the single artifact if unambiguous.
TARGET=""
for n in "${NAMES[@]}"; do
    [ "$n" = "$ARTIFACT" ] && TARGET="$n" && break
done
if [ -z "$TARGET" ]; then
    if [ "${#NAMES[@]}" -eq 1 ]; then
        TARGET="${NAMES[0]}"
        echo "NOTE: '$ARTIFACT' not present; using the only artifact: '$TARGET'"
        echo "      (this run predates the current workflow's artifact name)"
    else
        echo "Expected artifact '$ARTIFACT' not found and several exist." >&2
        echo "Re-run with the workflow updated, or download one by hand:" >&2
        echo "  gh run download $RUN_ID --name <name>" >&2
        exit 1
    fi
fi

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "downloading '$TARGET'..."
gh run download "$RUN_ID" --name "$TARGET" --dir "$TMP"

APK="$(find "$TMP" -name '*.apk' | head -1)"
[ -n "$APK" ] || { echo "artifact '$TARGET' contained no .apk:" >&2; find "$TMP" -type f >&2; exit 1; }

# Verify before handing it over. An APK missing compiled code or a resource
# table installs nowhere, and the installer's only feedback is the unhelpful
# "app wasn't installed".
#
# The listing is written to a file and then searched. It is never piped into
# grep, and that is the whole point.
#
# This script previously ran `unzip -l "$APK" | grep -q classes.dex` under
# `set -o pipefail`. grep -q exits the moment it matches; classes.dex is the
# fourth entry of 426, so grep closed the pipe while unzip was still writing.
# unzip died of SIGPIPE, the pipeline returned 141, pipefail surfaced that as
# failure, and a perfectly good APK — 5.5 MB of classes.dex — was reported as
# an "empty shell". CI never saw it because CI redirected to a file first.
#
# A second reader is kept as a tiebreaker: python's zipfile parses the central
# directory directly, so it stays correct even on a v2/v3-signed APK where the
# signing block sits between the entry data and the directory.
echo "verifying..."
LISTING="$TMP/listing.txt"
: > "$LISTING"

unzip -l "$APK" >> "$LISTING" 2>/dev/null || true
UNZIP_ENTRIES=$(grep -c "" "$LISTING" 2>/dev/null || echo 0)

PY_LISTING="$TMP/listing-py.txt"
: > "$PY_LISTING"
if command -v python3 >/dev/null 2>&1; then
    python3 - "$APK" > "$PY_LISTING" 2>/dev/null <<'PY' || true
import sys, zipfile
try:
    with zipfile.ZipFile(sys.argv[1]) as z:
        for n in z.namelist():
            print(n)
except Exception as exc:
    print(f"ZIPFILE_ERROR {exc}", file=sys.stderr)
PY
fi
PY_ENTRIES=$(grep -c "" "$PY_LISTING" 2>/dev/null || echo 0)

echo "  unzip listed $UNZIP_ENTRIES lines; python zipfile listed $PY_ENTRIES entries"

have() { grep -q "$1" "$LISTING" 2>/dev/null || grep -q "$1" "$PY_LISTING" 2>/dev/null; }

missing=""
have "classes.dex"    || missing="$missing classes.dex"
have "resources.arsc" || missing="$missing resources.arsc"

if [ -n "$missing" ]; then
    echo >&2
    echo "REJECTED: the APK is missing:$missing" >&2
    echo >&2
    echo "Neither reader found them, so this is very unlikely to be a tooling" >&2
    echo "artefact. What the APK does contain:" >&2
    if [ "$PY_ENTRIES" -gt 0 ]; then
        head -40 "$PY_LISTING" >&2
        echo "  ... $PY_ENTRIES entries total" >&2
    else
        head -40 "$LISTING" >&2
        echo "  (python zipfile could not read the archive either)" >&2
    fi
    echo >&2
    echo "The unsigned artifact from the same run is worth comparing:" >&2
    echo "  gh run download $RUN_ID --name atropos-android-unsigned --dir /tmp/unsigned" >&2
    exit 1
fi
echo "  contains classes.dex and resources.arsc"

# Timestamped filename so a stale copy in the downloads folder can never be
# mistaken for the new build. That masked two earlier fixes.
STAMP="$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT_DIR"
DEST="$OUT_DIR/ATROPOS-$STAMP.apk"
cp "$APK" "$DEST"

# Android's media index does not notice files written by Termux, so the APK is
# on disk but invisible to the Files and Downloads apps. That matters when
# Termux itself cannot be granted "install unknown apps" — opening the APK from
# a file manager is then the only route, and it cannot open what it cannot see.
if command -v termux-media-scan >/dev/null 2>&1; then
    termux-media-scan "$DEST" >/dev/null 2>&1 || true
fi

echo
echo "OK: $DEST"
echo "size: $(du -h "$DEST" | cut -f1)"
echo "from run $RUN_ID"
echo
echo "install with:"
echo "  adb install -r \"$DEST\""
echo "or open it from the Downloads app."
