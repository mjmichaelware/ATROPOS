---
name: atropos-acceptance-release
description: Use when preparing acceptance gates, release readiness, final smoke, or E(DELTA)=0 verification.
---

# Atropos Acceptance Release

## Procedure

1. Read `references/acceptance-release.md`.
2. Run the final deterministic audit before claiming completion.
3. Verify the diff scope, secret scan, handoff capture, and exact source coordinates.
4. Accept only when the slice is truthful and the worktree state is preserved.

## Guardrails

- Do not conflate progress with completion.
- Do not call a slice complete unless the selected gate is actually green.
- Keep release proof tied to the exact artifact or operating change.
