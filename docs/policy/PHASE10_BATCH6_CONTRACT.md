# Batch 6 — one permission authority: demote AutonomyPolicyEngine, put DAG execution under the gate

**Binding decision advanced:** A — single permission authority.
**Priority protected:** #3 — externally bounded agency.

## What is actually wrong

`DagExecutionService` executes DAG nodes by spawning **`ProcessBuilder("sh", "-c", payload)`** at four sites (lines 176, 206, 235, 273) and mutating files at a fifth. Its only permission check is:

```kotlin
policyEngine.evaluate(AutonomyActionClass.DAG_CONTROL, ...)   // line 99
```

`AutonomyPolicyEngine`'s rule table maps `DAG_CONTROL` to `allowed = true`,
unconditionally. So the DAG can run **arbitrary shell** — chained commands,
redirects, `curl`, writes into `.git` or `build/` — with no effective gate,
entirely bypassing `ExecutionPolicyEngine`. That is the second permit authority,
and it permits everything.

`AutonomyPolicyEngine` also exposes `evaluateExecutionPolicy` +
`mapExecutionToAutonomy`, a bridge that translates an `ExecutionPolicyRequest`
into its own verdict — the clearest expression of it being a rival authority.

## Territory (allowed files only)

```
src/main/kotlin/atropos/core/policy/AutonomyPolicyExtensions.kt   (demotion)
src/main/kotlin/atropos/core/dag/DagExecutionService.kt           (authorization site only)
src/main/kotlin/atropos/core/dag/DagNodeProposals.kt              (new, atomic)
src/main/kotlin/atropos/core/verification/VerifiedCompletionGate.kt  (dead field removal)
src/main/kotlin/atropos/cli/commands/AgentCommand.kt              (advisory call site only)
src/test/kotlin/atropos/core/dag/
```

## Acceptance criteria

1. `DagExecutionService` no longer consults `AutonomyPolicyEngine`. Node
   execution is authorised by `BoundedAgencyGate` from a real `ActionProposal`.
2. **Real refusal, proven:** a DAG node whose payload chains commands, redirects,
   or invokes network tools is `POLICY_BLOCKED` — it is refused today and was
   permitted before this batch.
3. A file-mutation node with **no declared territory** is refused (the engine
   denies `FILE_MUTATION` with no target paths). Previously permitted.
4. A legitimate node (`./gradlew test`, a scoped file write) is still `ALLOWED` —
   the gate is not a blanket denial.
5. `AutonomyPolicyEngine` can no longer be mistaken for a permit:
   `evaluateExecutionPolicy` and `mapExecutionToAutonomy` are **deleted**,
   `evaluate` becomes `advise`, and `allowed`/`policyBlocked` become
   `advisoryAllowed`/`advisoryBlocked`.
6. `VerifiedCompletionGate`'s unused `policyEngine` field is removed.
7. Tree-wide: no caller treats an `AutonomyPolicyDecision` as authorisation for
   a side effect.

## No stubs

Every change alters observable behaviour: nodes that ran before are refused now,
and the deleted bridge methods are removed outright rather than emptied. The
mapping file makes decisions about action class and target paths that the engine
then acts on; it is not a pass-through.

## Found, recorded, NOT fixed here

`DagExecutionService.executeCheck` (line 260) is a **constant-true gate**:
`POLICY_CHECK`, `SECRET_CHECK` and `TERRITORY_CHECK` nodes all unconditionally
complete with `"check passed"` without checking anything. This is pre-existing
theater and squarely violates the no-stub law, but making three real checks is a
distinct capability (priority #5, deterministic failure-first verification), not
a permission-authority change. Scheduled as its own batch — **not** left as
something I introduced.

Because those three actions execute nothing today, they make no proposal; the
gate is applied to every action that actually executes.

## Out of scope

Actor identity (Batch 7) · territory at dispatch (Batch 8) · the four `sh -c`
call sites themselves (the gate authorises before dispatch reaches them) ·
SpecGraph.

## Auditor result — PASS

Compile: one end-of-batch compile, clean (plus one recompile after the field
rename in `AgentCommand`).

| Class | Tests | Failures |
|---|---|---|
| `atropos.core.dag.DagNodeProposalsTest` (new) | 10 | 0 |
| `atropos.core.policy.*` (4 classes) | 16 | 0 |
| **total** | **26** | **0** |

| # | Criterion | Evidence |
|---|---|---|
| 1 | DAG authorised by the gate | no `AutonomyPolicyEngine` reference remains in `DagExecutionService` |
| 2 | real refusal, newly enforced | `a_chained_command_node_is_refused`, `a_redirecting_command_node_is_refused`, `a_network_command_node_is_refused`, `a_build_node_cannot_smuggle_a_second_command` |
| 3 | untargeted mutation refused | `a_file_mutation_node_with_no_territory_is_refused`, `..._targeting_a_forbidden_path_is_refused` |
| 4 | not a blanket denial | `legitimate_nodes_are_still_allowed` (4 cases) |
| 5 | bridge deleted | `evaluateExecutionPolicy` / `mapExecutionToAutonomy` gone from source (only a KDoc mention of the removal remains) |
| 6 | dead field removed | no `policyEngine` in `VerifiedCompletionGate` |
| 7 | no permit misuse | the sole remaining caller is `/agent policy`, renamed `autonomyAdvisor`, output labelled "advisory only — not an execution permit" |

`git diff --check`: clean. No out-of-territory repair required.

### No stub introduced

Behaviour genuinely changed. `a_build_node_cannot_smuggle_a_second_command` is
the sharpest case: `./gradlew build && curl …` ran before this batch and is
refused now. `DagNodeProposals` decides action class and target paths that the
engine acts on — the `SHELL`-not-`BUILD_TEST` choice is what closes the
first-token smuggling hole — so it is not a pass-through.

### Found, recorded, not fixed

`DagExecutionService.executeCheck` is constant-true: `POLICY_CHECK`,
`SECRET_CHECK` and `TERRITORY_CHECK` all complete with `"check passed"` without
checking anything. Pre-existing; scheduled as its own batch under priority #5.
`only_the_non_executing_actions_skip_the_gate` pins the skip list so nothing new
can join it silently.
