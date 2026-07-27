from __future__ import annotations

import os
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def check_rollback_workflow() -> list[str]:
    problems: list[str] = []
    path = ROOT / ".github" / "workflows" / "rollback.yml"
    if not path.is_file():
        return ["rollback.yml: missing"]
    content = path.read_text()
    required_patterns = [
        ("validation", "validate"),
        ("dry-run", "dry-run"),
        ("Cloud Run API", "rollback-api"),
        ("Cloud Run Worker", "rollback-worker"),
        ("Vercel", "rollback-vercel"),
        ("revision verification", "Verify target revision"),
        ("health verification", "Verify rollback"),
    ]
    for name, pattern in required_patterns:
        if pattern not in content:
            problems.append(f"rollback.yml: missing {name} ({pattern!r})")
    return problems


def check_rollback_script() -> list[str]:
    problems: list[str] = []
    path = ROOT / "scripts" / "rollback.sh"
    if not path.is_file():
        return ["scripts/rollback.sh: missing"]
    if not os.access(str(path), os.X_OK):
        problems.append("scripts/rollback.sh: not executable")
    content = path.read_text()
    if "--dry-run" not in content:
        problems.append("scripts/rollback.sh: missing --dry-run support")
    if "latest" in content and '"latest"' not in content:
        pass
    if "REVISION" not in content:
        problems.append("scripts/rollback.sh: missing REVISION handling")
    return problems


def check_refusal_paths() -> list[str]:
    problems: list[str] = []
    path = ROOT / ".github" / "workflows" / "rollback.yml"
    if path.is_file():
        content = path.read_text()
        refusal_patterns = [
            ("empty revision", "if [ -z \"$REVISION\""),
            ("latest rejection", "'latest'"),
            ("HEAD rejection", "'HEAD'"),
            ("invalid target", "target must be"),
            ("invalid environment", "environment must be"),
        ]
        for name, pattern in refusal_patterns:
            if pattern not in content:
                problems.append(f"rollback.yml: missing refusal for {name}")
    return problems


def main() -> int:
    problems = (
        check_rollback_workflow()
        + check_rollback_script()
        + check_refusal_paths()
    )

    if problems:
        print("ROLLBACK CHECK FAILED")
        for problem in problems:
            print(f"  - {problem}")
        return 1

    print("ROLLBACK CHECK PASSED")
    print("  - rollback.yml: valid")
    print("  - scripts/rollback.sh: valid")
    print("  - Refusal paths: covered")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
