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
MAX_PROOF_OUTPUT_BYTES=65536
PROOF_MAX_ADVANCES="${ATROPOS_SELF_HOST_MAX_ADVANCES:-}"

sanitize_text() {
  printf '%s' "$1" |
    tr '\r\n\t' '   ' |
    sed -E 's/(token|secret|password|api[_-]?key)[=:][^ ]*/\1=[REDACTED]/Ig' |
    cut -c1-400
}

mkdir -p "$PROOF_DIR"
cp -a "$ROOT"/. "$SANDBOX"/
rm -rf "$SANDBOX/.git" "$SANDBOX/.atropos" "$SANDBOX/build"
mkdir -p "$SANDBOX/installed"
PRIOR_JAR="$SANDBOX/prior-installed.jar"
printf 'prior installed proof jar\n' > "$PRIOR_JAR"
cp "$PRIOR_JAR" "$SANDBOX/installed/atropos.jar"

git -C "$SANDBOX" init >/dev/null
git -C "$SANDBOX" config user.email "atropos@example.invalid"
git -C "$SANDBOX" config user.name "ATROPOS Installed Proof"
git -C "$SANDBOX" add .
git -C "$SANDBOX" commit -m "installed proof baseline" >/dev/null

OUT="$SANDBOX/installed-proof.out"
set +e
(
  cd "$SANDBOX"
  printf '%s\n/exit\n' "$PROMPT" |
    env -i PATH="$PATH" ATROPOS_ROOT="$SANDBOX" \
      ATROPOS_SELF_HOST_MAX_ADVANCES="$PROOF_MAX_ADVANCES" \
      java -Datropos.installed.jar="$SANDBOX/installed/atropos.jar" -jar "$ABS_JAR"
) 2>&1 |
  sed -E 's/(token|secret|password|api[_-]?key)[=:][^ ]*/\1=[REDACTED]/Ig' >"$OUT"
JAVA_EXIT=${PIPESTATUS[0]}
set -e

if [ "${JAVA_EXIT:-1}" -ne 0 ]; then
  echo "installed proof failed: runtime exited with code $JAVA_EXIT" >&2
  sed -n '1,220p' "$OUT" >&2
  exit 17
fi

if [ "$(wc -c < "$OUT")" -gt "$MAX_PROOF_OUTPUT_BYTES" ]; then
  head -c "$MAX_PROOF_OUTPUT_BYTES" "$OUT" > "$OUT.limited"
  mv "$OUT.limited" "$OUT"
fi

MARKER="$SANDBOX/src/main/kotlin/atropos/core/agent/SelfHostCradleRuntimeState.kt"
MARKER_TEST="$SANDBOX/src/test/kotlin/atropos/core/agent/SelfHostCradleRuntimeStateTest.kt"
STATUS="$(git -C "$SANDBOX" status --short -- "$MARKER" "$MARKER_TEST" || true)"
EVIDENCE_DIR="$(find "$SANDBOX/.atropos/self-hosting/evidence" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort | tail -n 1 || true)"
BACKUP="$(find "$SANDBOX/installed" -maxdepth 1 -type f -name 'atropos.jar.backup-*' 2>/dev/null | sort | tail -n 1 || true)"

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
if [ -z "$STATUS" ]; then
  echo "installed proof failed: source mutation produced no git status change" >&2
  exit 12
fi
if ! grep -q "LAST_SELF_HOST_GOAL" "$MARKER"; then
  echo "installed proof failed: marker file lacks goal constant" >&2
  exit 13
fi
if [ ! -s "$SANDBOX/installed/atropos.jar" ] || [ ! -s "$SANDBOX/build/libs/ATROPOS.jar" ] || ! cmp -s "$SANDBOX/build/libs/ATROPOS.jar" "$SANDBOX/installed/atropos.jar"; then
  echo "installed proof failed: real candidate jar was not swapped byte-for-byte" >&2
  exit 14
fi
if [ -z "$BACKUP" ] || ! cmp -s "$PRIOR_JAR" "$BACKUP"; then
  echo "installed proof failed: prior sandbox jar backup missing or wrong" >&2
  exit 15
fi
if [ -z "$EVIDENCE_DIR" ] || [ ! -s "$EVIDENCE_DIR/bundle.md" ] || [ ! -s "$EVIDENCE_DIR/bundle.json" ]; then
  echo "installed proof failed: evidence bundle missing" >&2
  exit 16
fi
if ! grep -q 'provenanceChainSha256' "$EVIDENCE_DIR/bundle.json" ||
   ! grep -q '"redacted": true' "$EVIDENCE_DIR/bundle.json" ||
   ! grep -q 'evidenceHashes' "$EVIDENCE_DIR/bundle.json" ||
   ! grep -q 'sha256' "$EVIDENCE_DIR/bundle.md"; then
  echo "installed proof failed: evidence provenance/redaction/hash fields incomplete" >&2
  exit 16
fi
SAFETY_LINE="$(grep -n -m1 'self_host_safety' "$EVIDENCE_DIR/bundle.md" | cut -d: -f1 || true)"
DIRECTOR_LINE="$(grep -n -m1 'director_pre_promote' "$EVIDENCE_DIR/bundle.md" | cut -d: -f1 || true)"
GATE_LINE="$(grep -n -m1 'promotion_gate' "$EVIDENCE_DIR/bundle.md" | cut -d: -f1 || true)"
SWAP_LINE="$(grep -n -m1 'jar_swap' "$EVIDENCE_DIR/bundle.md" | cut -d: -f1 || true)"
if [ -z "$SAFETY_LINE" ] || [ -z "$DIRECTOR_LINE" ] || [ -z "$GATE_LINE" ] || [ -z "$SWAP_LINE" ] ||
   [ "$SAFETY_LINE" -ge "$DIRECTOR_LINE" ] || [ "$DIRECTOR_LINE" -ge "$GATE_LINE" ] || [ "$GATE_LINE" -ge "$SWAP_LINE" ]; then
  echo "installed proof failed: promotion evidence gate order is incomplete" >&2
  exit 16
fi

MARKER_HASH="$(sha256sum "$MARKER" | awk '{print $1}')"
JSON_HASH="$(sha256sum "$EVIDENCE_DIR/bundle.json" | awk '{print $1}')"
MD_HASH="$(sha256sum "$EVIDENCE_DIR/bundle.md" | awk '{print $1}')"
JAR_HASH="$(sha256sum "$SANDBOX/installed/atropos.jar" | awk '{print $1}')"
BACKUP_HASH="$(sha256sum "$BACKUP" | awk '{print $1}')"

SAFE_PROMPT="$(sanitize_text "$PROMPT")"
cat > "$PROOF_FILE" <<EOF
prompt=$SAFE_PROMPT
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
candidateJar=$SANDBOX/build/libs/ATROPOS.jar
candidateJarSha256=$(sha256sum "$SANDBOX/build/libs/ATROPOS.jar" | awk '{print $1}')
candidateBuildGate=test+jar
maxAdvances=${PROOF_MAX_ADVANCES:-default}
outputLog=$OUT
result=PASS
EOF

echo "ATROPOS_SELFHOST_INSTALLED_PROOF_OK"
echo "proof=$PROOF_FILE"
echo "sandbox=$SANDBOX"
