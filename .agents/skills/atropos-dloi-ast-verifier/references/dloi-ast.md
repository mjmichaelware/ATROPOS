# DLOI and AST Reference

## Source anchors

- `800a9d30b0c183b6__ATROPOS_SOURCE_DOCUMENT_MAP_UPDATED_DLOI.md`
  - map purpose and address-only routing around lines 1-12
  - DLOI band legend around lines 19-21
  - quick routing reference around line 81
- `13a085184bfc3741__ATROPOS_00_MASTER_ADDRESS_MAP-1.md`
  - true DLOI coordinates and "address never ingest" around lines 4-6
  - Android Kotlin runtime / asynchronous sockets anchor around line 8
  - coordinate format around line 16
  - corpus-to-DLOI mapping around lines 58-75
  - companion docs and routing protocol around lines 93-106
- `fbf3e1d68c6f141b__ATROPOS_00_MASTER_ADDRESS_MAP.md`
  - role, coordinate, and content-hash framing around lines 3, 16-17, 23-31
  - companion docs around lines 91-97
  - headings used for category/leaf around lines 105-106

## Query commands

```bash
python3 scripts/codex/source-query.py search DLOI address never ingest --source-id 800a9d30b0c183b6 --source-id 13a085184bfc3741 --source-id fbf3e1d68c6f141b --limit 12
python3 scripts/codex/source-query.py search AST symbol graph package path --limit 12
python3 scripts/codex/source-query.py source 13a085184bfc3741 --limit 12
```

## AST rule

- Identify the impacted files and symbols before editing.
- Use exact source coordinates when deciding what is in scope.
- Keep the edit slice narrow enough that deterministic verification can decide it.
