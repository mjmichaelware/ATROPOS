---
name: atropos-source-authority
description: Use when indexing, querying, or selecting ATROPOS source authority, exact coordinates, deduped inventories, or authority precedence for context packs and resume handoffs.
---

# Atropos Source Authority

## Procedure

1. Read `references/source-authority.md` before building context from the corpus.
2. Refresh the index with `python3 scripts/codex/source-index.py --strict` after source changes.
3. Use `python3 scripts/codex/source-query.py` or `python3 scripts/codex/source-section.py` for exact sections, never whole-corpus prompts.
4. Build context packs only from selected authority, current contracts, changed files, impacted symbols, and bounded failure evidence.

## Guardrails

- Preserve original bytes in `.atropos/source-authority/original/`.
- Treat the generated index as a deterministic cache, not a replacement for the manifest.
- Mark duplicate hashes and superseded versions, but do not delete either from source authority.
