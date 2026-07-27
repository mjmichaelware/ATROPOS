# Batch 8 — grant-on-dispatch territory as runtime law

**Priority advanced:** #1 — preventive territory at dispatch + Director drift.
**Binding decision implemented:** C.

Supersedes `PHASE10_BATCH8_HARDSTOP.md`, which asked for exactly this decision.

## The model

```
HUMAN_OWNER  ──holds──▶  root grant (repository scope, durable, revocable)
     │
     └──dispatches──▶  HierarchyNode("dag-executor", "node-7")
                          └── child grant: narrowed to the node's declared paths,
                              parentTerritoryId = root.id,
                              boundActorIdentity = "dag-executor:node-7"
```

Rules, all enforced:

- A dispatcher may issue a child grant **only if it already holds a valid one**.
- A child's prefix must lie **within** the parent's prefix.
- The child is bound to the dispatched node's identity, so it authorises that
  work item and nothing else.
- **No grant → refuse.** **Path outside the grant → refuse.**
- Nodes never self-issue: `TerritoryGrantService` takes the dispatcher's actor
  and refuses when that actor holds nothing.
- Every refusal records a `TerritoryViolation`, which `TerritoryService` already
  forwards to `DirectorService.observe` — the drift-detection half of #1.

## Scope of enforcement

The gate checks territory for **`HierarchyNode` actors with non-empty
`targetPaths`**. That is the whole surface where paths are touched: the engine
already refuses `FILE_MUTATION`/`PATCH_APPLY` that declare no targets, and DAG
command nodes propose their declared territory as target paths.

`HumanOwner` is not checked — it holds the root grant, and an owner bounded out
of their own repository is incoherent. `SystemService` lifecycle actions carry
no paths.

To close the one gap this leaves — a DAG command node that declares no
territory and so proposes no paths — `DagExecutionService` refuses to execute
any node with empty territory. Previously such a node ran unbounded.

## Territory (allowed files only)

```
src/main/kotlin/atropos/core/territory/TerritoryModels.kt        (bound identity field)
src/main/kotlin/atropos/core/territory/TerritoryStore.kt         (serialise it)
src/main/kotlin/atropos/core/territory/TerritoryGrantService.kt  (new, atomic)
src/main/kotlin/atropos/core/policy/BoundedAgencyGate.kt         (enforcement)
src/main/kotlin/atropos/core/dag/DagExecutionService.kt          (dispatcher)
src/main/kotlin/atropos/core/agent/AgentPatchStore.kt            (dispatcher)
src/test/kotlin/atropos/core/territory/, .../policy/, .../dag/
```

## Acceptance criteria

1. A `HierarchyNode` proposal with target paths and **no grant** is refused.
2. A node granted `src/foo` is refused when it targets `src/bar`, and allowed
   when it targets `src/foo/A.kt`.
3. A dispatcher holding **no** grant cannot issue one — `grantToNode` refuses.
4. A child grant narrower than its parent succeeds; one **wider** than its
   parent is refused.
5. Every refusal writes a `TerritoryViolation` retrievable from the store.
6. `HumanOwner` proposals are unaffected; existing dispositions unchanged.
7. A DAG node with empty territory is refused rather than executed unbounded.
8. Round-trips through `TerritoryStore` preserve `boundActorIdentity`, and
   pre-existing 11-field lines still parse.

## No stub

Every criterion above is a behaviour change from refusing nothing to refusing
something specific, each proven by a test. No always-allow branch is introduced:
the `HumanOwner` exemption is the root-holder rule from decision C, stated and
tested, not a default.

## Out of scope (next batches)

`executeCheck` constant-true paths — `TERRITORY_CHECK` becomes implementable
once this lands, and is mission item 3 · priorities #2, #4–#8.

## Auditor result — PASS

Compile: one end-of-batch compile, clean.

| Class | Tests | Failures |
|---|---|---|
| `atropos.core.territory.TerritoryGrantServiceTest` (new) | 13 | 0 |
| `atropos.core.territory.TerritoryServiceTest` (pre-existing) | 4 | 0 |
| `atropos.core.policy.*` | 23 | 0 |
| `atropos.core.dag.*` | 23 | 0 |
| `atropos.core.agent.AgentPatchBoundedAgencyTest` | 8 | 0 |
| **total** | **71** | **0** |

| # | Criterion | Evidence |
|---|---|---|
| 1 | ungranted node refused | `an_ungranted_node_is_refused_at_the_gate` |
| 2 | inside allowed, outside refused | `a_granted_node_may_act_inside_its_grant_and_not_outside` |
| 3 | holder of nothing cannot grant | `a_dispatcher_holding_nothing_cannot_grant_anything` |
| 4 | narrow yes, widen no | `a_child_may_narrow_but_never_widen` |
| 5 | violation recorded | `every_refusal_is_recorded_as_a_violation` |
| 6 | owner unaffected | `the_owner_is_not_bounded_out_of_its_own_repository`, `a_lifecycle_actor_is_not_territory_bounded` |
| 7 | empty-territory node refused | `a_node_declaring_no_territory_is_granted_nothing` + the `GrantResult.Refused` branch in `DagExecutionService` |
| 8 | round-trip and back-compat | `the_bound_identity_survives_a_store_round_trip`, `assignments_written_before_grant_on_dispatch_still_parse` |

Beyond the criteria: `a_grant_authorises_one_work_item_only` proves a grant
issued to `node-1` cannot be ridden by `node-2` — the binding that makes
`boundActorIdentity` load-bearing rather than decorative.

`git diff --check`: clean. No out-of-territory repair required.

### One actor corrected during the Worker pass

`AgentPatchStore.runGitStatusForPaths` was attributed in Batch 7 to
`HierarchyNode("patch", "status")` — a synthetic work item. Once territory
began enforcing, that made a **read-only** status report territory-bounded,
which is over-reach: it mutates nothing and exists to render the operator's
apply result. Re-attributed to `HumanOwner`, which is also the truthful answer
under Batch 7's own rule that the actor is whoever authored the request.

### Test changes are the law taking effect

`DagNodeProposalsTest` and `AgentPatchBoundedAgencyTest` now grant before
acting, because without a grant their nodes are refused. That is the behaviour
change, not a weakening: the ungranted case is asserted directly in
`an_ungranted_node_is_refused_at_the_gate`.
