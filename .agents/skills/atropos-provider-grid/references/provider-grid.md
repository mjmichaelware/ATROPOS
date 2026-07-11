# Provider Grid Reference

## Source anchors

- `4995ec5b478cae18__README.md`
  - What ATROPOS actually is right now: lines 64-81
  - Full provider registry: lines 82-123
  - Current capabilities summary: lines 231-247
  - 20-phase blueprint overview: lines 248-263
- `04c393b5f0ec1017__ATROPOS_UX_PASS5_PROVIDER_DOCTOR_CONTEXT_20260628_202907.txt`
  - RoutePolicy and provider truth sections around lines 678-700
  - Quota record and paid-locked context around lines 704-716 and 1526/1699

## Query commands

```bash
python3 scripts/codex/source-query.py search free-first RoutePolicy paid locked paid emergency --source-id 4995ec5b478cae18 --source-id 04c393b5f0ec1017 --limit 12
python3 scripts/codex/source-query.py search provider doctor route policy --source-id 04c393b5f0ec1017 --limit 12
python3 scripts/codex/source-query.py phase 1 --limit 5
python3 scripts/codex/source-query.py phase 3 --limit 5
```

## Route law

- Prefer the local toolchain first.
- Prefer free ready providers before paid providers.
- Require explicit unlock for paid emergencies.
- Show skipped-provider reasons and cooldown state.

## State distinctions

- Configured means the provider can be contacted.
- Verified means the provider or route has passed a truth check.
- Ready means configured, verified, allowed, not cooling down, and not locked.
