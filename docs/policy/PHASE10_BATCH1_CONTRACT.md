# Phase 10 Batch 1 — shell execution chokepoint for externally bounded agency

**Status:** in progress → see "Auditor result" at the end.
**Priority advanced:** #3 — Externally bounded agency (models propose, system decides).
**Blueprint phase:** 10 (Execution Policy Engine).

> **Phase 10 is NOT complete after this batch.** This batch converts exactly one
> execution site — the interactive shell path — from a direct policy-engine call
> into a bounded-agency proposal. Seven sites remain.

## Contract

Every side effect on this path must travel:

```
ActionProposal → BoundedAgencyGate → TypedToolExecutor → ToolExecutionResult
```

`ShellCommandRunner` may not consult the policy engine itself. It states an
intent; the system decides; the runner renders whatever the system returned.

## Territory (allowed files only)

```
src/main/kotlin/atropos/core/policy/          extend existing owners only
src/main/kotlin/atropos/cli/shell/ShellCommandRunner.kt
src/test/kotlin/atropos/core/policy/
src/test/kotlin/atropos/cli/shell/
```

Nothing outside this list is touched.

## Acceptance criteria

1. `ShellCommandRunner` contains zero direct `policyEngine.evaluate(...)` calls.
2. A `POLICY_BLOCKED` proposal never reaches `ProcessBuilder`.
3. An `APPROVAL_REQUIRED` proposal never reaches `ProcessBuilder`, and is
   **distinguishable from** `POLICY_BLOCKED` — it is not collapsed into exit 126.
4. The refusal is a typed, renderable outcome (`AgencyDisposition` + proposal id
   + policy reason carried on `ShellCommandResult`), not a bare exit code, so a
   future compositor can raise an approval dialog against it.
5. Exactly one `ExecutionPolicyEngine` instance exists in the shell dependency
   chain, injected through `BoundedAgencyGate` for testability.
6. `ExecutionPolicyEngineTest` semantics unchanged — the gate delegates, it does
   not re-decide. No shell command's decision changes as a result of this batch.

## Non-duplication law (preserved)

- No new policy engine. `BoundedAgencyGate` delegates to the existing
  `ExecutionPolicyEngine`.
- No second territory system, verifier, queue, journal, or provider registry.
- No parallel executor framework. `TypedToolExecutor` and `ToolExecutionResult`
  already existed; this batch gives them their first caller.
- `ShellActionProposals` is proposal *construction* only. It holds no policy
  logic and makes no decision — classification mirrors what the runner already
  passed to the engine, byte for byte.

## Explicitly out of scope (downstream, not in this batch)

Territory Enforcement · Auditor / Custodian · HR Information Router ·
Manager / Specialist / Worker hierarchy · App Factory · long-horizon restart
autonomy · the UI approval flow and compositor · Source Document ingestion ·
SpecGraph.

## Remaining evaluate-site ledger

Seven non-shell sites still call `ExecutionPolicyEngine.evaluate(...)` directly
and remain unfinished after this batch:

| # | File | Status |
|---|---|---|
| 1 | `src/main/kotlin/atropos/core/agent/AgentService.kt` | unconverted |
| 2 | `src/main/kotlin/atropos/core/agent/AgentDaemonService.kt` | unconverted |
| 3 | `src/main/kotlin/atropos/core/agent/AgentSmokeRunner.kt` | unconverted |
| 4 | `src/main/kotlin/atropos/core/agent/AgentRepairService.kt` | unconverted |
| 5 | `src/main/kotlin/atropos/core/agent/AgentVerifier.kt` | unconverted |
| 6 | `src/main/kotlin/atropos/core/agent/AgentQueueService.kt` | unconverted |
| 7 | `src/main/kotlin/atropos/core/agent/AgentPatchStore.kt` | unconverted |

| # | File | Status |
|---|---|---|
| 0 | `src/main/kotlin/atropos/cli/shell/ShellCommandRunner.kt` | **converted in this batch** |

## Territory exception (declared, not waived)

`src/test/kotlin/atropos/cli/commands/SelfHostCommandTest.kt` was repaired
outside the declared territory. It was **already broken on `main` before this
batch** — three `Smart cast to 'SelfHostGoal' is impossible` errors, reproduced
by stashing this batch and compiling the baseline. Because Kotlin compiles a
source set as a unit, that breakage made *every* test in the repository
uncompilable, so the batch could not be verified at all without it.

The repair is mechanical: bind the nullable `started.goal` to a local before
using it. No assertion, no behaviour, and no production code was changed.

## Auditor result — PASS

Compile: **one** end-of-batch compile, `compileKotlin` + `compileTestKotlin`,
clean (warnings only, all pre-existing).

Tests run — only those covering this batch:

| Class | Tests | Failures |
|---|---|---|
| `atropos.cli.shell.ShellBoundedAgencyTest` (new) | 6 | 0 |
| `atropos.core.policy.TypedToolExecutorTest` | 3 | 0 |
| `atropos.core.policy.ExecutionPolicyEngineTest` | 3 | 0 |
| **total** | **12** | **0** |

Acceptance criteria:

| # | Criterion | Evidence |
|---|---|---|
| 1 | zero direct `policyEngine.evaluate` | grep for `policyEngine`/`ExecutionPolicyRequest` in `ShellCommandRunner.kt` → no matches |
| 2 | blocked never spawns | `policy_blocked_proposal_never_reaches_the_process_seam` — spawn counter 0 |
| 3 | approval-required never spawns, stays distinct | `approval_required_*` + `approval_required_is_not_collapsed_into_policy_blocked` — 126 vs 125 |
| 4 | typed renderable outcome | `refusal_is_a_typed_outcome_a_compositor_can_render` |
| 5 | exactly one engine in the chain | one `ExecutionPolicyEngine(` at `ShellCommandRunner.kt:55`, inside `BoundedAgencyGate` |
| 6 | verdicts unchanged | `ExecutionPolicyEngineTest` 3/3 unchanged; `real_policy_verdicts_are_unchanged_by_the_gate` |

`git diff --check`: clean.

Self-approval: none. The Auditor verified against the recorded contract above,
which was written before any code, and reports the territory exception rather
than absorbing it.
