---
name: atropos-foundation-1-11
description: Use when working on ATROPOS phases 0-11, provider activation, route truth, memory, or self-build loop foundations without touching active product code.
---

# Atropos Foundation 1-11

## Procedure

1. Read `references/foundation-1-11.md`.
2. Anchor the slice to phases 0-11 before editing anything.
3. Keep changes out of the active Kotlin implementation unless the task explicitly targets that code.
4. Use deterministic checks first, then compile only after a coherent slice.

## Guardrails

- Phase 11 is the first self-build gate, not a license to mix unrelated work.
- Phase 12 is the boundary where hierarchy begins; do not fold it into phase 11 work.
- Provider activation, quota truth, secret hardening, DLOI, AST, and memory work should stay bounded to the requested slice.
