# Batch 9 — the three constant-true DAG checks become real

**Mission item:** 3 — replace `DagExecutionService.executeCheck`.
**Priority advanced:** #5 (deterministic failure-first verification), plus the
`TERRITORY_CHECK` half of #1 that Batch 8 unblocked.

## What is wrong

```kotlin
private fun executeCheck(node: DagNode, original: DagNode): DagNodeExecutionResult {
    val running = store.writeNode(node.copy(state = DagNodeState.RUNNING))
    completeNode(running, NodeResult(original.id, true, "check passed", COMPLETE, result = "check passed"))
    return DagNodeExecutionResult(original.id, COMPLETE, true, "check passed")
}
```

`POLICY_CHECK`, `SECRET_CHECK` and `TERRITORY_CHECK` all report **"check
passed"** without checking anything. Three gates that cannot fail. This is the
theater the no-stub law exists to prevent, and it was found and recorded in
Batch 6 rather than fixed there.

## What each check becomes

| Action | Question it answers | Fails when |
|---|---|---|
| `POLICY_CHECK` | would the single permission authority allow this node's payload? | `BoundedAgencyGate` returns anything but `ALLOWED` |
| `SECRET_CHECK` | do the node's paths carry credential material? | `CredentialDiffGuard.inspectPaths` returns findings |
| `TERRITORY_CHECK` | does the node hold a grant covering everything it declared? | any declared path lies outside its grants |

All three use existing owners — the gate, `CredentialDiffGuard`,
`TerritoryGrantService`. Nothing new is invented, and none of them can pass
vacuously: a check with nothing to inspect **fails**, because a gate that
cannot tell you anything must not report success.

## Territory (allowed files only)

```
src/main/kotlin/atropos/core/dag/DagNodeCheckEvaluator.kt   (new, atomic)
src/main/kotlin/atropos/core/dag/DagExecutionService.kt     (executeCheck only)
src/test/kotlin/atropos/core/dag/
```

The evaluator is its own file: deciding whether a check passes is a distinct
responsibility from orchestrating node execution, and `DagExecutionService` is
already large.

## Acceptance criteria

1. A `POLICY_CHECK` node whose payload chains commands, redirects, or calls
   network tools **fails**; one with a clean payload passes.
2. A `SECRET_CHECK` node whose paths include `.env` or a credential-shaped
   filename **fails**; one over ordinary source paths passes.
3. A `TERRITORY_CHECK` node declaring a path outside its grant **fails**; one
   fully inside its grant passes.
4. A check node with nothing to inspect **fails** rather than passing — no
   vacuous success.
5. Failing checks set `DagNodeState.FAILED` with the specific reason, not
   `COMPLETE`.
6. Every branch above is covered by a test asserting both the pass and the fail
   direction.

## No stub

Each check moves from unconditional success to a real verdict with a real
failure mode. Criterion 4 exists specifically so the "nothing to check" path
cannot become the new always-pass.

## Out of scope

Priorities #2, #4, #6, #7, #8 · size-debt decoupling · SpecGraph.

## Auditor result — PASS

Compile: one end-of-batch compile, clean.

| Class | Tests | Failures |
|---|---|---|
| `atropos.core.dag.DagNodeCheckEvaluatorTest` (new) | 11 | 0 |
| `atropos.core.dag.*` (4 existing) | 23 | 0 |
| `atropos.core.territory.*` | 17 | 0 |
| **total** | **51** | **0** |

| # | Criterion | Evidence |
|---|---|---|
| 1 | policy check both directions | `policy_check_fails_on_a_payload_the_authority_would_refuse` (3 payloads), `policy_check_passes_a_clean_payload` |
| 2 | secret check both directions | `secret_check_fails_on_credential_material` (`.env` and `private_key.pem`), `secret_check_passes_ordinary_source_paths` |
| 3 | territory check both directions | `territory_check_fails_when_a_declared_path_is_outside_the_grant`, `..._when_the_node_holds_no_grant_at_all`, `territory_check_passes_inside_the_grant` |
| 4 | no vacuous success | `no_check_action_can_pass_without_something_to_inspect`, plus one per action |
| 5 | failing check fails the node | `executeCheck` sets `DagNodeState.FAILED` with `failureReason` |
| 6 | pass and fail both covered | every check has at least one of each |

The only remaining occurrence of `"check passed"` in `DagExecutionService` is
the KDoc sentence recording what the code used to do.

`git diff --check`: clean. No out-of-territory repair required.

### No stub

Each check went from unconditional success to a real verdict sourced from an
existing owner. Criterion 4 was written specifically so "nothing to inspect"
could not become the new always-pass — and it is asserted for all three
actions.
