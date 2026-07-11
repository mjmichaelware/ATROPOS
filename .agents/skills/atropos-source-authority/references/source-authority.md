# Source Authority Reference

## Canonical query commands

- `python3 scripts/codex/source-query.py phase 1 --limit 3`
- `python3 scripts/codex/source-query.py phase 11 --limit 3`
- `python3 scripts/codex/source-query.py phase 12 --limit 3`
- `python3 scripts/codex/source-query.py phase 19 --limit 3`
- `python3 scripts/codex/source-query.py phase 20 --limit 3`
- `python3 scripts/codex/source-query.py search Director territory HR Router Auditor Custodian Manager Specialist Worker --source-id daa37f228230881b --limit 12`
- `python3 scripts/codex/source-query.py search free-first RoutePolicy paid locked paid emergency --source-id 4995ec5b478cae18 --source-id 04c393b5f0ec1017 --limit 12`
- `python3 scripts/codex/source-query.py search DLOI address never ingest --source-id 800a9d30b0c183b6 --source-id 13a085184bfc3741 --source-id fbf3e1d68c6f141b --limit 12`

## Indexed coordinates

- Blueprint phase anchors live in `97cff09c0f362337__ATROPOS CODEX-CLI BUILD BLUEPRINT OVER TIME.txt`
  - Phase 1: `[S0003]` lines 16-18
  - Phase 11: `[S0013]` lines 46-48
  - Phase 12: `[S0014]` lines 49-51
  - Phase 19: `[S0021]` lines 70-72
  - Phase 20: `[S0022]` lines 73-84
- Source Doc 2 carries the batch cadence and authority contract around lines 1203 and 3019-3023.
- Hierarchy research anchors live in `daa37f228230881b__ATROPOS_Research_Hierarchy_Critique_v1.3.docx`
  - Territory + Director + HR Router + Custodian/Auditor/Manager/Worker: `[S0014]` lines 40-40
  - Director/territory/HR Router detail: `[S0010]` lines 33-34
  - HR Router detail: `[S0011]` lines 35-36
- Provider and route truth anchors live in `4995ec5b478cae18__README.md` and `04c393b5f0ec1017__ATROPOS_UX_PASS5_PROVIDER_DOCTOR_CONTEXT_20260628_202907.txt`
  - Free-first routing and provider registry: lines 64-81 and 82-123 in the README
  - RoutePolicy and paid-lock context: the provider doctor context sections around lines 678-700 and 1526/1699
- DLOI anchors live in `800a9d30b0c183b6__ATROPOS_SOURCE_DOCUMENT_MAP_UPDATED_DLOI.md` and `13a085184bfc3741__ATROPOS_00_MASTER_ADDRESS_MAP-1.md`

## Inventory facts

- Sources: 88
- Sections: 3927
- Duplicate hashes: 0
- Superseded versions are recorded, not deleted
- Generated index root: `.atropos/context-cache/source-index/v1/`
