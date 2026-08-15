#!/usr/bin/env python3
"""Lists production Kotlin files that nothing else calls.

A file is ORPHANED when no other file under src/main/kotlin references any
symbol it declares. It exists, it compiles, it usually has a test, and it never
executes -- so it counts toward the line total and does nothing.

Run from the repository root:

    python3 scripts/find-orphans.py

Exits 0 always; the count is the output, not the status. Tests are excluded on
purpose: a test calling a class is what makes it look alive in a coverage
report while no shipped code path can reach it, which is the exact illusion
this script exists to strip away.
"""

import os
import re
import sys

ROOT = "src/main/kotlin"

# Top-level declarations only -- an indented line is a member, and a member
# name matching somewhere else says nothing about whether the file is reachable.
DECL = re.compile(
    r"^(?:@\w+(?:\([^)]*\))?\s*)*"
    r"(?:public\s+|internal\s+|sealed\s+|abstract\s+|open\s+|data\s+|value\s+|"
    r"annotation\s+|inline\s+|suspend\s+)*"
    r"(?:class|object|interface|enum\s+class|fun|val|const\s+val|typealias)\s+"
    r"([A-Za-z_][A-Za-z0-9_]*)"
)


def main() -> int:
    if not os.path.isdir(ROOT):
        print(f"{ROOT} not found -- run from the repository root", file=sys.stderr)
        return 2

    files = [
        os.path.join(d, f)
        for d, _, fs in os.walk(ROOT)
        for f in fs
        if f.endswith(".kt")
    ]
    source = {p: open(p, encoding="utf-8", errors="replace").read() for p in files}

    declared = {}
    for path, text in source.items():
        names = set()
        for line in text.splitlines():
            if line.startswith((" ", "\t")):
                continue
            match = DECL.match(line)
            if match:
                names.add(match.group(1))
        declared[path] = names

    # Whole-word tokens rather than substring search: `Gc` must not count as a
    # reference to `GcSweeper`, and a comment mentioning a name is a reference
    # for this purpose only if it is spelled exactly.
    tokens = {
        p: set(re.findall(r"\b[A-Za-z_][A-Za-z0-9_]*\b", t)) for p, t in source.items()
    }

    orphans = []
    for path, names in declared.items():
        if not names:
            continue
        if any(names & tokens[other] for other in files if other != path):
            continue
        orphans.append((path, len(source[path].splitlines()), sorted(names)))

    orphans.sort(key=lambda row: -row[1])
    for path, loc, names in orphans:
        print(f"{loc:5d}  {path}  :: {','.join(names)}")

    print()
    print(f"{len(orphans)} orphaned of {len(files)} production files")
    print(f"{sum(row[1] for row in orphans)} orphan LOC")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
