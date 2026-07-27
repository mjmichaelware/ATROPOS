# Hierarchy 12-16 Reference

## Source anchors

- `97cff09c0f362337__ATROPOS CODEX-CLI BUILD BLUEPRINT OVER TIME.txt`
  - Phase 12 Director advisory mode: `[S0014]` lines 49-51
  - Phase 13 territory enforcement: `[S0015]` lines 52-54
  - Phase 14 HR Router: `[S0016]` lines 55-57
  - Phase 15 Auditor and Custodian: `[S0017]` lines 58-60
  - Phase 16 Manager/Specialist/Worker hierarchy: `[S0018]` lines 61-63
- `daa37f228230881b__ATROPOS_Research_Hierarchy_Critique_v1.3.docx`
  - hierarchy levels and core responsibilities: `[S0009]` lines 23-32
  - territory assignment: `[S0010]` lines 33-34
  - HR Router: `[S0011]` lines 35-36
  - mapping to ATROPOS architecture: `[S0012]` lines 37-38
  - implementation order and final integration: `[S0014]` lines 40-40 and `[S0040]` lines 99-99
  - Director detail: `[S0028]` lines 76-77
  - Manager detail: `[S0029]` lines 78-79
  - Specialist vs Worker detail: `[S0030]` lines 80-81
  - Custodian detail: `[S0031]` lines 82-83
  - Auditor detail: `[S0032]` lines 84-85
  - HR Router detail: `[S0033]` lines 86-87
  - CommandRouter integration: `[S0034]` lines 88-89

## Query commands

```bash
python3 scripts/codex/source-query.py phase 12 --limit 4
python3 scripts/codex/source-query.py phase 13 --limit 4
python3 scripts/codex/source-query.py phase 14 --limit 4
python3 scripts/codex/source-query.py phase 15 --limit 4
python3 scripts/codex/source-query.py phase 16 --limit 4
python3 scripts/codex/source-query.py search Director territory HR Router Auditor Custodian Manager Specialist Worker --source-id daa37f228230881b --limit 12
```

## Hierarchy rule

- Advisory first, enforcement second.
- Territory is recorded at dispatch time.
- Cross-boundary requests go through the router.
- Verification remains independent from execution.
