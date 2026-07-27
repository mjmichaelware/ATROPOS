# Batch 10 — the completion boundary: cycle, Auditor authority, fail-closed

**Decisions implemented:** D, E, F. **Priorities advanced:** #4, #5.

Three mission items, one file and one concern: what it takes for a node to be
declared complete.

## D — the construction cycle

`VerifiedCompletionGate.kt:38` holds
`dagService: DagExecutionService = DagExecutionService(config, repoRoot)`, while
`DagExecutionService` would need the gate — mutual default construction, a stack
overflow at instantiation.

It uses `dagService` at exactly two places, both `readDag(dagId)`, and
`DagStore.readDag` already exists on the **already-injected** `dagStore`. So the
cycle breaks by **deleting the field**, not by restructuring ownership. Nothing
gains a dependency.

## E — Auditor authority

A ninth gate consults `AuditorService` over the node's files. Findings of
`FAILURE` or `CRITICAL` refuse completion.

The auditor **cannot approve its own work**, structurally: `canComplete` is
`gates.all { it.passed }`, so an added gate can only ever subtract. A clean
audit never makes a failing node completable. A fresh `AuditorService` is
created per evaluation, because it accumulates findings in mutable state and a
shared instance would let one node's findings block another's.

## F — fail-closed

Five paths currently claim safety having inspected nothing:

| Line | Path | Today |
|---|---|---|
| 130 | `checkFocusedTests`, no payload | passes, "no tests required (skipped)" |
| 141 | `checkFocusedTests`, process threw | `.getOrDefault(true)` — **passes on exception** |
| 186 | `checkTerritoryAndSecrets`, git failed | `.getOrDefault("")` — empty diff reads as clean |
| 198 | `checkTerritoryAndSecrets`, no territory | `territoryOk = true` unconditionally |
| 219 | `checkExpectedOutputs`, none defined | passes, "no expected outputs defined" |

Each becomes a failure. `DagNode` gains `optionalChecks: Set<String>` — the
node contract's explicit opt-out required by decision F. A check named there
may skip; nothing else may.

## Territory (allowed files only)

```
src/main/kotlin/atropos/core/verification/VerifiedCompletionGate.kt
src/main/kotlin/atropos/core/dag/DagModels.kt    (optionalChecks on DagNode)
src/main/kotlin/atropos/core/dag/DagStore.kt     (serialise it)
src/test/kotlin/atropos/core/verification/
```

## Acceptance criteria

1. `VerifiedCompletionGate` no longer references `DagExecutionService`; both are
   constructible with defaults without recursion.
2. `reVerifyNode` and `detectFalseCompletions` still work, now via `dagStore`.
3. A node whose audited files contain secret material is refused, with the
   Auditor gate named in the failure.
4. A clean audit does not make an otherwise-failing node completable.
5. Each of the five paths in the table fails where it used to pass.
6. A check listed in `optionalChecks` may skip; one not listed may not.
7. `optionalChecks` round-trips through `DagStore`; nodes written before this
   batch load with an empty set.

## No stub

Every change converts a pass into a refusal, or removes a field. No new
always-allow path: the `optionalChecks` opt-out is explicit per node, defaults
to empty, and is asserted in both directions.

## Auditor result — PASS

Compile: one end-of-batch compile, clean.

| Class | Tests | Failures |
|---|---|---|
| `atropos.core.verification.VerifiedCompletionGateTest` (new) | 10 | 0 |
| `atropos.core.dag.*` (5 existing, cover the changed model and store) | 34 | 0 |
| **total** | **44** | **0** |

| # | Criterion | Evidence |
|---|---|---|
| 1 | cycle gone | no `DagExecutionService` reference remains; `the_completion_gate_and_the_dag_service_can_both_be_built_with_defaults` constructs both |
| 2 | reads still work | same test exercises `reVerifyNode` and `detectFalseCompletions` through `dagStore` |
| 3 | auditor refuses on secrets | `secret_material_in_an_audited_file_refuses_completion` |
| 4 | auditor cannot approve | `the_auditor_can_only_subtract_never_approve` — clean audit, node still not completable |
| 5 | five soft-passes now fail | `a_node_with_no_payload_...`, `..._no_territory_...`, `..._no_expected_outputs_...`, `..._names_no_files_...`; the two exception paths now `return` a failure instead of `getOrDefault` |
| 6 | opt-out honoured, and scoped | `an_explicit_opt_out_in_the_node_contract_is_honoured` — opting one check out does not opt out its neighbour |
| 7 | round trip and default | `optional_checks_survive_a_store_round_trip_and_default_to_empty` |

Also: `each_evaluation_gets_a_fresh_auditor` proves one node's findings cannot
refuse another's completion through the auditor's mutable state.

### Pre-existing failure, outside territory, NOT repaired

`atropos.core.verification.DeterministicVerifierTest >
catches_out_of_scope_sources_before_model_review` fails with
`IllegalArgumentException: unknown Kotlin source: ../atropos-deterministic-outside-…/Out.kt`
— it expects a finding and gets a throw. Confirmed on baseline by stashing this
batch and re-running: it fails identically without any of these changes.

It does **not** block test compilation, so the approved out-of-territory repair
protocol does not cover it. Reported rather than quietly fixed.

### No stub

Every change converts a pass into a refusal or deletes a field. The
`optionalChecks` opt-out defaults to empty and is asserted in both directions,
so it cannot become a silent always-allow.
