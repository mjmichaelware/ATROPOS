from __future__ import annotations

import ast
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]

SECRET_PATTERNS = [
    re.compile(r'-----BEGIN (RSA |EC )?PRIVATE KEY-----', re.IGNORECASE),
    re.compile(r'(ghp|gho|ghu|ghs|ghr)_[A-Za-z0-9_]{36,}'),
    re.compile(r'sk_live_[0-9a-zA-Z]+'),
    re.compile(r'AKIA[0-9A-Z]{16}'),
    re.compile(r'service_account.*"private_key"', re.IGNORECASE),
]

SKIP_DIRS = {
    ".git",
    "node_modules",
    "__pycache__",
    ".specgraph",
    ".vercel",
    ".next",
}

SKIP_PATTERNS = [
    re.compile(r'.*\.png$'),
    re.compile(r'.*\.svg$'),
    re.compile(r'.*\.ico$'),
    re.compile(r'.*\.woff2?$'),
    re.compile(r'.*\.lock$'),
    re.compile(r'package-lock\.json$'),
    re.compile(r'.*\.sqlite3?$'),
    re.compile(r'supabase/\.temp/'),
]


def should_skip(path: Path) -> bool:
    for part in path.parts:
        if part in SKIP_DIRS:
            return True
    for pattern in SKIP_PATTERNS:
        if pattern.match(str(path)):
            return True
    return False


def check_file(path: Path) -> list[str]:
    problems: list[str] = []
    try:
        content = path.read_bytes()
        # Only check text files
        try:
            text = content.decode("utf-8")
        except (UnicodeDecodeError, UnicodeError):
            return problems

        for i, line in enumerate(text.split("\n"), 1):
            for pattern in SECRET_PATTERNS:
                if pattern.search(line):
                    rel = path.relative_to(ROOT)
                    problems.append(f"{rel}:{i}: potential secret matched {pattern.pattern[:40]}")
    except (OSError, PermissionError):
        pass
    return problems


def main() -> int:
    problems: list[str] = []
    # Check Python source files (excluding self)
    for path in sorted(ROOT.rglob("*.py")):
        if should_skip(path):
            continue
        if path.name == "check_secrets.py":
            continue
        problems.extend(check_file(path))

    # Check workflow files
    for path in sorted((ROOT / ".github" / "workflows").rglob("*.yml")):
        if should_skip(path):
            continue
        problems.extend(check_file(path))

    # Check shell scripts
    for path in sorted(ROOT.glob("scripts/*.sh")):
        if should_skip(path):
            continue
        problems.extend(check_file(path))

    if problems:
        print("SECRET CHECK FAILED")
        for problem in problems:
            print(f"  - {problem}")
        return 1

    print("SECRET CHECK PASSED")
    print("  - No secrets detected in source files")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
