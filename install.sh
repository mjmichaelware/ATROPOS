#!/usr/bin/env sh
# ATROPOS installer.
#
#   curl -fsSL https://raw.githubusercontent.com/mjmichaelware/ATROPOS/main/install.sh | sh
#
# Downloads a prebuilt jar rather than compiling. The engine is a 967-file
# Kotlin tree whose compile needs more heap than a phone has spare, which is
# the whole reason this exists: the device that most needs to run ATROPOS is
# the one least able to build it.
#
# POSIX sh, not bash. Termux ships several shells and this has to run under
# whichever one is there.
set -eu

REPO="${ATROPOS_REPO:-mjmichaelware/ATROPOS}"
# The rolling build from main by default. Set ATROPOS_VERSION=v2.0.0 for a
# fixed release, which is what a bug report should cite.
VERSION="${ATROPOS_VERSION:-latest}"
HOST_PREFIX="${PREFIX:-}"
CONFIG_DIR="${ATROPOS_PREFIX:-$HOME/.atropos}"
BIN_DIR="${ATROPOS_BIN_DIR:-}"

say()  { printf '%s\n' "$*"; }
fail() { printf 'error: %s\n' "$*" >&2; exit 1; }

case "$REPO" in
  */*/*|*[!A-Za-z0-9._/-]*|/*|*/|*//*|*..*)
    fail "invalid repository; expected owner/name"
    ;;
  */*) : ;;
  *) fail "invalid repository; expected owner/name" ;;
esac
case "$VERSION" in
  latest|v[0-9]*) : ;;
  *) fail "invalid version; expected latest or a v-prefixed release tag" ;;
esac

if [ "$VERSION" = "latest" ]; then
  # The rolling `latest` release is deliberately a prerelease. GitHub's
  # `/releases/latest` endpoint skips prereleases, so address its immutable tag
  # directly instead of silently installing an older stable artifact.
  JAR_URL="https://github.com/$REPO/releases/download/latest/ATROPOS.jar"
else
  JAR_URL="https://github.com/$REPO/releases/download/$VERSION/ATROPOS.jar"
fi
SHA_URL="$JAR_URL.sha256"

# Detect the install target once. Termux is Linux underneath, but its PREFIX
# is the important boundary and must not be silently treated as desktop Linux.
OS_NAME=$(uname -s 2>/dev/null || printf 'unknown')
CPU_NAME=$(uname -m 2>/dev/null || printf 'unknown')
case "$CPU_NAME" in
  aarch64|arm64) CPU_ARCH=aarch64 ;;
  x86_64|amd64) CPU_ARCH=x86_64 ;;
  *) fail "unsupported CPU architecture: $CPU_NAME (expected aarch64 or x86_64)" ;;
esac
if [ -n "$HOST_PREFIX" ]; then
  PLATFORM="termux-$CPU_ARCH"
else
  case "$OS_NAME" in
    Linux) PLATFORM="linux-$CPU_ARCH" ;;
    Darwin) PLATFORM="darwin-$CPU_ARCH" ;;
    *) fail "unsupported operating system: $OS_NAME (expected Linux or Darwin)" ;;
  esac
fi
if [ -z "$BIN_DIR" ]; then
  case "$PLATFORM" in
    termux-*) BIN_DIR="$HOST_PREFIX/bin" ;;
    *) BIN_DIR="$HOME/.local/bin" ;;
  esac
fi

# --- what the host must already have --------------------------------------
# Checked before anything is downloaded, so a missing dependency costs no
# bandwidth on a metered connection.
command -v java >/dev/null 2>&1 || fail \
  "java not found. ATROPOS needs a JVM (17 or newer).
  Termux:  pkg install openjdk-21
  Debian:  sudo apt install openjdk-21-jre-headless"

JAVA_MAJOR=$(java -version 2>&1 | head -1 | sed -n 's/.*version "\([0-9]*\).*/\1/p')
if [ -n "${JAVA_MAJOR:-}" ] && [ "$JAVA_MAJOR" -lt 17 ] 2>/dev/null; then
  fail "java $JAVA_MAJOR found, 17 or newer required."
fi

if command -v curl >/dev/null 2>&1; then
  DOWNLOAD='curl -fsSL -o'
elif command -v wget >/dev/null 2>&1; then
  DOWNLOAD='wget -qO'
else
  fail "neither curl nor wget found."
fi

# --- fetch ------------------------------------------------------------------
mkdir -p "$CONFIG_DIR" "$CONFIG_DIR/provider" "$BIN_DIR"
# Bootstrap only missing local configuration files. Existing operator config is
# never overwritten by an upgrade.
if [ ! -f "$CONFIG_DIR/config.json" ]; then
  printf '%s\n' '{}' > "$CONFIG_DIR/config.json"
fi
if [ ! -f "$CONFIG_DIR/provider/providers.json" ]; then
  printf '%s\n' '[]' > "$CONFIG_DIR/provider/providers.json"
fi
TMP="$CONFIG_DIR/.ATROPOS.jar.part"

say "Downloading ATROPOS ($VERSION) ..."
say "Platform: $PLATFORM"
$DOWNLOAD "$TMP" "$JAR_URL" || fail \
  "download failed from $JAR_URL
  If this is a fresh repository the release may not exist yet -- push to main
  once so the Release JAR workflow can publish it."

# --- verify -----------------------------------------------------------------
# A jar is executable code. Checking it against the hash the build published is
# the difference between installing what was built and installing whatever
# answered the request.
$DOWNLOAD "$TMP.sha256" "$SHA_URL" 2>/dev/null || {
  rm -f "$TMP" "$TMP.sha256"
  fail "no published checksum at $SHA_URL; refusing to install an unverified jar."
}
EXPECTED=$(awk 'NF { print tolower($1); exit }' "$TMP.sha256")
if ! printf '%s\n' "$EXPECTED" | grep -Eq '^[0-9a-f]{64}$'; then
  rm -f "$TMP" "$TMP.sha256"
  fail "published checksum is missing or malformed; nothing was installed."
fi
if command -v sha256sum >/dev/null 2>&1; then
  ACTUAL=$(sha256sum "$TMP" | awk '{print $1}')
elif command -v shasum >/dev/null 2>&1; then
  ACTUAL=$(shasum -a 256 "$TMP" | awk '{print $1}')
else
  rm -f "$TMP" "$TMP.sha256"
  fail "no sha256 tool found; refusing to install an unverified jar."
fi
if [ "$ACTUAL" != "$EXPECTED" ]; then
  rm -f "$TMP" "$TMP.sha256"
  fail "checksum mismatch.
  expected $EXPECTED
  actual   $ACTUAL
  The download was corrupted or tampered with. Nothing was installed."
fi
rm -f "$TMP.sha256"

mv "$TMP" "$CONFIG_DIR/ATROPOS.jar"

# --- launcher ---------------------------------------------------------------
# A script rather than an alias, so it works from any shell and from other
# programs. `exec` so signals and the exit code belong to the JVM: Ctrl-C in
# the CLI must reach ATROPOS, not a wrapper standing in front of it.
cat > "$BIN_DIR/atropos" <<EOF
#!/usr/bin/env sh
# Generated by the ATROPOS installer. Re-run the installer to update.
export ATROPOS_PLATFORM="\${ATROPOS_PLATFORM:-$PLATFORM}"
export ATROPOS_CONFIG_DIR="\${ATROPOS_CONFIG_DIR:-$CONFIG_DIR}"
exec java \${ATROPOS_JAVA_OPTS:-} -jar "$CONFIG_DIR/ATROPOS.jar" "\$@"
EOF
chmod +x "$BIN_DIR/atropos"

# A downloaded executable is not a usable install until the same health entry
# point used by the release workflow starts successfully. This check is local,
# provider-free, and never sends credentials or project data anywhere.
if "$BIN_DIR/atropos" --health > "$CONFIG_DIR/first-run-doctor.txt" 2>&1 &&
   "$BIN_DIR/atropos" --doctor >> "$CONFIG_DIR/first-run-doctor.txt" 2>&1; then
  say "doctor: PASS"
else
  say "doctor: FAIL (see $CONFIG_DIR/first-run-doctor.txt)" >&2
  fail "the installed JAR did not pass its first-run health check"
fi

say ""
say "Installed:"
say "  jar      $CONFIG_DIR/ATROPOS.jar"
say "  launcher $BIN_DIR/atropos"

case ":$PATH:" in
  *":$BIN_DIR:"*) say ""; say "Run it:  atropos" ;;
  *)
    say ""
    say "$BIN_DIR is not on your PATH. Add it:"
    say "  echo 'export PATH=\"\$PATH:$BIN_DIR\"' >> ~/.profile && . ~/.profile"
    say ""
    say "Or run it directly:  $BIN_DIR/atropos"
    ;;
esac

say ""
say "ATROPOS reads and writes only inside the directory you launch it from."
say "Platform: $PLATFORM"
say "Start it from the project you want it to work on."
