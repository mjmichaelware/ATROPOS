---
name: atropos-dloi-ast-verifier
description: Use when building DLOI maps, exact-source addressing, or AST impact verification for changed files and symbols.
---

# Atropos DLOI AST Verifier

## Procedure

1. Read `references/dloi-ast.md`.
2. Resolve the task to exact source coordinates before touching files.
3. Derive the impacted files and symbols from the exact change slice.
4. Verify the result deterministically before model review or broader context packing.

## Guardrails

- Do not ingest the whole corpus when a coordinate lookup is enough.
- Do not guess impacted symbols from narrative descriptions alone.
- Use the DLOI map and AST graph to bound edits, not to expand them.
