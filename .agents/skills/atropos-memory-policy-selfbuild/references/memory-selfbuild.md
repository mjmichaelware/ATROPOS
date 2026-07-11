# Memory and Self-Build Reference

## Source anchors

- `97cff09c0f362337__ATROPOS CODEX-CLI BUILD BLUEPRINT OVER TIME.txt`
  - Phase 9 persistent memory: `[S0011]` lines 40-42
  - Phase 11 self-build loop: `[S0013]` lines 46-48
  - Phase 20 autonomous self-improvement: `[S0022]` lines 73-84
- `18d4116ff7fa0295__ATROPOS_README.md`
  - Phase 11 already enables bounded batches with `E(DELTA)=0`: lines 265-266
  - Phase 20 target: lines 266-271
- `b88acd252ce5100c__ATROPOS Source Doc. 2.txt`
  - persistent batch workflow and source authority around lines 1203 and 3019-3023

## Query commands

```bash
python3 scripts/codex/source-query.py phase 9 --limit 5
python3 scripts/codex/source-query.py phase 11 --limit 5
python3 scripts/codex/source-query.py phase 20 --limit 5
python3 scripts/codex/source-query.py search persistent memory self-build compaction resume --limit 12
```

## Memory rule

- Persist task state, route outcomes, and repair signatures locally.
- Treat the resume handoff as the recovery source of truth.
- Compact, do not erase.

## Self-build rule

- Plan a coherent slice.
- Edit only the slice territory.
- Run a focused deterministic test.
- Compile after the slice.
- Repair the real failure.
- Accept only when the gate is green and `E(DELTA)=0` holds.
