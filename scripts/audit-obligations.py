#!/usr/bin/env python3
"""Re-audits every obligation against the current tree.

The registry in docs/completion carries a `status` from whenever it was last
generated, and that status goes stale the moment anyone writes code: rows still
read NOT_WRITTEN for symbols that now exist and are wired. This re-derives the
three predicates from the tree itself, so a row's verdict is a measurement
rather than a memory.

  -impl  the declaring symbol exists in production source
  -wire  some other production file references it
  -edge  a test references it (semantics are asserted somewhere)

`-edge` is deliberately weak and labelled as such: a test existing is not proof
the behaviour is right, only that someone asserted something about it. A row
with no test cannot be claimed correct at all, which is the distinction worth
having.

Run from the repository root:  python3 scripts/audit-obligations.py
"""

import json
import os
import re
import sys
from collections import Counter, defaultdict

REGISTRY = "docs/completion/ATROPOS_CODE_OBLIGATION_REGISTRY.json"
MAIN = "src/main/kotlin"
TEST = "src/test/kotlin"
ANDROID_MAIN = "app/src/main"
ANDROID_TEST = "app/src/test"

DECL = re.compile(
    r"^(?:@\w+(?:\([^)]*\))?\s*)*"
    r"(?:public\s+|internal\s+|private\s+|sealed\s+|abstract\s+|open\s+|data\s+|value\s+|"
    r"annotation\s+|inline\s+|suspend\s+)*"
    r"(?:class|object|interface|enum\s+class|fun|val|const\s+val|typealias)\s+"
    r"([A-Za-z_][A-Za-z0-9_]*)"
)
WORD = re.compile(r"\b[A-Za-z_][A-Za-z0-9_]*\b")


def kotlin_files(*roots):
    out = []
    for root in roots:
        for d, _, fs in os.walk(root):
            out += [os.path.join(d, f) for f in fs if f.endswith(".kt")]
    return out


def index(paths):
    """symbol -> set of files declaring it, and file -> tokens referenced."""
    declared = defaultdict(set)
    tokens = {}
    for p in paths:
        text = open(p, encoding="utf-8", errors="replace").read()
        for line in text.splitlines():
            if line.startswith((" ", "\t")):
                continue
            m = DECL.match(line)
            if m:
                declared[m.group(1)].add(p)
        tokens[p] = set(WORD.findall(text))
    return declared, tokens


def main() -> int:
    if not os.path.isfile(REGISTRY):
        print(f"{REGISTRY} not found — run from the repository root", file=sys.stderr)
        return 2

    prod = kotlin_files(MAIN) + kotlin_files(ANDROID_MAIN)
    tests = kotlin_files(TEST) + kotlin_files(ANDROID_TEST)
    declared, prod_tokens = index(prod)
    _, test_tokens = index(tests)

    test_referenced = set()
    for toks in test_tokens.values():
        test_referenced |= toks

    def exists(sym):
        return sym in declared

    def wired(sym):
        homes = declared.get(sym)
        if not homes:
            return False
        return any(sym in toks for f, toks in prod_tokens.items() if f not in homes)

    registry = json.load(open(REGISTRY, encoding="utf-8"))
    rows = registry["obligations"]

    audited = []
    for row in rows:
        symbols = row.get("implementationEvidenceSymbols") or []
        paths = row.get("expectedPathsOrSymbols") or []
        kind = row.get("predicateKind")

        present = [s for s in symbols if exists(s)]
        missing = [s for s in symbols if not exists(s)]
        connected = [s for s in symbols if wired(s)]
        tested = [s for s in symbols if s in test_referenced]
        paths_present = [p for p in paths if os.path.exists(p)]

        if not symbols:
            verdict = "PRESENT" if paths_present else "ABSENT"
            detail = f"{len(paths_present)}/{len(paths)} declared paths present"
        elif kind == "implementation":
            verdict = "PRESENT" if not missing else ("PARTIAL" if present else "ABSENT")
            detail = f"{len(present)}/{len(symbols)} symbols declared"
        elif kind == "integration":
            if missing:
                verdict = "BLOCKED_BY_IMPL"
            elif len(connected) == len(symbols):
                verdict = "WIRED"
            elif connected:
                verdict = "PARTIAL"
            else:
                verdict = "ORPHANED"
            detail = f"{len(connected)}/{len(symbols)} symbols have a production caller"
        else:  # semantics
            if missing:
                verdict = "BLOCKED_BY_IMPL"
            elif len(tested) == len(symbols):
                verdict = "ASSERTED"
            elif tested:
                verdict = "PARTIAL"
            else:
                verdict = "UNASSERTED"
            detail = f"{len(tested)}/{len(symbols)} symbols referenced by a test"

        audited.append({
            "obligationId": row["obligationId"],
            "requirementId": row["requirementId"],
            "phase": row.get("phase"),
            "title": row.get("title"),
            "predicate": kind,
            "registryStatus": row.get("status"),
            "currentVerdict": verdict,
            "detail": detail,
            "symbols": symbols,
            "missingSymbols": missing,
            "unwiredSymbols": [s for s in symbols if exists(s) and not wired(s)],
            "untestedSymbols": [s for s in symbols if exists(s) and s not in test_referenced],
            "canonicalOwner": row.get("canonicalOwner"),
        })

    out = {
        "generatedAt": None,
        "productionFiles": len(prod),
        "testFiles": len(tests),
        "rows": len(audited),
        "byVerdict": dict(Counter(r["currentVerdict"] for r in audited)),
        "registryDrift": sum(
            1 for r in audited
            if r["registryStatus"] == "NOT_WRITTEN" and r["currentVerdict"] in ("PRESENT", "WIRED", "ASSERTED")
        ),
        "obligations": audited,
    }
    json.dump(out, open("docs/completion/ATROPOS_OBLIGATION_AUDIT.json", "w"), indent=1)

    print(f"production files {len(prod)}   test files {len(tests)}   rows {len(audited)}")
    for verdict, count in sorted(out["byVerdict"].items(), key=lambda kv: -kv[1]):
        print(f"  {verdict:18s} {count}")
    print(f"\nregistry rows marked NOT_WRITTEN that are now satisfied: {out['registryDrift']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
