#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PYTHONPATH="$script_dir${PYTHONPATH:+:$PYTHONPATH}" \
exec python3 - "$@" <<'PY'
from __future__ import annotations

import json
import os
import shlex
import subprocess
import sys
from pathlib import Path

from _lib import (
    REPO_ROOT,
    combined_file_fingerprint,
    command_file_paths,
    env_fingerprint,
    fingerprint,
    gate_cache_root,
    hashed_cache_path,
    select_repo_files,
    sha256_text,
    write_atomic_json,
)

TOOL_VERSION = "fast-gate-v1"
FORBIDDEN_CACHE_MARKERS = [
    "curl ",
    "wget ",
    "http://",
    "https://",
    "ping ",
    "ssh ",
    "telnet",
    "nc ",
    "netcat",
    "git fetch",
    "git pull",
    "live-test",
    "daemon",
    "secret",
    "token",
]


def parse_args(argv: list[str]) -> tuple[str, list[str], str, bool]:
    if not argv:
        raise SystemExit("usage: fast-gate.sh focused -- <command> | compile | full | no-cache -- <command>")
    mode = argv[0]
    rest = argv[1:]
    expect = ""
    cache_enabled = True
    if mode in {"focused", "no-cache"}:
        if rest[:1] and rest[0] == "--expect":
            if len(rest) < 3:
                raise SystemExit("missing value for --expect")
            expect = rest[1]
            rest = rest[2:]
        if not rest or rest[0] != "--":
            raise SystemExit("focused/no-cache requires -- before the command")
        command = rest[1:]
        if not command:
            raise SystemExit("missing command")
        cache_enabled = mode != "no-cache"
        return mode, command, expect, cache_enabled
    if mode in {"compile", "full"}:
        return mode, [], mode, True
    raise SystemExit("unknown mode: " + mode)


def build_lane_command(mode: str) -> tuple[list[list[str]], list[Path], list[str]]:
    if mode == "compile":
        command = ["bash", str(REPO_ROOT / "scripts/atropos-fast-gate.sh"), "classes"]
        inputs = select_repo_files(
            [
                "build.gradle.kts",
                "gradle/wrapper/gradle-wrapper.properties",
                "gradlew",
                "src/main/kotlin/**/*.kt",
                "scripts/atropos-fast-gate.sh",
                "scripts/atropos-smoke-cli.sh",
            ]
        )
        return [command], inputs, ["FAST_CLASSES_OK"]
    if mode == "full":
        command = ["bash", str(REPO_ROOT / "scripts/atropos-verify-worktree.sh")]
        inputs = select_repo_files(
            [
                "build.gradle.kts",
                "gradle/wrapper/gradle-wrapper.properties",
                "gradlew",
                "src/main/kotlin/**/*.kt",
                "src/test/kotlin/**/*.kt",
                "scripts/atropos-fast-gate.sh",
                "scripts/atropos-smoke-cli.sh",
                "scripts/atropos-verify-worktree.sh",
            ]
        )
        return [command], inputs, ["ATROPOS_WORKTREE_VERIFY_OK"]
    raise SystemExit(f"unsupported lane: {mode}")


def is_cacheable(command: list[str]) -> bool:
    text = " ".join(command).lower()
    return not any(marker in text for marker in FORBIDDEN_CACHE_MARKERS)


def run_command(command: list[str]) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        command,
        cwd=REPO_ROOT,
        text=True,
        capture_output=True,
    )


def replay(result: dict) -> int:
    if result.get("stdout"):
        sys.stdout.write(result["stdout"])
    if result.get("stderr"):
        sys.stderr.write(result["stderr"])
    return int(result["exit_code"])


def main(argv: list[str]) -> int:
    mode, command, expected, cache_enabled = parse_args(argv)
    if mode in {"compile", "full"}:
        steps, inputs, invariants = build_lane_command(mode)
        lane_name = mode
        command_repr = steps
    else:
        steps = [command]
        inputs = command_file_paths(command)
        invariants = [expected] if expected else []
        lane_name = "focused"
        command_repr = command

    build_fp = combined_file_fingerprint(inputs)
    env_fp = env_fingerprint()
    key_payload = {
        "tool_version": TOOL_VERSION,
        "mode": mode,
        "lane": lane_name,
        "command": command_repr,
        "cwd": str(REPO_ROOT),
        "build_inputs": build_fp,
        "environment": env_fp,
        "expected": invariants,
    }
    cache_key = fingerprint(key_payload)
    cache_root = gate_cache_root()
    cache_path = hashed_cache_path(cache_root, "commands", cache_key, ".json")

    cacheable = cache_enabled and all(is_cacheable(step) for step in steps)
    if cacheable and cache_path.exists():
        cached = json.loads(cache_path.read_text("utf-8"))
        print(f"FAST_GATE_CACHE_HIT key={cache_key}", file=sys.stderr)
        return replay(cached)

    print(f"FAST_GATE_CACHE_MISS key={cache_key}", file=sys.stderr)

    combined_stdout = []
    combined_stderr = []
    exit_code = 0
    for step in steps:
        result = run_command(step)
        combined_stdout.append(result.stdout)
        combined_stderr.append(result.stderr)
        if result.returncode != 0:
            exit_code = result.returncode
            break
    stdout = "".join(combined_stdout)
    stderr = "".join(combined_stderr)
    if stdout:
        sys.stdout.write(stdout)
    if stderr:
        sys.stderr.write(stderr)

    if cacheable:
        write_atomic_json(
            cache_path,
            {
                "tool_version": TOOL_VERSION,
                "mode": mode,
                "lane": lane_name,
                "command": command_repr,
                "cwd": str(REPO_ROOT),
                "build_inputs": build_fp,
                "environment": env_fp,
                "expected": invariants,
                "exit_code": exit_code,
                "stdout": stdout,
                "stderr": stderr,
                "cache_key": cache_key,
            },
        )
    return exit_code


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
PY
