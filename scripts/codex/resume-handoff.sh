#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PYTHONPATH="$script_dir${PYTHONPATH:+:$PYTHONPATH}" \
exec python3 - "$@" <<'PY'
from __future__ import annotations

import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

from _lib import HANDOFF_ROOT, atomic_write_text, ensure_dir


def git_output(*args: str) -> str:
    return subprocess.check_output(["git", *args], text=True)


def parse_status() -> tuple[list[str], list[str]]:
    tracked: list[str] = []
    untracked: list[str] = []
    for line in git_output("status", "--porcelain=v1").splitlines():
        if not line.strip():
            continue
        if line.startswith("?? "):
            untracked.append(line[3:])
        else:
            tracked.append(line[3:])
    return tracked, untracked


def main() -> int:
    ensure_dir(HANDOFF_ROOT)
    stamp = datetime.now(timezone.utc).strftime("%Y%m%d-%H%M%S")
    out = HANDOFF_ROOT / f"resume-{stamp}.md"
    latest = HANDOFF_ROOT / "latest.md"
    tracked, untracked = parse_status()
    dirty_diff = git_output("diff", "--name-only")
    dirty_stat = git_output("diff", "--stat")
    head = git_output("rev-parse", "HEAD").strip()
    branch = git_output("branch", "--show-current").strip()
    tracked_lines = [f"- {item}" for item in tracked] or ["- (none)"]
    untracked_lines = [f"- {item}" for item in untracked] or ["- (none)"]
    diff_lines = [f"- {line}" for line in dirty_diff.splitlines() if line.strip()] or ["- (none)"]
    body = "\n".join(
        [
            "# ATROPOS Resume Handoff",
            "",
            f"- generated_at: {datetime.now(timezone.utc).isoformat()}",
            f"- head: {head}",
            f"- branch: {branch or '(detached)'}",
            f"- cwd: {Path.cwd()}",
            "",
            "## Dirty tracked",
            "",
            *tracked_lines,
            "",
            "## Untracked",
            "",
            *untracked_lines,
            "",
            "## git diff --name-only",
            "",
            *diff_lines,
            "",
            "## git diff --stat",
            "",
            dirty_stat.rstrip() or "(none)",
            "",
        ]
    )
    atomic_write_text(out, body)
    atomic_write_text(latest, body)
    print(out)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
PY
