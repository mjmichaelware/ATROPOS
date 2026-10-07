from __future__ import annotations

import io
import re
import sys
import tokenize
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]

BOOLEAN_COLUMNS = (
    "enforce_acyclic",
    "inferred",
    "allow_open_research",
    "enabled",
    "allow_offline_degraded",
    "paid_emergency_enabled",
)

targets = sorted(
    (ROOT / "src" / "specgraph_foundry").glob("*.py")
) + sorted(
    (ROOT / "scripts").glob("*.py")
)

patterns: list[re.Pattern[str]] = []

for column in BOOLEAN_COLUMNS:
    identifier = (
        rf"(?:[A-Za-z_][A-Za-z0-9_]*\.)?"
        rf"{re.escape(column)}"
    )

    patterns.extend(
        [
            re.compile(
                rf"{identifier}\s*=\s*[01]\b"
            ),
            re.compile(
                rf"{identifier}\s*(?:!=|<>)\s*[01]\b"
            ),
            re.compile(
                rf"\b[01]\s*=\s*{identifier}"
            ),
        ]
    )

problems: list[str] = []

for path in targets:
    text = path.read_text(encoding="utf-8")

    tokens = tokenize.generate_tokens(
        io.StringIO(text).readline
    )

    for token in tokens:
        if token.type != tokenize.STRING:
            continue

        for pattern in patterns:
            match = pattern.search(token.string)

            if match is not None:
                problems.append(
                    (
                        f"{path.relative_to(ROOT)}:"
                        f"{token.start[0]}: "
                        f"{match.group(0)}"
                    )
                )

if problems:
    print(
        "POSTGRES BOOLEAN SQL CHECK FAILED",
        file=sys.stderr,
    )

    for problem in problems:
        print(
            f"- {problem}",
            file=sys.stderr,
        )

    raise SystemExit(1)

print(
    "POSTGRES BOOLEAN SQL CHECK PASSED"
)
