#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PYTHONPATH="$script_dir${PYTHONPATH:+:$PYTHONPATH}" \
exec python3 - "$@" <<'PY'
from __future__ import annotations

import json
import re
import subprocess
import sys
import tempfile
from pathlib import Path

from _lib import HANDOFF_ROOT, REPO_ROOT, ensure_dir, load_index, sha256_file, write_atomic_json


SKILL_ROOT = REPO_ROOT / ".agents/skills"
AGENTS_PATH = REPO_ROOT / "AGENTS.md"
OVERRIDE_PATH = REPO_ROOT / "ops/codex/AGENTS.override.md"


def run(cmd: list[str], check: bool = True) -> subprocess.CompletedProcess[str]:
    proc = subprocess.run(cmd, cwd=REPO_ROOT, text=True, capture_output=True)
    if check and proc.returncode != 0:
        raise RuntimeError(f"command failed: {' '.join(cmd)}\n{proc.stdout}\n{proc.stderr}")
    return proc


def extract_frontmatter(path: Path) -> dict[str, str]:
    lines = path.read_text("utf-8").splitlines()
    if not lines or lines[0].strip() != "---":
        raise ValueError(f"missing frontmatter: {path}")
    out: dict[str, str] = {}
    for line in lines[1:]:
        if line.strip() == "---":
            break
        if ":" not in line:
            continue
        key, value = line.split(":", 1)
        out[key.strip()] = value.strip()
    return out


def parse_key_from_stderr(stderr: str) -> str:
    m = re.search(r"key=([0-9a-f]{16,64})", stderr)
    if not m:
        raise ValueError(f"missing cache key in stderr: {stderr}")
    return m.group(1)


def source_query_json(*args: str) -> dict:
    proc = run([sys.executable, str(REPO_ROOT / "scripts/codex/source-query.py"), "--json", *args])
    return json.loads(proc.stdout)


def check_sources(index: dict) -> list[dict]:
    results = []
    for source in index["sources"]:
        original = Path(source["original_path"])
        if not original.exists():
            raise AssertionError(f"missing original source: {original}")
        if sha256_file(original) != source["sha256"]:
            raise AssertionError(f"source hash mismatch: {original}")
        if original.stat().st_size != source["size_bytes"]:
            raise AssertionError(f"source size mismatch: {original}")
        normalized = Path(source["normalized_path"])
        text = normalized.read_text("utf-8")
        if source["sha256"] == "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855":
            results.append({"source_id": source["source_id"], "status": "empty-ok"})
            continue
        if not text.strip():
            raise AssertionError(f"empty normalized text: {source['original_filename']}")
        for section in source["sections"]:
            if section["start_line"] <= 0 or section["end_line"] < section["start_line"]:
                raise AssertionError(f"bad line span in {source['source_id']} {section['section_id']}")
            if section.get("start_page") is None and section.get("start_paragraph") is None:
                raise AssertionError(f"missing coordinates in {source['source_id']} {section['section_id']}")
        results.append({"source_id": source["source_id"], "status": "ok", "sections": source["section_count"]})
    return results


def check_phases() -> dict:
    expectations = {
        1: "Provider Activation Doctor",
        11: "Self-Build Loop",
        12: "Director Advisory Mode",
        19: "App Factory Completion",
        20: "Full Autonomous ATROPOS",
    }
    results = {}
    for phase, phrase in expectations.items():
        payload = source_query_json("phase", str(phase), "--limit", "4")
        matches = payload["results"]
        if not matches:
            raise AssertionError(f"no results for phase {phase}")
        text = json.dumps(matches, ensure_ascii=False)
        if phrase not in text:
            raise AssertionError(f"phase {phase} did not include {phrase}")
        results[str(phase)] = matches[0]["section_id"]
    return results


def check_hierarchy() -> list[str]:
    required = ["Director", "territory", "HR Router", "Auditor", "Custodian", "Manager", "Specialist", "Worker"]
    payload = source_query_json("search", "Director", "territory", "HR", "Router", "Auditor", "Custodian", "Manager", "Specialist", "Worker", "--limit", "24")
    text = json.dumps(payload["results"], ensure_ascii=False)
    missing = [term for term in required if term not in text]
    if missing:
        raise AssertionError(f"hierarchy terms missing: {missing}")
    return required


def check_provider_query() -> list[str]:
    payload = source_query_json("search", "free-first", "route law", "paid locked", "paid emergency", "RoutePolicy", "--limit", "16")
    text = json.dumps(payload["results"], ensure_ascii=False)
    required = ["free-first", "RoutePolicy", "paid locked", "paid emergency", "route law"]
    missing = [term for term in required if term not in text]
    if missing:
        raise AssertionError(f"provider terms missing: {missing}")
    return required


def check_skill_frontmatter() -> dict:
    names = []
    descriptions = []
    details = []
    for skill in sorted(SKILL_ROOT.glob("*/SKILL.md")):
        meta = extract_frontmatter(skill)
        if "name" not in meta or "description" not in meta:
            raise AssertionError(f"frontmatter missing fields: {skill}")
        if not meta["name"] or not meta["description"]:
            raise AssertionError(f"empty frontmatter field: {skill}")
        names.append(meta["name"])
        descriptions.append(meta["description"])
        details.append({"file": str(skill), "name": meta["name"], "description": meta["description"]})
    duplicate_names = sorted({name for name in names if names.count(name) > 1})
    if duplicate_names:
        raise AssertionError(f"duplicate skill names: {duplicate_names}")
    return {"count": len(details), "skills": details}


def check_gate_cache_distinction() -> dict:
    proc_a = run(["bash", str(REPO_ROOT / "scripts/codex/fast-gate.sh"), "focused", "--", sys.executable, "-c", "print('alpha')"], check=False)
    proc_b = run(["bash", str(REPO_ROOT / "scripts/codex/fast-gate.sh"), "focused", "--", sys.executable, "-c", "print('beta')"], check=False)
    key_a = parse_key_from_stderr(proc_a.stderr)
    key_b = parse_key_from_stderr(proc_b.stderr)
    if key_a == key_b:
        raise AssertionError("gate cache did not distinguish different commands")
    return {"alpha": key_a, "beta": key_b}


def check_gate_cache_invalidation() -> dict:
    tmp = REPO_ROOT / ".atropos/context-cache/instruction-audit-input.txt"
    tmp.parent.mkdir(parents=True, exist_ok=True)
    tmp.write_text("one\n", encoding="utf-8")
    proc_a = run(["bash", str(REPO_ROOT / "scripts/codex/fast-gate.sh"), "focused", "--", "cat", str(tmp)], check=False)
    key_a = parse_key_from_stderr(proc_a.stderr)
    tmp.write_text("two\n", encoding="utf-8")
    proc_b = run(["bash", str(REPO_ROOT / "scripts/codex/fast-gate.sh"), "focused", "--", "cat", str(tmp)], check=False)
    key_b = parse_key_from_stderr(proc_b.stderr)
    if key_a == key_b:
        raise AssertionError("gate cache key did not change after input mutation")
    return {"before": key_a, "after": key_b}


def check_resume_handoff() -> dict:
    proc = run(["bash", str(REPO_ROOT / "scripts/codex/resume-handoff.sh")])
    handoff_path = Path(proc.stdout.strip().splitlines()[-1])
    text = handoff_path.read_text("utf-8")
    if "## Dirty tracked" not in text or "## Untracked" not in text:
        raise AssertionError("handoff missing dirty/untracked sections")
    if "src/main/" not in text:
        raise AssertionError("handoff did not capture tracked source dirt")
    if "scripts/codex/source-index.py" not in text and "AGENTS.md" not in text:
        raise AssertionError("handoff did not capture untracked bootstrap files")
    return {"handoff": str(handoff_path)}


def check_diff_check() -> dict:
    proc = run(["git", "diff", "--check"], check=False)
    return {"exit_code": proc.returncode, "stdout": proc.stdout, "stderr": proc.stderr}


def check_source_dir_dirty() -> dict:
    proc = run(["git", "diff", "--name-only", "--", "src/main", "src/test"], check=False)
    tracked = [line for line in proc.stdout.splitlines() if line.strip()]
    proc_untracked = run(["git", "ls-files", "--others", "--exclude-standard", "src/main", "src/test"], check=False)
    untracked = [line for line in proc_untracked.stdout.splitlines() if line.strip()]
    return {"tracked": tracked, "untracked": untracked}


def scan_for_secrets() -> dict:
    patterns = [
        re.compile(r"sk-[A-Za-z0-9]{16,}"),
        re.compile(r"gh[pousr]_[A-Za-z0-9]{16,}"),
        re.compile(r"AKIA[0-9A-Z]{16}"),
        re.compile(r"AIza[0-9A-Za-z\-_]{20,}"),
        re.compile(r"-----BEGIN [A-Z ]+ PRIVATE KEY-----"),
        re.compile(r"xox[baprs]-[A-Za-z0-9-]{10,}"),
    ]
    files = [
        REPO_ROOT / "AGENTS.md",
        REPO_ROOT / "ops/codex/AGENTS.override.md",
    ]
    files.extend(sorted((REPO_ROOT / ".agents/skills").glob("*/SKILL.md")))
    files.extend(sorted((REPO_ROOT / "scripts/codex").glob("*")))
    files.extend(sorted((REPO_ROOT / "docs").glob("ATROPOS_CODEX*.md")))
    hits = []
    for path in files:
        if not path.exists() or not path.is_file():
            continue
        text = path.read_text("utf-8", errors="replace")
        for pattern in patterns:
            if pattern.search(text):
                hits.append({"file": str(path), "pattern": pattern.pattern})
    if hits:
        raise AssertionError(f"possible secrets found: {hits}")
    return {"files_scanned": len(files)}


def main() -> int:
    report = {"checks": []}
    warnings = []

    index = load_index()
    report["source_index_output"] = "reused existing index"

    source_checks = check_sources(index)
    report["source_checks"] = source_checks
    report["phase_checks"] = check_phases()
    report["hierarchy_checks"] = check_hierarchy()
    report["provider_checks"] = check_provider_query()
    report["skill_frontmatter"] = check_skill_frontmatter()
    report["gate_cache_distinction"] = check_gate_cache_distinction()
    report["gate_cache_invalidation"] = check_gate_cache_invalidation()
    report["resume_handoff"] = check_resume_handoff()
    diff_check = check_diff_check()
    report["diff_check"] = diff_check
    source_dir_dirty = check_source_dir_dirty()
    report["source_dir_dirty"] = source_dir_dirty
    report["secret_scan"] = scan_for_secrets()

    if diff_check["exit_code"] != 0:
        warnings.append("git diff --check reported existing worktree issues")
    if source_dir_dirty["tracked"] or source_dir_dirty["untracked"]:
        warnings.append("src/main or src/test remain dirty from preexisting work")

    report["warnings"] = warnings
    report_path = REPO_ROOT / ".atropos/context-cache/instruction-audit.json"
    ensure_dir(report_path.parent)
    write_atomic_json(report_path, report)

    print("INSTRUCTION_AUDIT_OK")
    print(f"REPORT {report_path}")
    print(f"WARNINGS {len(warnings)}")
    for warning in warnings:
        print(f"WARNING {warning}")
    print(f"PHASES {report['phase_checks']}")
    print(f"SKILLS {report['skill_frontmatter']['count']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
PY
