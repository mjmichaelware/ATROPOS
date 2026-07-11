# Foundation 1-11 Reference

## Source anchors

- `97cff09c0f362337__ATROPOS CODEX-CLI BUILD BLUEPRINT OVER TIME.txt`
  - Phase 1: `[S0003]` lines 16-18
  - Phase 11: `[S0013]` lines 46-48
  - Phase 12 boundary: `[S0014]` lines 49-51
- `b88acd252ce5100c__ATROPOS Source Doc. 2.txt`
  - Compile cadence and batch workflow around line 1203
  - Next-phase workflow contract around lines 3019-3023

## Query commands

```bash
python3 scripts/codex/source-query.py phase 1 --limit 5
python3 scripts/codex/source-query.py phase 11 --limit 5
python3 scripts/codex/source-query.py phase 12 --limit 5
python3 scripts/codex/source-query.py search provider activation quota memory self-build --limit 12
```

## Phase 11 rule

Treat phase 11 as the bounded self-build loop:

- audit the contract
- edit one coherent slice
- run a focused deterministic test
- compile after the slice
- repair the actual failure
- keep `E(DELTA)=0`
