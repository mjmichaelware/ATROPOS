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
import subprocess
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


def code_only(text):
    """Remove comments and literal bodies without breaking Kotlin syntax.

    Regex-based string stripping is unsafe for Kotlin because escaped quotes
    can make the expression consume declarations that follow the string. The
    small scanner below preserves interpolation expressions, which are real
    production references (for example `${FactoryRequirementStatements...}`).
    """
    out = []
    i = 0
    n = len(text)
    while i < n:
        if text.startswith("//", i):
            end = text.find("\n", i)
            i = n if end < 0 else end
            out.append(" ")
            continue
        if text.startswith("/*", i):
            end = text.find("*/", i + 2)
            i = n if end < 0 else end + 2
            out.append(" ")
            continue
        if text.startswith('"""', i):
            end = text.find('"""', i + 3)
            body = text[i + 3:] if end < 0 else text[i + 3:end]
            out.append(" ")
            out.extend(re.findall(r"\$\{([^{}]+)\}|\$([A-Za-z_][A-Za-z0-9_]*)", body))
            i = n if end < 0 else end + 3
            continue
        if text[i] == '"':
            j = i + 1
            body = []
            while j < n:
                if text[j] == "\\":
                    j += 2
                    continue
                if text[j] == '"':
                    break
                body.append(text[j])
                j += 1
            out.append(" ")
            out.extend(re.findall(r"\$\{([^{}]+)\}|\$([A-Za-z_][A-Za-z0-9_]*)", "".join(body)))
            i = min(j + 1, n)
            continue
        if text[i] == "'":
            j = i + 1
            while j < n:
                if text[j] == "\\":
                    j += 2
                    continue
                if text[j] == "'":
                    j += 1
                    break
                j += 1
            out.append(" ")
            i = j
            continue
        out.append(text[i])
        i += 1
    # Interpolation extraction above returns tuples; flatten them so normal
    # tokenization sees each referenced identifier.
    return "".join(
        " ".join(part for part in item if part) if isinstance(item, tuple) else item
        for item in out
    )


def added_production_paths() -> set[str]:
    """Return newly added production source paths in the current revision."""
    base = os.environ.get("ATROPOS_ORPHAN_BASE", "HEAD^")
    result = subprocess.run(
        ["git", "diff", "--name-only", "--diff-filter=A", base, "HEAD", "--", "src/main"],
        check=False,
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        raise RuntimeError(f"could not determine added production paths: {result.stderr.strip()}")
    return {line.strip() for line in result.stdout.splitlines() if line.strip().endswith((".kt", ".java"))}


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
    code = {p: code_only(t) for p, t in source.items()}

    declared = {}
    for path, text in source.items():
        names = set()
        # Declaration discovery is line-oriented and must use the source line,
        # not the string-stripped stream. A Kotlin interpolation may contain
        # nested quoted expressions; that is irrelevant to a top-level
        # `object`/`class` declaration later in the file.
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
        p: set(re.findall(r"\b[A-Za-z_][A-Za-z0-9_]*\b", t)) for p, t in code.items()
    }
    token_files = {}
    for path, names in tokens.items():
        for name in names:
            token_files.setdefault(name, set()).add(path)

    orphans = []
    for path, names in declared.items():
        if not names:
            continue
        if any(token_files.get(name, set()) - {path} for name in names):
            continue
        orphans.append((path, len(source[path].splitlines()), sorted(names)))

    orphans.sort(key=lambda row: -row[1])
    for path, loc, names in orphans:
        print(f"{loc:5d}  {path}  :: {','.join(names)}")

    print()
    print(f"{len(orphans)} orphaned of {len(files)} production files")
    print(f"{sum(row[1] for row in orphans)} orphan LOC")
    if "--fail-on-new" in sys.argv:
        new_orphans = [row for row in orphans if row[0] in added_production_paths()]
        if new_orphans:
            print("new production orphan(s) detected; every added production file needs a caller", file=sys.stderr)
            return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
