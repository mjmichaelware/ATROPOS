# ATROPOS Operating Rules

ATROPOS is a local-first, deterministic software-development engine with persistent memory, exact source addressing, policy-governed execution, and verified artifact production. The final vision is a system that can accept a task, resolve it to exact source coordinates, edit only its assigned territory, verify deterministically, and preserve every decision across sessions.

## Authority

- Human Owner/CEO has final authority.
- Codex acts as the implementation worker inside the current worktree.
- Source documents define truth before code claims do.
- If code, docs, and tests disagree, prefer the highest authority below.

## Source Authority Precedence

1. ATROPOS CODEX-CLI BUILD BLUEPRINT OVER TIME
2. ATROPOS Source Documents 1 and 2
3. Latest hierarchy and coding-agent research documents
4. Updated DLOI source-document map
5. Current repository contracts and proven tests
6. Dated implementation contexts and doctor reports
7. README claims only as historical or aspirational evidence

## Canonical Phases

Never rename or reorder the canonical phases 0-20.

| Phase | Canonical name | Operating meaning |
| --- | --- | --- |
| 0 | Baseline Lock | Freeze current truth, inventory the repo, and preserve the known-good checkpoint. |
| 1 | Provider Activation Doctor | Make configured providers diagnosable and truthful. |
| 2 | Provider Transport Completion | Reach real provider responses through working adapters. |
| 3 | Quota Ledger + Route Truth | Route free-first, track quota, and explain skipped providers. |
| 4 | Secret and Security Hardening | Prevent secret leakage across UI, logs, prompts, diffs, and history. |
| 5 | Provider Fixture Matrix | Verify providers without live keys using deterministic fixtures. |
| 6 | DLOI Source Router | Route intent to exact source sections and document coordinates. |
| 7 | AST Symbol Graph | Map changed files and symbols without guessing. |
| 8 | Deterministic Verifier | Catch structural errors locally before model review. |
| 9 | Persistent Memory | Persist session, route, and repair state across restarts. |
| 10 | Execution Policy Engine | Govern every tool action with explicit policy. |
| 11 | Self-Build Loop | Land bounded batches with deterministic verification and `E(DELTA)=0`. |
| 12 | Director Advisory Mode | Add Director visibility and advisory drift detection. |
| 13 | Territory Enforcement | Make file and worktree scope deterministic and enforceable. |
| 14 | HR Router | Control cross-boundary information flow. |
| 15 | Auditor and Custodian | Add independent verification and deterministic cleanup. |
| 16 | Manager/Specialist/Worker Hierarchy | Scale execution with bounded roles and escalation paths. |
| 17 | Multimodal Runtime | Add visual and snapshot inspection to the engine. |
| 18 | Multiplatform Expansion | Extract a portable core and broaden runtime targets. |
| 19 | App Factory Completion | Turn requests into verified artifacts, install proofs, and commits. |
| 20 | Full Autonomous ATROPOS | Self-direct within policy, memory, and territory boundaries. |

## Fixed Phase Definitions

- Phase 11: execute a coherent self-build slice, compile once per slice boundary, repair the real failure, verify the result, and preserve `E(DELTA)=0`.
- Phase 19: move from prompt to plan to code to verification to artifact to commit, with screenshot or run proof before acceptance.
- Phase 20: operate as a policy-bound, persistent, self-improving software-development engine that reuses the same gates used to build it.

## Formal Invariants

- `HIG=0`
- `E(DELTA)=0`
- `LOCAL_FIRST=true`
- `PAID_AUTO=false`
- `RAW_SECRET_OUTPUT=false`
- `ROUTE_EXPLAINABLE=true`
- `PROVIDER_REPLACEABLE=true`
- `TERRITORY_BOUND=true`
- `VERIFY_BEFORE_COMMIT=true`
- `ADDRESS_NEVER_BLINDLY_INGEST=true`

## Operating Laws

### Free-First Route Law

Prefer local toolchain work first, then free ready providers, then free fallbacks, then cooldown queues, then offline degraded mode. Paid providers require explicit unlock and remain off by default.

### Configured, Verified, Ready

- Configured: the endpoint and credentials are present.
- Verified: the provider or route has passed the configured truth check.
- Ready: configured, verified, allowed, not cooling down, and not locked.

### Paid-Auto Prohibition

Do not auto-spend paid provider capacity. Do not silently unlock paid routes. Require explicit intent and truthful status.

### Exact-Source and DLOI Law

Never treat a corpus claim as current truth unless it resolves to exact source coordinates. Use source IDs, section IDs, line/page/paragraph spans, and deterministic query commands. No blind whole-corpus injection.

### AST Impact Law

Before editing, determine the impacted files and symbols from the exact change slice. Do not expand scope by intuition.

### Deterministic-Verification-First Law

Run deterministic checks before model review whenever a local check can decide the issue. Model review is for undecidable cases, not for first-pass validation.

### Persistent Memory Law

Persist session state, route history, failure signatures, repair results, and compaction summaries in local durable stores. A restart must not erase project truth.

### Execution-Policy Law

Every tool call is governed. Every mutation is logged. No action is assumed safe just because it is possible.

### Self-Build Law

ATROPOS must be able to plan a bounded batch, edit only its territory, compile once per coherent slice, repair the real failure, and resume from persisted state.

### Territory and Hierarchy Law

Tasks have owners, allowed paths, and escalation paths. Out-of-territory edits are violations, not surprises.

### Secret and Redaction Law

Redact secrets before logs, prompts, diffs, history, and status surfaces. Never print raw credentials or token values.

### Context Economy

Build context packs from selected authority only: current contracts, changed files, impacted symbols, and bounded evidence. Do not inject the entire corpus into a prompt.

### Cache Correctness

Cache keys must include the real invalidation inputs. For source caches, include source hashes and tool schema version. For gate caches, include command, cwd, input hashes, build config hashes, affecting env, tool version, and expected invariant set.

### Compile Cadence

Use this cadence:

1. Contract audit
2. Coherent edit slice
3. Focused deterministic test
4. `compileKotlin` after the coherent slice
5. Test or jar only at milestone acceptance
6. Installed smoke only at the final gate

Never compile after every small edit.

### Interruption Recovery

If the terminal closes, resume from the persisted index, gate cache, and handoff notes before re-running expensive work.

### Dirty-Work Preservation

Preserve every existing tracked and untracked byte unless the user explicitly requests a change. Never discard unrelated local work.

### Exact-Path Staging Only

Stage only the exact files that belong to the current slice. No blind `git add .`.

### No Force Push

Do not force push.

### No Auto-Commit or Auto-Push

Do not commit or push unless the user explicitly requests it.

### Missing Implementation Is Work

If a capability is missing, that is implementation work, not an external blocker.

### Truthful Acceptance

Accept only when the selected gate is actually green. If a gate fails, report the failure rather than inflating progress.

### UI Separate From Backend

Presentation work remains separate from adapter, routing, policy, and storage work unless the batch explicitly needs observability changes.

## Cache and Index Commands

```bash
python3 scripts/codex/source-index.py --strict
python3 scripts/codex/source-query.py phase 11 --limit 3
python3 scripts/codex/source-section.py <source-id> <section-id>
python3 scripts/codex/context-pack.py --help
python3 scripts/codex/resume-handoff.sh
python3 scripts/codex/fast-gate.sh focused -- <command>
python3 scripts/codex/instruction-audit.sh
```

## Acceptance Rule

Truthful acceptance requires that the selected gate passes, the worktree diff is scoped, secrets are absent from new files, and the result matches `E(DELTA)=0` for the intended slice.
