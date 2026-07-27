# Batch 14 — attestation covers every provider entry point

**Priority advanced:** #2 — context attestation + typed drift failures as runtime law.

## Gap map

Every site that turns provider output into work:

| Site | Attested |
|---|---|
| `CommandRouter.kt:733` plain prompt | yes |
| `AgentService.kt:153,247` `/agent ask` | yes |
| `AgentService.kt:452` patch generation | via `AgentPromptContract` envelope |
| **`AgentRepairService.kt:206` repair** | **no** — zero attestation references in the file |
| **`core/swarm/DirectorOrchestrator.kt:19`** | **no envelope at all** |
| **`core/swarm/WorkerCodeSynthesizer.kt:34`** | **no envelope at all** |

Two distinct problems.

### 1. Repair is live and unattested

`AgentRepairService` generates replacement patches from provider output and hands
them to `AgentPatchStore`. It is the one live path where model output becomes a
repository mutation without its response ever being checked against the context
it was given.

### 2. The swarm package is an unattested route around every law

`DirectorOrchestrator.blueprintExecutionGraph` sends a bare prompt and parses the
reply into **a list of file paths to write**. `WorkerCodeSynthesizer` does the
same for file contents. No envelope, no attestation, no policy proposal, no
territory, no actor.

They have **zero callers** — `grep` across `src/main` and `src/test` returns
nothing outside the package itself. So this is dead code whose only behaviour is
to offer a route that bypasses decisions A, B, C and priority #2 at once.

The 100% Completion Blueprint §4 item 5 names them directly:

> **DirectorOrchestrator / WorkerCodeSynthesizer** — paths that allow models to
> invent file plans or write provider output directly. **Must be retired or
> rewritten** to respect territory, source authority, and independent verification.

Rewriting an uncalled path to satisfy four laws would be building a feature
nobody requested. Retiring it is the blueprint's other stated option and the
honest one: it deletes the bypass rather than decorating it.

## Territory (allowed files only)

```
src/main/kotlin/atropos/core/agent/AgentRepairService.kt   (attestation)
src/main/kotlin/atropos/core/swarm/                        (retired)
src/test/kotlin/atropos/core/agent/
```

## Acceptance criteria

1. `AgentRepairService` builds an envelope and verifies the response before the
   patch is accepted.
2. A repair response that fails attestation is refused — the unattested patch is
   not stored, and the typed failure is persisted like `AgentService` does.
3. An attested repair response still produces its patch.
4. `core/swarm/` no longer exists; no provider call in the tree lacks an
   envelope.
5. Nothing referenced the deleted package, proven by grep before and after.

## No stub

Criterion 2 is a new refusal with a real failure mode. The deletion removes
behaviour rather than adding a permissive path; criterion 5 proves it was
unreachable, so no capability is lost.

## Auditor result — PASS (with a pre-existing failure reported)

Compile: one end-of-batch compile, clean.

| Class | Tests | Failures |
|---|---|---|
| `atropos.core.agent.AgentRepairAttestationTest` (new) | 3 | 0 |
| `atropos.core.agent.*` (9 other suites) | 41 | 0 |
| `atropos.core.agent.AgentSecurityRedactionSurfaceTest` | 1 | **1 (pre-existing)** |

| # | Criterion | Evidence |
|---|---|---|
| 1 | repair verifies before accepting | `validatePatchAttempt` calls `attested(...)` before extraction |
| 2 | unattested repair refused, failure persisted | `a_repair_response_with_no_attestation_block_is_rejected`, `a_repair_response_attesting_to_a_different_context_is_rejected`; rejection writes the same `context_failure` record `AgentService` writes |
| 3 | attested repair still produces its patch | `an_attested_repair_response_is_accepted_and_keeps_its_diff` — and the attestation block does not leak into the stored diff |
| 4 | no unenveloped provider call | `core/swarm/` deleted; the only remaining `.complete(` sites outside adapters are `CommandRouter:733` and its corrective retry at `:766`, both inside the attestation block |
| 5 | deletion was unreachable | `grep` for `DirectorOrchestrator|WorkerCodeSynthesizer|OllamaClient` across `src/main` and `src/test` returned nothing outside the package, before and after |

`git diff --check`: clean.

### Pre-existing failure, reported not repaired

`AgentSecurityRedactionSurfaceTest > durable_agent_surfaces_redact_secrets`
fails with `AssertionError: Expected value to be false`. Confirmed on baseline
by stashing this batch and re-running — it fails identically without any of
these changes. It does not block test compilation, so the out-of-territory
repair protocol does not cover it.

### No stub

Criterion 2 is a new refusal with a real failure mode. The deletion removes
behaviour rather than adding a permissive path, and criterion 5 proves nothing
could reach it.
