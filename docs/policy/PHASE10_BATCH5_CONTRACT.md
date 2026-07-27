# Phase 10 Batch 5 — daemon and queue chokepoint for externally bounded agency

**Priority advanced:** #3 — Externally bounded agency.
**Blueprint phase:** 10.

> **This batch closes the evaluate-site ledger.** After it, no file in the tree
> calls `ExecutionPolicyEngine.evaluate` outside `core/policy/`.

## The final pair

| Line | Method | Action class | Shape |
|---|---|---|---|
| `AgentDaemonService.kt:239` | `enforceDaemonPolicy` | `DAEMON` | metadata-only lifecycle gate |
| `AgentQueueService.kt:304` | `enforceQueuePolicy` | `QUEUE` | metadata-only lifecycle gate |

Both carry no command and no target paths — they authorise a lifecycle
transition, not an execution. They use the same pre-authorisation shape as
Batch 3, and both preserve their `require(...)` contract so callers keep seeing
`IllegalArgumentException` with the policy reason.

## Territory (allowed files only)

```
src/main/kotlin/atropos/core/policy/
src/main/kotlin/atropos/core/agent/AgentDaemonService.kt   (evaluate-site only)
src/main/kotlin/atropos/core/agent/AgentQueueService.kt    (evaluate-site only)
src/test/kotlin/atropos/core/policy/
```

## Acceptance criteria

1. Neither file contains a direct `policyEngine.evaluate(...)` call.
2. **Tree-wide:** no `ExecutionPolicyEngine.evaluate` call sites remain outside
   `core/policy/`. This is the ledger-closing check.
3. Proposals reproduce the previous requests field for field, including the
   `operation` and `detail` metadata.
4. The `require(...)` refusal contract is preserved in both.
5. Exactly one `ExecutionPolicyEngine` per service, reached through its gate.

## Known gap, deliberately not closed here

`AgentDaemonService.kt:230` spawns `termux-wake-unlock` through a bare
`ProcessBuilder` with no policy gate at all. It is a device wake-lock release,
not an agent action, and it was never routed through the engine — so it is not
an evaluate-site regression, it is a separate unguarded spawn. Recorded as a
finding rather than fixed, because bringing it under policy is a behaviour
change to daemon shutdown, not a chokepoint conversion.

## Non-duplication law (preserved)

No new engine, queue, journal, or daemon supervisor. `LifecycleActionProposals`
is construction only.

## Remaining evaluate-site ledger — CLOSED

| # | File | Status |
|---|---|---|
| 0 | `cli/shell/ShellCommandRunner.kt` | converted — Batch 1 |
| 1 | `core/agent/AgentPatchStore.kt` | converted — Batch 2 |
| 2 | `core/agent/AgentService.kt` | converted — Batch 3 |
| 3 | `core/agent/AgentRepairService.kt` | converted — Batch 3 |
| 4 | `core/agent/AgentVerifier.kt` | converted — Batch 4 |
| 5 | `core/agent/AgentSmokeRunner.kt` | converted — Batch 4 |
| 6 | `core/agent/AgentDaemonService.kt` | **converted — this batch** |
| 7 | `core/agent/AgentQueueService.kt` | **converted — this batch** |

## Auditor result — PASS

Compile: one end-of-batch compile, clean.

| Class | Tests | Failures |
|---|---|---|
| `atropos.core.policy.LifecycleActionProposalsTest` (new) | 5 | 0 |
| `atropos.core.policy.ProviderActionProposalsTest` | 5 | 0 |
| `atropos.core.policy.TypedToolExecutorTest` | 3 | 0 |
| `atropos.core.policy.ExecutionPolicyEngineTest` | 3 | 0 |
| **total** | **16** | **0** |

| # | Criterion | Evidence |
|---|---|---|
| 1 | no direct `evaluate` | grep in both files → no matches |
| 2 | **ledger closed** | no `ExecutionPolicyEngine.evaluate` call site outside `core/policy/`; all 8 remaining references are `BoundedAgencyGate(...)` constructions |
| 3 | requests reproduced | `daemon_proposal_...`, `queue_proposal_...`, `queue_detail_defaults_to_blank_as_it_did_before` |
| 4 | `require(...)` preserved | both call sites still `require(disposition == ALLOWED) { reason }` |
| 5 | one engine per service | 1 `ExecutionPolicyEngine(` in each |

Additional: `the_gate_delegates_rather_than_deciding` pins that engine and gate
reach the same verdict for the same proposal — the gate maps a decision, it does
not form one.

`git diff --check`: clean. No out-of-territory repair required.

## Phase 10 status

`ExecutionPolicyEngine` is now reached through exactly one road:
`ActionProposal → BoundedAgencyGate → [TypedToolExecutor] → ToolExecutionResult`.
Externally bounded agency is **runtime law on every execution path that was
routed through that engine**.

## Finding — a second policy engine exists

`AutonomyPolicyEngine` (`core/policy/AutonomyPolicyExtensions.kt`) is a
**separate policy engine**: its own action-class enum, its own rule table, and
its own audit log (`.atropos/policy/autonomy-audit.log`, alongside the execution
engine's `audit.log`). It is consulted by `AgentCommand.kt:1250`,
`DagExecutionService.kt:99` and `VerifiedCompletionGate.kt:43`.

This predates the bounded-agency work — it was not created by these batches —
but it means the tree has two authorities that can each permit an action, which
is precisely what the non-duplication law forbids. Unifying them is an
ownership change and a high-risk redesign, not a bounded chokepoint conversion,
so it is **reported, not attempted**.
