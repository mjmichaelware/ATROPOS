# Phase 10 Batch 2 — patch/apply chokepoint for externally bounded agency

**Priority advanced:** #3 — Externally bounded agency.
**Blueprint phase:** 10 (Execution Policy Engine).

> **Phase 10 is NOT complete after this batch.** Six agent-side evaluate-sites
> remain after `AgentPatchStore`.

## Why this site

`AgentPatchStore` is where provider-authored text becomes a repository
mutation. It is the site at which "never execute raw provider prose" actually
bites — a shell command is typed by the operator, but a patch is written by a
model. Four direct `policyEngine.evaluate(...)` calls guard it today:

| Line | Method | Action class | Spawns |
|---|---|---|---|
| 194 | `runGitApplyCheck` | `PATCH_APPLY` | `git apply --check` |
| 247 | `runGitApply` | `PATCH_APPLY` | `git apply` |
| 278 | `runGitStatusForPaths` | `GIT` | `git status --porcelain` |
| 329 | `applyPatch` | `PATCH_APPLY` | pre-authorisation, no spawn |

## Territory (allowed files only)

```
src/main/kotlin/atropos/core/policy/          extend existing owners only
src/main/kotlin/atropos/core/agent/AgentPatchStore.kt
src/test/kotlin/atropos/core/agent/
```

## Acceptance criteria

1. `AgentPatchStore` contains zero direct `policyEngine.evaluate(...)` calls;
   all four sites travel `ActionProposal → BoundedAgencyGate →
   TypedToolExecutor → ToolExecutionResult`.
2. A refused proposal never reaches `ProcessBuilder` — proven with a spawn seam
   that counts invocations.
3. `APPROVAL_REQUIRED` stays distinct from `POLICY_BLOCKED` (125 vs 126) on the
   patch path, matching Batch 1.
4. Refusals are typed and renderable: `disposition` + `proposalId` survive on
   `AgentPatchCheckResult` and `AgentPatchApplyResult`.
5. Exactly one `ExecutionPolicyEngine` in the patch dependency chain, reached
   through one shared `BoundedAgencyGate`.
6. No verdict changes: proposals reproduce the previous `ExecutionPolicyRequest`
   field for field, including `targetPaths`, on which `PATCH_APPLY` denial
   depends.
7. `applyPatch` still refuses before any mutation when the gate does not
   authorise — the pre-authorisation ordering is preserved.

## Non-duplication law (preserved)

No new policy engine, territory system, verifier, queue, journal, or registry.
`PatchActionProposals` is construction only — no policy logic, no verdict.
`BoundedAgencyGate` delegates to the existing `ExecutionPolicyEngine`.

## Explicitly out of scope

The six remaining agent-side evaluate-sites · Territory Enforcement · Auditor /
Custodian · HR Router · hierarchy · App Factory · restart autonomy · UI approval
flow · SpecGraph.

## Remaining evaluate-site ledger

| # | File | Status |
|---|---|---|
| 0 | `cli/shell/ShellCommandRunner.kt` | converted — Batch 1 |
| 1 | `core/agent/AgentPatchStore.kt` | **converted — this batch** |
| 2 | `core/agent/AgentService.kt` | unconverted |
| 3 | `core/agent/AgentDaemonService.kt` | unconverted |
| 4 | `core/agent/AgentSmokeRunner.kt` | unconverted |
| 5 | `core/agent/AgentRepairService.kt` | unconverted |
| 6 | `core/agent/AgentVerifier.kt` | unconverted |
| 7 | `core/agent/AgentQueueService.kt` | unconverted |

## Auditor result — PASS

Compile: **one** end-of-batch compile, `compileKotlin` + `compileTestKotlin`, clean.

| Class | Tests | Failures |
|---|---|---|
| `atropos.core.agent.AgentPatchBoundedAgencyTest` (new) | 8 | 0 |
| `atropos.core.policy.TypedToolExecutorTest` | 3 | 0 |
| `atropos.core.policy.ExecutionPolicyEngineTest` | 3 | 0 |
| **total** | **14** | **0** |

| # | Criterion | Evidence |
|---|---|---|
| 1 | zero direct `policyEngine.evaluate` | grep in `AgentPatchStore.kt` → no matches |
| 2 | refused never spawns | 4 spawn-counter tests, all 0 |
| 3 | 125 vs 126 preserved | `approval_required_apply_is_withheld_and_distinct_from_blocked` |
| 4 | typed renderable refusal | `refusal_carries_a_proposal_id_a_compositor_can_act_on` |
| 5 | one engine in the chain | one `ExecutionPolicyEngine(` at line 135, inside the shared gate |
| 6 | no verdict change | `proposals_reproduce_the_previous_policy_request`, `real_policy_verdicts_are_unchanged_by_the_gate` |
| 7 | pre-authorisation ordering kept | `apply_patch_refuses_before_mutating_when_the_gate_withholds` |

`ProcessBuilder` sites in the file: 1 (the default seam). `git diff --check`: clean.

No out-of-territory repair was required in this batch.

### Behaviour preserved deliberately

`runGitStatusForPaths` returned **uncompacted** output before this batch. The
shared `runThroughAgency` helper compacts by default, so the status read passes
`compact = false` — the refactor must not quietly truncate a caller's output.

Self-approval: none. Verified against the contract recorded above, written
before any code.
