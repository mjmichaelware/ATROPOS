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
echo "verifying..."
if ! unzip -l "$APK" | grep -q "classes.dex"; then
    echo "REJECTED: no classes.dex — this APK contains no compiled code." >&2
    echo "This is the 'empty shell' build; it will never install." >&2
    exit 1
fi
if ! unzip -l "$APK" | grep -q "resources.arsc"; then
    echo "REJECTED: no resources.arsc — the resource table is missing." >&2
    exit 1
fi

# Timestamped filename so a stale copy in the downloads folder can never be
# mistaken for the new build. That masked two earlier fixes.
STAMP="$(date +%Y%m%d-%H%M%S)"
mkdir -p "$OUT_DIR"
DEST="$OUT_DIR/ATROPOS-$STAMP.apk"
cp "$APK" "$DEST"

echo
echo "OK: $DEST"
echo "size: $(du -h "$DEST" | cut -f1)"
echo "from run $RUN_ID"
echo
echo "install with:"
echo "  adb install -r \"$DEST\""
echo "or open it from the Downloads app."
