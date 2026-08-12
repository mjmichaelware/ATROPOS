#!/usr/bin/env bash
# Create the Android signing keystore and upload the three required secrets
# through the GitHub API. One command, no web UI.
#
#   bash scripts/setup-signing-secrets.sh
#
# The keystore is generated on this device and never leaves it except as the
# ANDROID_KEYSTORE_B64 secret in your own repository. It is the permanent
# identity of the app: if it is replaced, Android refuses to upgrade an already
# installed copy, which is the "signature conflict" that blocks installs. The
# script therefore reuses an existing keystore instead of regenerating one.
#
# Requires: keytool (any JDK) and gh, authenticated:  gh auth login
set -euo pipefail

KEYSTORE="${KEYSTORE:-$HOME/atropos-release.jks}"
ALIAS="${ALIAS:-atropos}"
PASSFILE="${PASSFILE:-$HOME/.atropos-keystore-pass}"

command -v keytool >/dev/null 2>&1 || { echo "keytool not found. Install a JDK: pkg install openjdk-17" >&2; exit 1; }
command -v gh      >/dev/null 2>&1 || { echo "gh not found. Install it: pkg install gh" >&2; exit 1; }
gh auth status >/dev/null 2>&1     || { echo "gh is not authenticated. Run: gh auth login" >&2; exit 1; }

REPO="$(gh repo view --json nameWithOwner --jq .nameWithOwner)"
echo "repo: $REPO"
echo

# ---- password -------------------------------------------------------------
# Reused across runs so the keystore stays openable. Stored 0600 locally.
if [ -f "$PASSFILE" ]; then
    STOREPASS="$(cat "$PASSFILE")"
    echo "using existing password from $PASSFILE"
else
    STOREPASS="$(head -c 24 /dev/urandom | base64 | tr -d '/+=' | head -c 24)"
    umask 077
    printf '%s' "$STOREPASS" > "$PASSFILE"
    echo "generated a new password and saved it to $PASSFILE (keep this)"
fi

# ---- keystore -------------------------------------------------------------
if [ -f "$KEYSTORE" ]; then
    echo "reusing existing keystore: $KEYSTORE"
    echo "(not regenerating — a new key would break upgrades of the installed app)"
    if ! keytool -list -keystore "$KEYSTORE" -storepass "$STOREPASS" -alias "$ALIAS" >/dev/null 2>&1; then
        echo >&2
        echo "ERROR: $KEYSTORE will not open with alias '$ALIAS' and the stored password." >&2
        echo "Either the password in $PASSFILE is wrong, or the alias differs." >&2
        echo "Inspect it with:  keytool -list -v -keystore \"$KEYSTORE\"" >&2
        echo "To start over (this changes the app identity):  rm \"$KEYSTORE\" \"$PASSFILE\"" >&2
        exit 1
    fi
else
    echo "generating keystore: $KEYSTORE"
    keytool -genkeypair -v \
        -keystore "$KEYSTORE" \
        -alias "$ALIAS" \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -storepass "$STOREPASS" -keypass "$STOREPASS" \
        -dname "CN=ATROPOS, OU=Dev, O=ATROPOS, L=Local, S=Local, C=US" >/dev/null
    chmod 600 "$KEYSTORE"
    echo "created."
fi
echo

# ---- encode and verify the round trip -------------------------------------
# Verified before upload: a truncated or wrapped base64 secret is exactly what
# produces apksigner's "Tag number over 30 is not supported".
B64="$(base64 -w0 "$KEYSTORE" 2>/dev/null || base64 "$KEYSTORE" | tr -d '\n')"
echo "base64 length: ${#B64} chars"

TMP="$(mktemp)"; trap 'rm -f "$TMP"' EXIT
printf '%s' "$B64" | base64 -d > "$TMP"
if ! cmp -s "$TMP" "$KEYSTORE"; then
    echo "ERROR: base64 does not round-trip back to the keystore. Aborting." >&2
    exit 1
fi
keytool -list -keystore "$TMP" -storepass "$STOREPASS" -alias "$ALIAS" >/dev/null 2>&1 || {
    echo "ERROR: the decoded keystore does not open. Aborting before upload." >&2; exit 1; }
echo "round-trip verified: decoded keystore opens with alias '$ALIAS'"
echo

# ---- upload via the API ---------------------------------------------------
echo "setting repository secrets on $REPO ..."
printf '%s' "$B64"       | gh secret set ANDROID_KEYSTORE_B64      --repo "$REPO"
printf '%s' "$ALIAS"     | gh secret set ANDROID_KEY_ALIAS         --repo "$REPO"
printf '%s' "$STOREPASS" | gh secret set ANDROID_KEYSTORE_PASSWORD --repo "$REPO"
echo "done."
echo

echo "secrets now present:"
gh secret list --repo "$REPO" | grep -E "ANDROID_" || echo "  (none listed — check gh permissions)"
echo

echo "Keep these two files. Losing them means the app can never be upgraded in place:"
echo "  $KEYSTORE"
echo "  $PASSFILE"
echo
echo "Next:"
echo "  gh workflow run android-apk.yml --repo $REPO"
echo "  bash scripts/pull-apk.sh"
