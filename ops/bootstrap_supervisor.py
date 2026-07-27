#!/usr/bin/env python3

import json
import os
import time
import urllib.error
import urllib.request
from datetime import datetime
from pathlib import Path

ROOT = Path.home() / "ATROPOS"
BASE = "http://127.0.0.1:4096"
STATE = ROOT / ".atropos/bootstrap"
SID = (STATE / "session.id").read_text(encoding="utf-8").strip()
STATUS = ROOT / "docs/bootstrap/ATROPOS_SELF_HOSTING_BOOTSTRAP_STATUS.md"
LOG = STATE / "supervisor.log"

CONTINUATION = """
Continue the ATROPOS self-hosting bootstrap now.

Reread AGENTS.md, docs/bootstrap/ATROPOS_SELF_HOSTING_BOOTSTRAP.md,
the bootstrap status document, current Git status and diff, existing tests,
and the most recent provider output.

Resume from the first incomplete milestone or failed acceptance gate.
Implement production code, run focused verification, repair failures,
record evidence, and immediately continue.

Do not execute or modify Source Document 1–3 DAG work.
Do not return only recommendations or a next-session plan.
Do not write BOOTSTRAP_COMPLETION: VERIFIED until all ten milestones and
the complete synthetic self-hosting acceptance DAG pass.
""".strip()

HEADERS = {
    "Content-Type": "application/json",
    "x-opencode-directory": str(ROOT),
}


def log(message: str) -> None:
    STATE.mkdir(parents=True, exist_ok=True)
    line = f"{datetime.now().isoformat(timespec='seconds')} {message}"
    with LOG.open("a", encoding="utf-8") as handle:
        handle.write(line + "\n")


def request_json(path: str):
    request = urllib.request.Request(BASE + path, headers=HEADERS)
    with urllib.request.urlopen(request, timeout=20) as response:
        return json.load(response)


def send_continuation() -> None:
    payload = json.dumps({
        "agent": "build",
        "model": {
            "providerID": "opencode",
            "modelID": "deepseek-v4-flash-free",
        },
        "parts": [{
            "type": "text",
            "text": CONTINUATION,
        }],
    }).encode("utf-8")

    request = urllib.request.Request(
        f"{BASE}/session/{SID}/prompt_async",
        data=payload,
        headers=HEADERS,
        method="POST",
    )

    with urllib.request.urlopen(request, timeout=30):
        pass


last_state = None
log(f"supervisor started session={SID}")

while True:
    if STATUS.exists() and "BOOTSTRAP_COMPLETION: VERIFIED" in STATUS.read_text(
        encoding="utf-8"
    ):
        log("verified completion marker detected; supervisor exiting")
        break

    try:
        statuses = request_json("/session/status")
        state = statuses.get(SID, {}).get("type", "idle")

        if state != last_state:
            log(f"session state={state}")
            last_state = state

        if state == "busy":
            time.sleep(20)
            continue

        send_continuation()
        log("continuation submitted")
        time.sleep(60)

    except (
        urllib.error.URLError,
        urllib.error.HTTPError,
        TimeoutError,
        ConnectionError,
        json.JSONDecodeError,
    ) as failure:
        log(f"server/session error={failure}; retrying")
        time.sleep(30)
