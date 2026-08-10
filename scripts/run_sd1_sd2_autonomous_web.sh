#!/usr/bin/env bash

set -u

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROMPT="$ROOT/docs/automation/SD1_SD2_ABSOLUTE_COMPLETION_PROMPT.md"
STATUS="$ROOT/docs/automation/SD1_SD2_AUTONOMOUS_STATUS.md"
REPORT="$ROOT/docs/automation/SD1_SD2_ABSOLUTE_COMPLETION_REPORT.md"
LOG_DIR="$ROOT/ops/logs/sd1-sd2-autonomous"
PORT="4096"
URL="http://127.0.0.1:$PORT"
RUNNER_PID="$LOG_DIR/runner.pid"
WEB_PID="$LOG_DIR/web.pid"

mkdir -p "$LOG_DIR"
cd "$ROOT" || exit 1

if [ ! -f "$PROMPT" ]; then
    echo "ERROR: Missing prompt:"
    echo "$PROMPT"
    exit 1
fi

if [ -f "$RUNNER_PID" ]; then
    OLD_PID="$(cat "$RUNNER_PID" 2>/dev/null || true)"
    if [ -n "$OLD_PID" ] && kill -0 "$OLD_PID" 2>/dev/null; then
        echo "The autonomous runner is already active as PID $OLD_PID"
        exit 0
    fi
fi

echo "$$" > "$RUNNER_PID"
trap 'rm -f "$RUNNER_PID"' EXIT INT TERM

termux-wake-lock >/dev/null 2>&1 || true

MODEL="$(
    opencode models opencode --refresh 2>/dev/null |
    grep -i 'deepseek-v4-flash' |
    head -n 1 |
    awk '{print $1}'
)"

if [ -z "$MODEL" ]; then
    MODEL="opencode/deepseek-v4-flash"
fi

echo "MODEL=$MODEL"
echo "ROOT=$ROOT"
echo "WEB=$URL"

export OPENCODE_CONFIG_CONTENT="$(
    printf '%s' \
    "{\"model\":\"$MODEL\",\"permission\":{\"read\":\"allow\",\"edit\":\"allow\",\"glob\":\"allow\",\"grep\":\"allow\",\"list\":\"allow\",\"bash\":\"allow\",\"task\":\"allow\",\"todowrite\":\"allow\",\"webfetch\":\"allow\",\"websearch\":\"allow\",\"lsp\":\"allow\",\"skill\":\"allow\",\"doom_loop\":\"allow\",\"question\":\"deny\",\"external_directory\":\"deny\"}}"
)"

export OPENCODE_EXPERIMENTAL_BACKGROUND_SUBAGENTS="true"
export OPENCODE_EXPERIMENTAL_OUTPUT_TOKEN_MAX="32768"
export OPENCODE_EXPERIMENTAL_BASH_DEFAULT_TIMEOUT_MS="3600000"

if ! curl -fsS "$URL" >/dev/null 2>&1; then
    nohup opencode web \
        --hostname 127.0.0.1 \
        --port "$PORT" \
        > "$LOG_DIR/web.log" 2>&1 &

    echo "$!" > "$WEB_PID"

    READY=0
    for ATTEMPT in $(seq 1 60); do
        if curl -fsS "$URL" >/dev/null 2>&1; then
            READY=1
            break
        fi
        sleep 1
    done

    if [ "$READY" -ne 1 ]; then
        echo "ERROR: OpenCode Web did not start."
        tail -100 "$LOG_DIR/web.log"
        exit 1
    fi
fi

termux-open-url "$URL" >/dev/null 2>&1 || true

CYCLE=1
FAILURES=0

echo
echo "Starting initial SD1/SD2 completion session..."

opencode run \
    --attach "$URL" \
    --dir "$ROOT" \
    --model "$MODEL" \
    --auto \
    --title "ATROPOS SD1 SD2 Absolute DAG Completion" \
    --file "$PROMPT" \
    "Execute the attached absolute-completion mandate now. Read all authoritative documents and continue implementing the DAG rather than returning a plan." \
    2>&1 | tee -a "$LOG_DIR/cycle-000-initial.log"

INITIAL_RC="${PIPESTATUS[0]}"

if [ "$INITIAL_RC" -ne 0 ]; then
    echo "Initial run exited with status $INITIAL_RC; continuation supervisor remains active."
fi

while ! grep -Fxq 'ABSOLUTE_COMPLETION: VERIFIED' "$STATUS" 2>/dev/null; do
    printf -v CYCLE_PADDED '%03d' "$CYCLE"

    echo
    echo "============================================================"
    echo "AUTONOMOUS CONTINUATION CYCLE $CYCLE"
    echo "============================================================"

    opencode run \
        --attach "$URL" \
        --dir "$ROOT" \
        --model "$MODEL" \
        --auto \
        --continue \
        --file "$PROMPT" \
        "Continue the attached mandate now. Reread the source documents, DAG, ledger, status file, tests, and current diff. Resume from the highest-priority incomplete dependency atom. Do not stop after one atom and do not write the verified marker prematurely." \
        2>&1 | tee -a "$LOG_DIR/cycle-${CYCLE_PADDED}.log"

    RC="${PIPESTATUS[0]}"

    if [ "$RC" -eq 0 ]; then
        FAILURES=0
        sleep 5
    else
        FAILURES=$((FAILURES + 1))
        echo "Cycle $CYCLE exited with status $RC."
        echo "Consecutive failed cycles: $FAILURES"
        sleep 30
    fi

    if ! curl -fsS "$URL" >/dev/null 2>&1; then
        echo "OpenCode Web stopped. Restarting it..."

        nohup opencode web \
            --hostname 127.0.0.1 \
            --port "$PORT" \
            >> "$LOG_DIR/web.log" 2>&1 &

        echo "$!" > "$WEB_PID"

        for ATTEMPT in $(seq 1 60); do
            curl -fsS "$URL" >/dev/null 2>&1 && break
            sleep 1
        done
    fi

    CYCLE=$((CYCLE + 1))
done

echo
echo "VERIFIED COMPLETION MARKER DETECTED."

STAMP="$(date +%Y%m%d_%H%M%S)"
DEST="$HOME/storage/downloads/ATROPOS_SD1_SD2_COMPLETION_${STAMP}"

mkdir -p "$DEST"

cp "$STATUS" "$DEST/" 2>/dev/null || true
cp "$REPORT" "$DEST/" 2>/dev/null || true
cp "$PROMPT" "$DEST/" 2>/dev/null || true

git status --short --branch > "$DEST/GIT_STATUS.txt"
git log -20 --oneline --decorate > "$DEST/RECENT_COMMITS.txt"

termux-media-scan -r "$DEST" >/dev/null 2>&1 || true

echo "Completion evidence copied to:"
echo "$DEST"
echo
echo "OpenCode Web remains available at:"
echo "$URL"
