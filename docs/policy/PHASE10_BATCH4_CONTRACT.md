# Phase 10 Batch 4 — build/test and smoke chokepoint for externally bounded agency

**Priority advanced:** #3 — Externally bounded agency.
**Blueprint phase:** 10.

> **Phase 10 is NOT complete after this batch.** Two evaluate-sites remain.

## Why this pair

| Line | Method | Action class | Command source |
|---|---|---|---|
| `AgentVerifier.kt:126` | `runVerificationCommand` | `BUILD_TEST` | hardcoded `./gradlew test jar --no-daemon` |
| `AgentSmokeRunner.kt:128` | smoke execution | `SMOKE` | **operator/provider-supplied string** |

`AgentSmokeRunner` is the one that matters for "never execute raw provider
prose": its command arrives as free text and is tokenised before execution.
`AgentVerifier` is paired with it because both share the same shape — a policy
check guarding a `ProcessBuilder` that scrubs secret-bearing environment
variables before spawning.

## Conversion pattern: pre-authorisation, not spawn-wrapping

Unlike Batches 1 and 2, the spawn is **not** moved inside the executor lambda.
Both sites build a process with elaborate environment scrubbing, then hand it to
a two-thread stream pump with futures and timeouts; wrapping that in a lambda
that must return a `String` would restructure working, security-relevant code
for no gain in authority.

Instead these use the same pre-authorisation shape as `AgentPatchStore.applyPatch`:
`agencyGate.evaluate(proposal)`, and return the refusal before the
`ProcessBuilder` line is reached. The gate still holds the decision; the
ordering is what prevents execution.

Because there is no spawn seam, "nothing ran" is proven **observationally**: a
forced refusal on a command that *would* produce output returns with a null exit
code and empty stdout. Had it spawned, both would be populated.

## Territory (allowed files only)

```
src/main/kotlin/atropos/core/policy/
src/main/kotlin/atropos/core/agent/AgentVerifier.kt      (evaluate-site only)
src/main/kotlin/atropos/core/agent/AgentSmokeRunner.kt   (evaluate-site only)
src/test/kotlin/atropos/core/agent/
```

## Acceptance criteria

1. Neither file contains a direct `policyEngine.evaluate(...)` call.
2. A refused verification returns `launchError` = the policy reason, with
   `exitCode == null` — nothing spawned.
3. A refused smoke run returns `refusalReason` = the policy reason, with
   `exitCode == null` and empty stdout — nothing spawned.
4. `AgentSmokeRunner.validate` still refuses chaining, redirects and multiline
   input **before** any proposal is made; bounded agency does not replace it.
5. Proposals reproduce the previous `ExecutionPolicyRequest` field for field.
6. Exactly one `ExecutionPolicyEngine` per class, reached through its gate.
7. The environment-scrubbing spawn blocks are untouched.

## Non-duplication law (preserved)

No new engine, verifier, or queue. `VerificationActionProposals` is
construction only.

## Explicitly out of scope

`AgentDaemonService` / `AgentQueueService` (Batch 5) · size-debt decoupling ·
Territory · Auditor · SpecGraph.

## Remaining evaluate-site ledger

| # | File | Status |
|---|---|---|
| 0 | `cli/shell/ShellCommandRunner.kt` | converted — Batch 1 |
| 1 | `core/agent/AgentPatchStore.kt` | converted — Batch 2 |
| 2 | `core/agent/AgentService.kt` | converted — Batch 3 |
| 3 | `core/agent/AgentRepairService.kt` | converted — Batch 3 |
| 4 | `core/agent/AgentVerifier.kt` | **converted — this batch** |
| 5 | `core/agent/AgentSmokeRunner.kt` | **converted — this batch** |
| 6 | `core/agent/AgentDaemonService.kt` | unconverted — Batch 5 |
| 7 | `core/agent/AgentQueueService.kt` | unconverted — Batch 5 |

## Auditor result — PASS

Compile: one end-of-batch compile, clean.

| Class | Tests | Failures |
|---|---|---|
| `atropos.core.agent.AgentSmokeBoundedAgencyTest` (new) | 6 | 0 |
| `atropos.core.agent.AgentPatchBoundedAgencyTest` | 8 | 0 |
| `atropos.core.policy.*` (3 classes) | 11 | 0 |
| **total** | **25** | **0** |

| # | Criterion | Evidence |
|---|---|---|
| 1 | no direct `evaluate` | grep in both files → no matches |
| 2 | refused verification never launches | `a_refused_verification_never_launches_gradle` — null exit, empty stdout |
| 3 | refused smoke never runs | `a_refused_smoke_command_never_runs`, `an_unapproved_smoke_command_never_runs` |
| 4 | `validate` still refuses first | `syntactic_validation_still_refuses_before_any_proposal` (allow-everything engine, still refused), `network_smoke_commands_stay_refused_under_real_policy` |
| 5 | proposals reproduce requests | `proposals_reproduce_the_previous_policy_requests`, including that `BUILD_TEST` and `SMOKE` are not interchangeable |
| 6 | one engine per class | 1 `ExecutionPolicyEngine(` in each |
| 7 | env-scrub blocks untouched | scrub predicate still present in both |

`git diff --check`: clean. No out-of-territory repair required.

### Correction made during the Worker pass

The first draft of the smoke tests used `echo`, which `validate()` rejects as an
unsupported command — so they were proving validation, not the gate. Switched to
`printf`, which is on the allowlist and produces output, so empty stdout now
isolates the gate as the thing that stopped execution. The test was corrected;
no production behaviour changed.
