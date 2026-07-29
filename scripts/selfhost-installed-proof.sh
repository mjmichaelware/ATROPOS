#!/usr/bin/env bash
set -euo pipefail

JAR="${1:-./atropos.jar}"
PROMPT="${2:-ATROPOS, build yourself from the inside out and run self-host Phase 11}"

if [ ! -f "$JAR" ]; then
  echo "missing installed jar: $JAR" >&2
  exit 2
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ABS_JAR="$(cd "$(dirname "$JAR")" && pwd)/$(basename "$JAR")"
SANDBOX="$(mktemp -d "${TMPDIR:-/tmp}/atropos-installed-proof.XXXXXX")"
PROOF_DIR="$ROOT/.atropos/self-hosting/proofs"
PROOF_FILE="$PROOF_DIR/phase11-installed-runtime-proof.properties"

mkdir -p "$SANDBOX/src/main/kotlin/atropos/core/agent" \
  "$SANDBOX/src/test/kotlin/atropos/core/agent" \
  "$SANDBOX/installed" \
  "$PROOF_DIR"

cat > "$SANDBOX/settings.gradle.kts" <<'EOF'
pluginManagement {}
rootProject.name = "ATROPOS"
EOF
cat > "$SANDBOX/build.gradle.kts" <<'EOF'
plugins {}
EOF
cat > "$SANDBOX/gradlew" <<'EOF'
#!/bin/sh
mkdir -p build/libs
case " $* " in
  *" jar "*) printf 'installed proof candidate jar\n' > build/libs/ATROPOS.jar ;;
esac
exit 0
EOF
chmod +x "$SANDBOX/gradlew"
cat > "$SANDBOX/src/main/kotlin/atropos/Main.kt" <<'EOF'
package atropos
fun main() {}
EOF
printf 'prior installed proof jar\n' > "$SANDBOX/installed/atropos.jar"

git -C "$SANDBOX" init >/dev/null
git -C "$SANDBOX" config user.email "atropos@example.invalid"
git -C "$SANDBOX" config user.name "ATROPOS Installed Proof"
git -C "$SANDBOX" add .
git -C "$SANDBOX" commit -m "installed proof baseline" >/dev/null

OUT="$SANDBOX/installed-proof.out"
(
  cd "$SANDBOX"
  printf '%s\n/exit\n' "$PROMPT" | java -Datropos.installed.jar="$SANDBOX/installed/atropos.jar" -jar "$ABS_JAR"
) >"$OUT" 2>&1

MARKER="$SANDBOX/src/main/kotlin/atropos/core/agent/SelfHostCradleRuntimeState.kt"
MARKER_TEST="$SANDBOX/src/test/kotlin/atropos/core/agent/SelfHostCradleRuntimeStateTest.kt"
STATUS="$(git -C "$SANDBOX" status --short -- "$MARKER" "$MARKER_TEST" || true)"
EVIDENCE_DIR="$(find "$SANDBOX/.atropos/self-hosting/evidence" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | head -n 1 || true)"
BACKUP="$(find "$SANDBOX/installed" -maxdepth 1 -type f ! -name 'atropos.jar' | head -n 1 || true)"

if ! grep -q "SELF-HOST RUN" "$OUT"; then
  echo "installed proof failed: NL prompt did not reach self-host runner" >&2
  sed -n '1,220p' "$OUT" >&2
  exit 10
fi
if ! grep -q "self-host run promoted verified jar" "$OUT"; then
  echo "installed proof failed: self-host run did not promote sandbox jar" >&2
  sed -n '1,260p' "$OUT" >&2
  exit 11
fi
if [ ! -s "$MARKER" ] || [ ! -s "$MARKER_TEST" ]; then
  echo "installed proof failed: expected source marker files were not written" >&2
  exit 12
fi
if ! grep -q "LAST_SELF_HOST_GOAL" "$MARKER"; then
  echo "installed proof failed: marker file lacks goal constant" >&2
  exit 13
fi
if [ ! -s "$SANDBOX/installed/atropos.jar" ] || ! grep -q "installed proof candidate jar" "$SANDBOX/installed/atropos.jar"; then
  echo "installed proof failed: sandbox installed jar was not swapped" >&2
  exit 14
fi
if [ -z "$BACKUP" ] || ! grep -q "prior installed proof jar" "$BACKUP"; then
  echo "installed proof failed: prior sandbox jar backup missing or wrong" >&2
  exit 15
fi
if [ -z "$EVIDENCE_DIR" ] || [ ! -s "$EVIDENCE_DIR/bundle.md" ] || [ ! -s "$EVIDENCE_DIR/bundle.json" ]; then
  echo "installed proof failed: evidence bundle missing" >&2
  exit 16
fi

MARKER_HASH="$(sha256sum "$MARKER" | awk '{print $1}')"
JSON_HASH="$(sha256sum "$EVIDENCE_DIR/bundle.json" | awk '{print $1}')"
MD_HASH="$(sha256sum "$EVIDENCE_DIR/bundle.md" | awk '{print $1}')"
JAR_HASH="$(sha256sum "$SANDBOX/installed/atropos.jar" | awk '{print $1}')"
BACKUP_HASH="$(sha256sum "$BACKUP" | awk '{print $1}')"

cat > "$PROOF_FILE" <<EOF
prompt=$PROMPT
installedRuntimeJar=$ABS_JAR
sandboxRoot=$SANDBOX
markerPath=$MARKER
markerSha256=$MARKER_HASH
mutationStatus=${STATUS//$'\n'/ | }
evidenceMarkdown=$EVIDENCE_DIR/bundle.md
evidenceMarkdownSha256=$MD_HASH
evidenceJson=$EVIDENCE_DIR/bundle.json
evidenceJsonSha256=$JSON_HASH
sandboxInstalledJar=$SANDBOX/installed/atropos.jar
sandboxInstalledJarSha256=$JAR_HASH
sandboxBackupJar=$BACKUP
sandboxBackupJarSha256=$BACKUP_HASH
outputLog=$OUT
result=PASS
EOF

echo "ATROPOS_SELFHOST_INSTALLED_PROOF_OK"
echo "proof=$PROOF_FILE"
echo "sandbox=$SANDBOX"
