# App Factory 19 Reference

## Source anchors

- `97cff09c0f362337__ATROPOS CODEX-CLI BUILD BLUEPRINT OVER TIME.txt`
  - Phase 19 app factory completion: `[S0021]` lines 70-72
- `18d4116ff7fa0295__ATROPOS_README.md`
  - phase 11 and phase 20 framing: lines 263-271

## Query commands

```bash
python3 scripts/codex/source-query.py phase 19 --limit 4
python3 scripts/codex/source-query.py search app spec parser DAG planner file generator compile test loop screenshot smoke artifact packager install run proof --limit 12
```

## Acceptance rule

- Prompt -> plan -> code -> verify -> artifact -> commit.
- Artifact acceptance requires install/run proof or equivalent smoke evidence.
