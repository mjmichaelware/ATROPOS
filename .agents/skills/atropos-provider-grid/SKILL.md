---
name: atropos-provider-grid
description: Use when working on provider activation, free-first routing, quota/paid-lock behavior, provider doctoring, or route/eligibility truth.
---

# Atropos Provider Grid

## Procedure

1. Read `references/provider-grid.md`.
2. Resolve provider state before route logic.
3. Keep free-first routing truthful: configured is not verified, and verified is not ready.
4. Treat paid routes as explicit unlocks only.

## Guardrails

- Never collapse provider truth into a single boolean.
- Report missing, configured, verified, locked, rate-limited, quota-exhausted, and ready states distinctly.
- Route explanations must show why a provider was chosen or skipped.
