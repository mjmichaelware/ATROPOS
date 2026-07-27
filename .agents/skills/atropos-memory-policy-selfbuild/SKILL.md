---
name: atropos-memory-policy-selfbuild
description: Use when working on persistent memory, resume state, or the self-build loop and its verification gates.
---

# Atropos Memory Policy Self-Build

## Procedure

1. Read `references/memory-selfbuild.md`.
2. Persist session state, route history, and repair outcomes before attempting broader changes.
3. Reconstruct interrupted work from the handoff and source index, not from memory.
4. Use the self-build loop only on coherent slices that can be verified deterministically.

## Guardrails

- Memory is durable project state, not a chat transcript.
- Self-build work must preserve `E(DELTA)=0`.
- Failure signatures and successful repairs should become structured local state.
