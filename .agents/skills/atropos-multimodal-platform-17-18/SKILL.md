---
name: atropos-multimodal-platform-17-18
description: "Use when implementing phases 17-18: multimodal inspection, screenshot/vision handling, Compose Desktop, Android shell, or multiplatform extraction."
---

# Atropos Multimodal Platform 17-18

## Procedure

1. Read `references/multimodal-platform-17-18.md`.
2. Separate multimodal inspection work from platform extraction work.
3. Keep visual verification and multiplatform runtime changes in their own batch unless observability demands coupling.
4. Verify screenshots, terminal snapshots, or platform-specific builds with deterministic checks first.

## Guardrails

- Phase 17 is about seeing the runtime.
- Phase 18 is about extracting the portable core.
- Do not mix core provider logic with UI plumbing unless the slice explicitly requires it.
