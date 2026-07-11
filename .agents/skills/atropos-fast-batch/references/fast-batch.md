# Fast Batch Reference

## Gate cache key

The gate cache key must include:

- full command and arguments
- working directory
- relevant input-file hashes
- build configuration hashes
- environment variables that affect compilation, excluding secret values
- tool version
- expected output or invariant set

## Never cache

- live-provider tests
- time-sensitive checks
- network checks
- mutable daemon checks
- secret checks
- commands explicitly marked `no-cache`

## Supported commands

```bash
bash scripts/codex/fast-gate.sh focused -- <command>
bash scripts/codex/fast-gate.sh compile
bash scripts/codex/fast-gate.sh full
bash scripts/codex/fast-gate.sh no-cache -- <command>
```

## Cadence

Use the fast gate after a coherent slice, not after every line edit. Favor deterministic local checks before any expensive compile or smoke.
