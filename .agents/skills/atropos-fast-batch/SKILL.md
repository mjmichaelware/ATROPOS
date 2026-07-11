---
name: atropos-fast-batch
description: Use when running ATROPOS fast-gate checks, cache-aware compile lanes, or deterministic batch verification without recompiling after every edit.
---

# Atropos Fast Batch

## Procedure

1. Read `references/fast-batch.md` before running any cacheable gate.
2. Use `bash scripts/codex/fast-gate.sh focused -- <command>` for a cacheable deterministic command.
3. Use `bash scripts/codex/fast-gate.sh compile` only after a coherent edit slice.
4. Use `bash scripts/codex/fast-gate.sh full` only at milestone acceptance.
5. Use `bash scripts/codex/fast-gate.sh no-cache -- <command>` for live, time-sensitive, network, mutable-daemon, or secret-sensitive checks.

## Guardrails

- Do not compile after every small edit.
- A cached pass may only satisfy the exact command that produced it.
- If a command is explicitly marked no-cache, never store or replay it.
