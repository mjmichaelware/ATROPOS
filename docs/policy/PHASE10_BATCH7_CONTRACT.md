# Batch 7 — actor identity on every ActionProposal

**Binding decision advanced:** B — explicit actor identity for territory.
**Unblocks:** priority #1 (territory at dispatch), which is Batch 8.

## The model

```kotlin
sealed interface ActionActor {
    HumanOwner                          // operator-typed shell / direct human command
    HierarchyNode(role, nodeId)         // model-authored dispatched work
    SystemService(service)              // daemon / lifecycle internal action
}
```

`ActionProposal.actor` is **required and non-nullable**. "No actor → refuse" is
enforced by the type system: a proposal without an actor cannot be constructed.
`HierarchyNode` rejects a blank role or nodeId at construction, and
`BoundedAgencyGate` refuses any proposal whose actor identity is blank.

## Actor assignment — truthful, not invented

Each site is given the identity that actually exists there. No site is handed a
fabricated node id.

| Builder | Caller | Actor | Identity source |
|---|---|---|---|
| `ShellActionProposals` | `ShellCommandRunner` | `HumanOwner` | operator typed it |
| `DagNodeProposals` | `DagExecutionService` | `HierarchyNode("dag-executor", node.id)` | real node id |
| `PatchActionProposals` | `AgentPatchStore` | `HierarchyNode("patch", patchId)` | patch id, from the record or the `<id>.diff` filename |
| `ProviderActionProposals` | `AgentService` | `HumanOwner` | `/agent ask` and `/agent patch` are operator-initiated; the provider is the tool, not the actor |
| `ProviderActionProposals` | `AgentRepairService` | `HierarchyNode("repair", patchId)` | the patch being repaired |
| `VerificationActionProposals` | `AgentVerifier` | `HierarchyNode("verify", patchId)` | the patch being verified |
| `VerificationActionProposals` | `AgentSmokeRunner` | caller-supplied, default `HumanOwner` | smoke commands are operator-supplied unless a run passes its own |
| `LifecycleActionProposals` | `AgentDaemonService`, `AgentQueueService` | `SystemService("daemon" / "queue")` | internal lifecycle |

## Territory (allowed files only)

```
src/main/kotlin/atropos/core/policy/          ActionActor.kt (new), ActionProposal.kt,
                                              BoundedAgencyGate.kt, the four *ActionProposals
src/main/kotlin/atropos/core/dag/DagNodeProposals.kt, DagExecutionService.kt
src/main/kotlin/atropos/cli/shell/ShellCommandRunner.kt
src/main/kotlin/atropos/core/agent/AgentPatchStore.kt, AgentService.kt,
                                   AgentRepairService.kt, AgentVerifier.kt,
                                   AgentSmokeRunner.kt, AgentDaemonService.kt,
                                   AgentQueueService.kt
src/test/kotlin/atropos/core/policy/, src/test/kotlin/atropos/core/dag/,
src/test/kotlin/atropos/core/agent/, src/test/kotlin/atropos/cli/shell/
```

## Acceptance criteria

1. `ActionProposal.actor` is required and non-nullable — no default, so every
   construction site must state one.
2. `HierarchyNode("", …)` and `HierarchyNode(…, "")` throw at construction.
3. `BoundedAgencyGate` refuses a proposal whose actor identity is blank, with
   `POLICY_BLOCKED` and a reason naming the missing identity.
4. Every one of the 11 construction sites supplies the actor from the table
   above; no site invents an identifier.
5. Existing dispositions are unchanged for well-formed proposals — adding an
   actor must not alter any current allow/deny outcome.

## Design correction made during the Worker pass

The contract above specified a `BoundedAgencyGate` refusal for a blank actor
identity (criterion 3). While writing its test the compiler proved that branch
**unreachable**: `ActionActor` is a sealed interface, so no code outside the
policy module can implement it, and once `SystemService` validates its name the
way `HierarchyNode` already did, every variant guarantees a non-blank identity by
construction.

Keeping a runtime guard that cannot fire would be exactly the unreachable-branch
theater the no-stub law forbids, so **the guard was removed** and the refusal
lives entirely at construction, where it is total rather than conditional:

- `ActionProposal.actor` has no default → omission is a **compile error**
- `HierarchyNode` / `SystemService` reject blank input → **`IllegalArgumentException`**

That is stronger than criterion 3 as written. Criterion 3 is recorded as
**superseded**, not silently dropped.

## Auditor result — PASS

Compile: one end-of-batch compile (plus two corrective recompiles: a missing
`patchId` thread-through in `AgentRepairService`, and the sealed-interface
finding above).

| Class | Tests | Failures |
|---|---|---|
| `atropos.core.policy.ActionActorTest` (new) | 7 | 0 |
| `atropos.core.policy.*` (4 existing) | 16 | 0 |
| `atropos.core.dag.DagNodeProposalsTest` | 10 | 0 |
| `atropos.core.dag.*` (3 pre-existing, pulled in by the package filter) | 13 | 0 |
| `atropos.core.agent.AgentPatchBoundedAgencyTest` | 8 | 0 |
| `atropos.core.agent.AgentSmokeBoundedAgencyTest` | 6 | 0 |
| `atropos.cli.shell.ShellBoundedAgencyTest` | 6 | 0 |
| **total** | **66** | **0** |

| # | Criterion | Evidence |
|---|---|---|
| 1 | actor required, no default | `ActionProposal.kt:10` — `val actor: ActionActor` |
| 2 | blank hierarchy actor throws | `a_hierarchy_node_without_a_role_cannot_be_constructed`, `..._without_a_node_id_...` |
| 3 | ~~gate refuses blank identity~~ | **superseded** — made unrepresentable instead; `no_actor_variant_can_produce_a_blank_identity`, `a_system_actor_without_a_service_name_cannot_be_constructed` |
| 4 | all 11 sites supply a real actor | no `ActionProposal(` in the tree lacks `actor`; every identity traces to an existing id |
| 5 | dispositions unchanged | `the_actor_does_not_change_an_existing_verdict` — three actors, one verdict, allow and deny |

`git diff --check`: clean. No out-of-territory repair required.

### Honest scope note

The actor changes what every proposal *records*, but it does not yet change any
verdict — proven deliberately by criterion 5. **Its load-bearing use is Batch 8**,
where territory resolves by actor and an actor with no matching assignment is
refused. This batch is the identity substrate and is reported as such, not as
territory enforcement.
