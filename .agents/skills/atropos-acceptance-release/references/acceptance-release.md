# Acceptance and Release Reference

## Source anchors

- `97cff09c0f362337__ATROPOS CODEX-CLI BUILD BLUEPRINT OVER TIME.txt`
  - Phase 11 self-build loop: `[S0013]` lines 46-48
  - Phase 19 app factory: `[S0021]` lines 70-72
  - Phase 20 autonomy: `[S0022]` lines 73-84
- `4995ec5b478cae18__README.md`
  - strict hierarchy, route truth, and batch discipline around lines 3-9 and 66-78

## Required checks

```bash
python3 scripts/codex/instruction-audit.sh
python3 scripts/codex/fast-gate.sh full
git diff --check
python3 scripts/codex/source-query.py phase 11 --limit 3
python3 scripts/codex/source-query.py phase 19 --limit 3
python3 scripts/codex/source-query.py phase 20 --limit 3
```

## Acceptance rule

- Truthful acceptance means the selected gate passes, the diff is scoped, secrets are absent, and `E(DELTA)=0` holds for the intended slice.
- Release proof is not a promise; it is a verified artifact or verified operating state.
