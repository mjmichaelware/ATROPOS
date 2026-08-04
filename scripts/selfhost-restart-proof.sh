#!/usr/bin/env bash
set -euo pipefail

JAR="${1:-./atropos.jar}"
PROMPT="${2:-ATROPOS, build yourself from the inside out and run self-host Phase 11}"
KILL_AFTER_SECONDS="${ATROPOS_SELF_HOST_KILL_AFTER_SECONDS:-1}"
: "${ATROPOS_VAULT_KEY:?set ATROPOS_VAULT_KEY to a base64-encoded AES-256 key}"

sanitize_text() {
  printf '%s' "$1" |
    tr '\r\n\t' '   ' |
    sed -E 's/(token|secret|password|api[_-]?key)[=:][^ ]*/\1=[REDACTED]/Ig' |
    cut -c1-400
}

# Keep the proof environment secret-minimal while preserving Termux loader
# variables required by native executables launched from the JVM.
runtime_env() {
  local args=(env -i "PATH=$PATH")
  local name
  for name in LD_LIBRARY_PATH LD_PRELOAD TERMUX_EXEC__PROC_SELF_EXE; do
    if [ "${!name+x}" = x ]; then
      args+=("$name=${!name}")
    fi
  done
  args+=("GRADLE_USER_HOME=${GRADLE_USER_HOME:-$HOME/.gradle}")
  "${args[@]}" "$@"
}

if [ ! -f "$JAR" ]; then
  echo "missing installed jar: $JAR" >&2
  exit 2
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ABS_JAR="$(cd "$(dirname "$JAR")" && pwd)/$(basename "$JAR")"
SANDBOX="$(mktemp -d "${TMPDIR:-/tmp}/atropos-restart-proof.XXXXXX")"
PROOF_DIR="$ROOT/.atropos/self-hosting/proofs"
PROOF_FILE="$PROOF_DIR/phase11-restart-proof.properties"

mkdir -p "$PROOF_DIR"
# Keep restart proof independent of ignored dependency/build caches and fast
# enough for constrained Termux devices.
tar -C "$ROOT" \
  --exclude=.git \
  --exclude=.atropos \
  --exclude=.gradle \
  --exclude=build \
  --exclude=node_modules \
  --exclude='apps/web/.next' \
  --exclude='apps/web/tsconfig.tsbuildinfo' \
  -cf - . | tar -C "$SANDBOX" -xf -
mkdir -p "$SANDBOX/installed"
PRIOR_JAR="$SANDBOX/prior-installed.jar"
printf 'restart proof prior jar\n' > "$PRIOR_JAR"
cp "$PRIOR_JAR" "$SANDBOX/installed/atropos.jar"

git -C "$SANDBOX" init >/dev/null
git -C "$SANDBOX" config user.email "atropos@example.invalid"
git -C "$SANDBOX" config user.name "ATROPOS Restart Proof"
git -C "$SANDBOX" add .
git -C "$SANDBOX" commit -m "restart proof baseline" >/dev/null

run_runtime() {
  local input="$1"
  local output="$2"
  local max_advances="${3:-}"
  printf '%s\n/exit\n' "$input" |
    runtime_env ATROPOS_ROOT="$SANDBOX" ATROPOS_VAULT_KEY="$ATROPOS_VAULT_KEY" \
      ATROPOS_SELF_HOST_MAX_ADVANCES="$max_advances" \
      java -Djdk.lang.Process.launchMechanism=VFORK \
        -Datropos.installed.jar="$SANDBOX/installed/atropos.jar" -jar "$ABS_JAR" \
      >"$output" 2>&1
}

FIRST="$SANDBOX/first.out"
SECOND="$SANDBOX/second.out"
set +e
(
  cd "$SANDBOX"
  printf '%s\n' "$PROMPT" |
    runtime_env ATROPOS_ROOT="$SANDBOX" ATROPOS_VAULT_KEY="$ATROPOS_VAULT_KEY" \
      ATROPOS_SELF_HOST_MAX_ADVANCES="1" \
      java -Djdk.lang.Process.launchMechanism=VFORK \
        -Datropos.installed.jar="$SANDBOX/installed/atropos.jar" -jar "$ABS_JAR"
) >"$FIRST" 2>&1 &
PID=$!
sleep "$KILL_AFTER_SECONDS"
KILLED=false
if kill -0 "$PID" 2>/dev/null; then
  kill -KILL "$PID" 2>/dev/null || true
  KILLED=true
fi
wait "$PID" 2>/dev/null || true
set -e

if [ "$KILLED" != true ]; then
  echo "restart proof failed: first installed process completed before kill" >&2
  exit 20
fi

set +e
run_runtime "/agent self-host recover" "$SECOND" ""
SECOND_EXIT=$?
set -e
if [ "$SECOND_EXIT" -ne 0 ]; then
  echo "restart proof failed: recovery runtime exited with code $SECOND_EXIT" >&2
  sed -n '1,240p' "$SECOND" >&2
  exit 21
fi

if ! grep -Eq '^ATROPOS_SELF_HOST_RUN_STARTED goal=[^[:space:]]+$' "$FIRST" &&
   ! grep -Eq '^ATROPOS_SELF_HOST_RUN_STARTED goal=[^[:space:]]+$' "$SECOND"; then
  echo "restart proof failed: canonical self-host start marker missing" >&2
  sed -n '1,220p' "$FIRST" >&2
  sed -n '1,220p' "$SECOND" >&2
  exit 21
fi

RUN_META="$(find "$SANDBOX/.atropos/runs" -maxdepth 1 -type f -name '*.meta' -print -quit || true)"
SNAPSHOT="$(find "$SANDBOX/.atropos/recovery/snapshots" -maxdepth 1 -type f -name '*.snapshot' -print -quit || true)"
MARKER="$SANDBOX/src/main/kotlin/atropos/core/agent/SelfHostCradleRuntimeState.kt"
MARKER_TEST="$SANDBOX/src/test/kotlin/atropos/core/agent/SelfHostCradleRuntimeStateTest.kt"
EVIDENCE_DIR="$(find "$SANDBOX/.atropos/self-hosting/evidence" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort | tail -n 1 || true)"

if [ -z "$RUN_META" ] || [ -z "$SNAPSHOT" ]; then
  echo "restart proof failed: durable run or restart snapshot missing" >&2
  exit 22
fi
if [ ! -s "$MARKER" ] || [ ! -s "$MARKER_TEST" ]; then
  echo "restart proof failed: source mutation was not continued after restart" >&2
  exit 23
fi
if ! grep -q 'currentNodeId=' "$RUN_META" || ! (grep -q 'territoryB64=' "$RUN_META" || grep -q '^territory=' "$RUN_META") || ! grep -q 'evidenceEntryB64=' "$RUN_META"; then
  echo "restart proof failed: goal/node/territory/evidence state incomplete" >&2
  exit 24
fi
if [ -z "$EVIDENCE_DIR" ] || [ ! -s "$EVIDENCE_DIR/bundle.md" ] || [ ! -s "$EVIDENCE_DIR/bundle.json" ]; then
  echo "restart proof failed: evidence bundle missing after restart" >&2
  exit 25
fi
if ! grep -q 'provenanceChainSha256' "$EVIDENCE_DIR/bundle.json" ||
   ! grep -q '"redacted": true' "$EVIDENCE_DIR/bundle.json" ||
   ! grep -q 'evidenceHashes' "$EVIDENCE_DIR/bundle.json" ||
   ! grep -q 'sha256' "$EVIDENCE_DIR/bundle.md"; then
  echo "restart proof failed: evidence provenance/redaction/hash fields incomplete" >&2
  exit 26
fi
SAFETY_LINE="$(grep -n -m1 'self_host_safety' "$EVIDENCE_DIR/bundle.md" | cut -d: -f1 || true)"
DIRECTOR_LINE="$(grep -n -m1 'director_pre_promote' "$EVIDENCE_DIR/bundle.md" | cut -d: -f1 || true)"
GATE_LINE="$(grep -n -m1 'promotion_gate' "$EVIDENCE_DIR/bundle.md" | cut -d: -f1 || true)"
SWAP_LINE="$(grep -n -m1 'jar_swap' "$EVIDENCE_DIR/bundle.md" | cut -d: -f1 || true)"
if [ -z "$SAFETY_LINE" ] || [ -z "$DIRECTOR_LINE" ] || [ -z "$GATE_LINE" ] || [ -z "$SWAP_LINE" ] ||
   [ "$SAFETY_LINE" -ge "$DIRECTOR_LINE" ] || [ "$DIRECTOR_LINE" -ge "$GATE_LINE" ] || [ "$GATE_LINE" -ge "$SWAP_LINE" ]; then
  echo "restart proof failed: promotion evidence gate order is incomplete" >&2
  exit 26
fi
BACKUP="$(find "$SANDBOX/installed" -maxdepth 1 -type f -name 'atropos.jar.backup-*' -print -quit 2>/dev/null || true)"
if [ -z "$BACKUP" ] || ! cmp -s "$PRIOR_JAR" "$BACKUP" || ! cmp -s "$SANDBOX/build/libs/ATROPOS.jar" "$SANDBOX/installed/atropos.jar"; then
  echo "restart proof failed: real candidate swap or prior-JAR preservation missing" >&2
  exit 27
fi

MARKER_HASH="$(sha256sum "$MARKER" | awk '{print $1}')"
SNAPSHOT_HASH="$(sha256sum "$SNAPSHOT" | awk '{print $1}')"
JSON_HASH="$(sha256sum "$EVIDENCE_DIR/bundle.json" | awk '{print $1}')"
SAFE_PROMPT="$(sanitize_text "$PROMPT")"
cat > "$PROOF_FILE" <<EOF
prompt=$SAFE_PROMPT
installedRuntimeJar=$ABS_JAR
sandboxRoot=$SANDBOX
firstProcessKilled=$KILLED
runMeta=$RUN_META
restartSnapshot=$SNAPSHOT
restartSnapshotSha256=$SNAPSHOT_HASH
marker=$MARKER
markerSha256=$MARKER_HASH
evidenceJson=$EVIDENCE_DIR/bundle.json
evidenceJsonSha256=$JSON_HASH
candidateJar=$SANDBOX/build/libs/ATROPOS.jar
candidateJarSha256=$(sha256sum "$SANDBOX/build/libs/ATROPOS.jar" | awk '{print $1}')
sandboxBackupJar=$BACKUP
sandboxBackupJarSha256=$(sha256sum "$BACKUP" | awk '{print $1}')
secondOutput=$SECOND
result=PASS
EOF

echo "ATROPOS_SELFHOST_RESTART_PROOF_OK"
echo "proof=$PROOF_FILE"
echo "sandbox=$SANDBOX"
