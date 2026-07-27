# Autonomy 20 Reference

## Source anchors

- `97cff09c0f362337__ATROPOS CODEX-CLI BUILD BLUEPRINT OVER TIME.txt`
  - Phase 20 full autonomous ATROPOS: `[S0022]` lines 73-84
- `4995ec5b478cae18__README.md`
  - current capabilities summary and blueprint overview: lines 231-263
- `18d4116ff7fa0295__ATROPOS_README.md`
  - phase 20 target: lines 266-271

## Query commands

```bash
python3 scripts/codex/source-query.py phase 20 --limit 4
python3 scripts/codex/source-query.py search autonomous backlog scheduled doctor queued provider retries self-improvement scoring route optimization territory-governed self-build --limit 12
```

## Autonomy rule

- Self-improvement is allowed only inside policy and territory.
- Route optimization must remain explainable.
- The same deterministic gates used during construction remain the acceptance gates for autonomy work.
