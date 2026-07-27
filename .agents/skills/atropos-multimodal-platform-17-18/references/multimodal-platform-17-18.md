# Multimodal and Platform Reference

## Source anchors

- `97cff09c0f362337__ATROPOS CODEX-CLI BUILD BLUEPRINT OVER TIME.txt`
  - Phase 17 multimodal runtime: `[S0019]` lines 64-66
  - Phase 18 multiplatform expansion: `[S0020]` lines 67-69
- `18d4116ff7fa0295__ATROPOS_README.md`
  - multiplatform future: lines 272-288

## Query commands

```bash
python3 scripts/codex/source-query.py phase 17 --limit 4
python3 scripts/codex/source-query.py phase 18 --limit 4
python3 scripts/codex/source-query.py search Compose Desktop Android shell Docker GraalVM Ktor backend --limit 12
```

## Batch split

- Phase 17: screenshot review, terminal snapshots, vision inspection, and multimodal evidence.
- Phase 18: core extraction, renderer abstraction, Compose Desktop, Android shell, Docker/GraalVM, and Ktor backend work.
