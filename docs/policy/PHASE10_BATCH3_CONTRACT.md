# Phase 10 Batch 3 — provider-call chokepoint for externally bounded agency

**Priority advanced:** #3 — Externally bounded agency.
**Blueprint phase:** 10.

> **Phase 10 is NOT complete after this batch.** Four evaluate-sites remain.

## Why this pair

The six remaining sites cluster into three proposal kinds. `PROVIDER_CALL` is
the highest-leverage of the three because it carries the **paid-provider lock**,
and it sits on the default agent path — every `/agent` run and every repair
attempt passes through it.

| Line | Method | Action class |
|---|---|---|
| `AgentService.kt:537` | `enforceProviderPolicy` | `PROVIDER_CALL` |
| `AgentRepairService.kt:265` | `enforceProviderPolicy` | `PROVIDER_CALL` |

Both are byte-identical in shape, and **each carries its own private copy of the
paid-provider set** (`AgentService.kt:552`, `AgentRepairService.kt:336`). Two
copies of a security-critical lock can drift; consolidating them into the policy
package — which already owns `paidProvider` semantics — removes a duplicate
rather than adding one.

## Territory (allowed files only)

```
src/main/kotlin/atropos/core/policy/          extend existing owners only
src/main/kotlin/atropos/core/agent/AgentService.kt          (evaluate-site + companion only)
src/main/kotlin/atropos/core/agent/AgentRepairService.kt    (evaluate-site + companion only)
src/test/kotlin/atropos/core/policy/
```

`AgentService.kt` is on the size-debt list (554 lines). This batch touches only
its evaluate-site and companion; the decoupling campaign is a separate batch.

## Acceptance criteria

1. Neither service contains a direct `policyEngine.evaluate(...)` call; both
   route `ActionProposal → BoundedAgencyGate`.
2. Exactly one definition of the paid-provider set remains in the tree.
3. The paid-provider lock still refuses: a paid provider is denied, and the
   refusal is still thrown as `IllegalArgumentException` with the policy reason,
   preserving the existing caller contract.
4. A free provider is still allowed, with `operation` and `prompt_length`
   metadata preserved field for field.
5. Exactly one `ExecutionPolicyEngine` per service, reached through its gate.
6. No verdict changes.

## Non-duplication law (preserved)

No new policy engine, registry, or provider list — the paid set is **moved**,
not copied. `ProviderActionProposals` is construction only.

## Explicitly out of scope

`AgentVerifier` / `AgentSmokeRunner` (`BUILD_TEST` / `SMOKE`, Batch 4) ·
`AgentDaemonService` / `AgentQueueService` (`DAEMON` / `QUEUE`, Batch 5) ·
size-debt decoupling · Territory · Auditor · SpecGraph.

## Remaining evaluate-site ledger

| # | File | Status |
|---|---|---|
| 0 | `cli/shell/ShellCommandRunner.kt` | converted — Batch 1 |
| 1 | `core/agent/AgentPatchStore.kt` | converted — Batch 2 |
| 2 | `core/agent/AgentService.kt` | **converted — this batch** |
| 3 | `core/agent/AgentRepairService.kt` | **converted — this batch** |
| 4 | `core/agent/AgentVerifier.kt` | unconverted — Batch 4 |
| 5 | `core/agent/AgentSmokeRunner.kt` | unconverted — Batch 4 |
| 6 | `core/agent/AgentDaemonService.kt` | unconverted — Batch 5 |
| 7 | `core/agent/AgentQueueService.kt` | unconverted — Batch 5 |

## Territory amendment (declared mid-batch)

`src/main/kotlin/atropos/core/paid/EmergencyPaidGate.kt` was added to territory
during the Worker pass. Acceptance criterion 2 says one paid-provider set may
remain in the tree; the Director pass found two copies and missed a **third** at
`EmergencyPaidGate.kt:28` — the same six providers, restated locally.

This is not cosmetic. That gate reports `knownPaidProviders` on the status
surface, so a drifted copy would display a different set than the engine is
actually locking. It now reads `ProviderActionProposals.PAID_PROVIDERS`.

Behaviour: membership identical. The displayed order changes (now sorted, so it
does not depend on how the set is written). No gate logic touched.

This is a declared amendment, not a silent widening — the alternative was to
leave a security-critical constant triplicated in order to keep the original
territory line intact.

## Auditor result — PASS

Compile: **one** end-of-batch compile (plus one forced re-run after the declared
amendment), clean.

| Class | Tests | Failures |
|---|---|---|
| `atropos.core.policy.ProviderActionProposalsTest` (new) | 5 | 0 |
| `atropos.core.policy.TypedToolExecutorTest` | 3 | 0 |
| `atropos.core.policy.ExecutionPolicyEngineTest` | 3 | 0 |
| **total** | **11** | **0** |

No test exists for `core/paid`; the amendment is covered indirectly by
`every_paid_provider_is_blocked_through_the_gate`, which pins the canonical set
both files now share.

| # | Criterion | Evidence |
|---|---|---|
| 1 | no direct `evaluate` in either service | grep for `policyEngine` in both → no matches |
| 2 | one paid-provider definition | grep for the set literal → 1 hit, `ProviderActionProposals.kt:25` |
| 3 | paid lock still refuses, still throws | `every_paid_provider_is_blocked_through_the_gate`; `require(...)` contract preserved |
| 4 | free provider allowed, metadata intact | `free_providers_are_allowed_through_the_gate`, `proposal_reproduces_the_previous_policy_request` |
| 5 | one engine per service | 1 `ExecutionPolicyEngine(` in each |
| 6 | no verdict change | proposal field-for-field test; engine tests unchanged |

`git diff --check`: clean. No out-of-territory *repair* was needed (the
amendment above is a scope change, not a repair).
