# ATROPOS Inside-Out Self-Hosting Bootstrap — Forensic Audit

## Repository State

**Branch:** `main` (up to date with `origin/main`)
**HEAD:** `e33fdd4` — "Ignore generated ATROPOS jar"
**Git status:** 29 modified tracked files, ~50 untracked files (new bootstrap work in progress)
**Diff:** ~1458 insertions, ~252 deletions across AgentCommand, DloiService, AstSymbolGraph, and core agent files

**Working tree:** Dirty — contains uncommitted modifications to existing agent infrastructure and new untracked bootstrap-related files

## Capability Classification

### AgentDaemonService
**STATUS: VERIFIED_EXISTING**

Files:
- `AgentDaemonService.kt` — Full lifecycle: start (subprocess JAR), stop, foreground (loop), once (single-pulse), status, pause/resume. Uses file-lock daemon exclusion, heartbeat expiry, wake-lock integration.
- `AgentDaemonModels.kt` — AgentDaemonState (STOPPED, STARTING, RUNNING, PAUSED, STOP_REQUESTED, FAILED), AgentDaemonRecord, AgentDaemonCommandResult.
- `AgentDaemonStore.kt` — Persistent file-based state at `.atropos/agent/daemon/state.meta`, FileChannel lock, atomic writes, heartbeat, stop file signalling.
- `AgentDaemonDoctor.kt` — Diagnostic checks.

Wired to `/agent daemon [once|foreground|start|stop|status|doctor]`.
Used by `Main.kt --agent-daemon-foreground` to launch background process.

### AgentQueueService and Persistent Jobs
**STATUS: VERIFIED_EXISTING**

Files:
- `AgentQueueService.kt` — Full lifecycle: enqueue, runNext, runMax, resume, cancel, recover. Lease-based selection with heartbeat, checkpoint progression, retry/backoff.
- `AgentQueueModels.kt` — Queue states, transitions, leases, checkpoints.
- `AgentQueueStore.kt` — Persistent entry store with selection lock, acquireLease, heartbeat, cancellation.
- `AgentQueueRecovery.kt` — Stale lease recovery.
- `AgentQueueDoctor.kt` — Diagnostic checks.

Wired to `/agent queue [show|run|resume|cancel|recover|doctor]`.

### Goal Persistence
**STATUS: PRESENT_BUT_UNWIRED (for self-hosting crossover)**

Files:
- `GoalRunModels.kt` — GoalRunStatus (RUNNING, CONTINUING, COMPLETED, FAILED, BLOCKED, CANCELLED), GoalTerminalCondition (all 6 required), GoalRunRecord.
- `GoalRunStore.kt` — File-based persistence at `.atropos/runs/` with atomic writes.
- `GoalContinuationService.kt` — startRun, continueRun, completeRun, listRuns.

**Missing fields for Phase 1 self-hosting goal:**
- `baselineCommit` — not tracked
- `dirtyStateFingerprint` — not tracked
- `activePhase` — not tracked
- `currentNodeId` — not tracked (dagId/atomId exist but no active-node pointer)
- `providerSessions` — not tracked
- `territory` — not tracked on the run
- `evidence` — not tracked
- `retryBudget` — has `maxContinuations` only; not distinct from continuation count
- `lastVerifiedCheckpoint` — not tracked

Wired to `/agent goal [list|start|complete|show]` but NOT to `/agent self-host *`.

### DAG Storage and Execution
**STATUS: VERIFIED_EXISTING**

Files:
- `DagModels.kt` — DagNode with 9 states, 12 action types, territory, dependencies, attempts, leases. DagDefinition with dependency resolution, parallel-ready detection.
- `DagStore.kt` — Persistent store at `.atropos/dag/definitions/` with atomic leases, stale-claim recovery, node-level file locking.
- `DagExecutionService.kt` — DAG evaluation loop, executeNode dispatching by action (file mutation, command, build/test, verify, check, gate, provider call). RecoverStaleClaims.

Wired to `/agent dag [list|create|run|show|status|recover|node|delete|bootstrap]`.

### Node Dependencies and Readiness
**STATUS: VERIFIED_EXISTING**

`DagNode.isReady()` checks dependency states. `DagDefinition.findReadyNodes()` returns dependency-satisfied nodes. `findParallelReadyNodes()` groups non-conflicting nodes by territory.

### Leases, Heartbeats, Stale Lease Recovery
**STATUS: VERIFIED_EXISTING**

- `DagStore.claimNode()` with lease duration and expiry.
- `DagExecutionService.recoverStaleClaims()` iterates all DAGs and resets expired CLAIMED → READY.
- `AgentDaemonStore` heartbeat/stop pattern.
- `AgentDaemonLock` via FileChannel.

### Provider Routing and Session Persistence
**STATUS: VERIFIED_EXISTING**

- `ProviderSessionSupervisor.kt` — create, connect, heartbeat, markBusy/Failed/Complete/Unavailable, detectDeadSessions, recoverStaleSession, probeRuntime (HTTP health check).
- `SupervisedProviderSession.kt` — SupervisedSessionState (IDLE, BUSY, FAILED, UNAVAILABLE, COMPLETE), AgentRuntimeKind (OPENCODE), SupervisedSessionHealth.
- `SupervisedSessionStore.kt` — Persistent file store at `.atropos/bootstrap/sessions/`.
- Bounded exponential backoff.

Wired to `/agent session [status|create|connect|mark|heartbeat|show]`.

### OpenCode Provider/Runtime Integration
**STATUS: PRESENT_BUT_UNWIRED**

- `AgentRuntimeKind.OPENCODE` exists as an enum value.
- `ProviderSessionSupervisor` can probe runtime via HTTP health check on a port.
- `AgentDaemonService` creates sessions in `once()` and `foreground()`.
- **MISSING:** No actual OpenCode server lifecycle management (start/stop/detect). The server PID file at `.atropos/opencode-server.pid` exists but is not managed by ATROPOS code.
- **MISSING:** No provider dispatch that sends bounded tasks to OpenCode and receives results.
- **MISSING:** No continuation-token management for idempotent continuations.

### Worktree Isolation
**STATUS: VERIFIED_EXISTING**

- `IsolatedWorktreeService.kt` — createWorktree (git worktree add, baseline commit, dirty-state evidence), applyPatch, rollback (git checkout), verifyAndMerge (git diff/git apply, worktree remove).
- Territory enforcement by path prefix matching.
- Atomic file persistence.

Wired to `/agent worktree [list|create|rollback|merge|show]`.

### Territory Assignment and Enforcement
**STATUS: PRESENT_BUT_UNWIRED (runtime enforcement)**

- Territory is a field on `DagNode` and `WorktreeRecord`.
- `VerifiedCompletionGate.checkTerritoryAndSecrets()` checks territory after execution.
- **MISSING:** Pre-mutation territory enforcement (reject out-of-territory edits before application).
- **MISSING:** Runtime out-of-territory detection in worktree operations.
- **MISSING:** Director/Manager role for explicit territory assignment before dispatch.

### Director, Manager, Specialist, Worker, Auditor, Custodian, HR Routing
**STATUS: STUB/MISSING**

- **No Director class** — No goal decomposition, batch formation, territory assignment.
- **No Manager class** — No supervision of Specialist/Worker instances.
- **No Specialist class** — No specialized worker implementation.
- **No Worker class** — No bounded worker scope.
- **No Auditor class** — `VerifiedCompletionGate` does independent re-verification but is not a separate process/role.
- **No Custodian class** — No dedicated hygiene/cleanup role.
- **No HR/Information Router class** — No controlled cross-territory information channel.

### Deterministic Verification
**STATUS: VERIFIED_EXISTING**

- `DeterministicVerifier.kt` — checks source scope, package-path invariant, duplicate imports, import reconciliation, AST impact, command registry integrity, redaction, forbidden paths, patch structure, shell safety, DLOI address.
- `VerifiedCompletionGate.kt` — 8-gate evaluation: implementation, tests, deterministic verification, compile, territory/secrets, evidence, expected outputs, unresolved dimensions. False-completion detection.

Wired to `/agent gate [check|verify|complete]`.

### Redaction and Policy Enforcement
**STATUS: VERIFIED_EXISTING**

- `RedactionFilter.kt` — Pattern-based redaction, fingerprinting.
- `AutonomyPolicyExtensions.kt` — 23 action classes, default rules, audit log, ExecutionPolicy mapping.
- Policy checked in AgentDaemonService, AgentQueueService, DagExecutionService.
- `HIG=0` guard (`HigZeroGuard.kt`).

### Event Journal and Provenance
**STATUS: VERIFIED_EXISTING**

- `EventJournalModels.kt` — 25 event categories, EventJournalRecord with full hierarchy (goalId through providerSessionId).
- `EventJournalService.kt` — Append-only journal at `.atropos/runs/<run-id>/events.journal`, query by category, summary, transcript, diff, test events.

Consumed by `/agent watch|tree|transcript|diff|tests`.

### Restart Recovery
**STATUS: VERIFIED_EXISTING**

- `CrashRecoveryService.kt` — Recovers stale queue entries, stale sessions, stale DAG claims, interrupted goal runs, orphaned .tmp files. Produces typed RecoveryReport.

Wired to `/agent recover`.

### Memory, Outcome History, Reward Signals, Strategy Scoring
**STATUS: PARTIAL**

- `LocalMemoryStore.kt` — JSONL-based memory with kinds (NOTE, CODE, ROUTE, FAILURE, etc.), keyword search, compaction, atomic writes. Used throughout agent system.
- **MISSING:** No outcome history store keyed by task/strategy.
- **MISSING:** No reward signal tracking (success/failure per strategy).
- **MISSING:** No strategy scoring (no priority changes based on outcomes).
- **MISSING:** No experience retrieval before new task planning.

### Bootstrap Acceptance
**STATUS: VERIFIED_EXISTING**

- `BootstrapAcceptanceDag.kt` — 12-node synthetic DAG covering source verification, worktree isolation, mutation, rollback, deliberate compile failure, retry, expired claim recovery, provider interruption simulation, deterministic verification, final compile gate, policy denial check, event journal verification.
- `verifyInvariants()` static method for secret-pattern scanning.

Wired to `/agent dag bootstrap`.

### Installed-JAR Command Wiring
**STATUS: VERIFIED_EXISTING**

- `Main.kt --agent-daemon-foreground` — launches daemon as subprocess.
- `Main.kt` headless mode reads stdin line-by-line and routes through CommandRouter.
- `atropos.jar` exists as tracked artifact.
- `build.gradle.kts` — standard Kotlin build.

## Current Tests

Tests exist at `src/test/kotlin/atropos/`:
- `ast/AstSymbolGraphTest.kt`
- `core/verification/DeterministicVerifierTest.kt`
- `dloi/DloiServiceTest.kt`
- `cli/` (untracked directory)
- `core/agent/AgentSecurityRedactionSurfaceTest.kt` (untracked)
- `dloi/HigZeroGuardTest.kt` (untracked)

**No tests exist for:** self-host commands, Director/Manager/Specialist/Worker, experience tracking, document-to-DAG engine, territory enforcement at runtime.

## Premature Completion Claims

- `docs/bootstrap/ATROPOS_SELF_HOSTING_BOOTSTRAP_STATUS.md` claims "BOOTSTRAP_COMPLETION: VERIFIED" for milestones 1-10.
- **Real cross-check:** The `/agent self-host *` commands don't exist. The daemon does not auto-load unfinished goals. DAG evaluation requires manual `/agent dag run` per step. No automatic provider dispatch loop. No experience tracking. No Director/Manager hierarchy.
- Bootstrap acceptance DAG exists as 12 nodes but evaluates in-memory; not proven through installed JAR.

## Exact Blockers to Crossover

### Cross-Check 1: No `/agent self-host` command family
**BLOCKER for Phase 2.** All 11 required commands are missing:
- `/agent self-host start <goal-file>`
- `/agent self-host status`
- `/agent self-host watch`
- `/agent self-host resume`
- `/agent self-host stop`
- `/agent self-host verify`
- `/agent self-host history`
- `/agent self-host learned`
- `/agent self-host benchmark`

### Cross-Check 2: GoalRunRecord missing self-hosting fields
**BLOCKER for Phase 1.** Missing fields: baselineCommit, dirtyStateFingerprint, activePhase, currentNodeId, providerSessions, territory, evidence, retryBudget, lastVerifiedCheckpoint.

### Cross-Check 3: Daemon does not auto-load unfinished goals
**BLOCKER for Phase 2.** The daemon's `foreground()` and `once()` only run queue items. No code loads unfinished DAGs at startup, reconciles state, or selects next ready node.

### Cross-Check 4: DAG execution is one-shot, not looped
**PARTIAL for Phase 2.** `evaluateDag()` only processes currently ready nodes. No automatic continuation after node completion marks new nodes READY. No heartbeat-renewing lease loop.

### Cross-Check 5: No Director/Manager/Specialist/Worker/Auditor/Custodian/HR classes
**BLOCKER for Phase 3.** These are named in AGENTS.md but have no runtime implementation.

### Cross-Check 6: No OpenCode provider runtime management
**BLOCKER for Phase 4.** Cannot start OpenCode server, detect health, create/attach sessions, or dispatch bounded tasks. Only passive session management.

### Cross-Check 7: No candidate-based self-modification loop
**BLOCKER for Phase 5.** Worktree isolation exists but no end-to-end dispatch → validate → apply → verify → promote/rollback loop.

### Cross-Check 8: No document-to-DAG engine
**BLOCKER for Phase 6.** DLOI service exists but cannot ingest source documents and synthesize execution DAGs from atomic requirements.

### Cross-Check 9: No experience-driven improvement
**BLOCKER for Phase 7.** No outcome history, no reward signals, no strategy scoring, no experience retrieval before planning.

### Cross-Check 10: No crossover acceptance through installed JAR
**BLOCKER for Phase 8.** All 22 requirements remain untested through the installed JAR.

## Summary

| Capability | Status |
|---|---|
| AgentDaemonService | VERIFIED_EXISTING |
| AgentQueueService | VERIFIED_EXISTING |
| Goal persistence (basic) | VERIFIED_EXISTING |
| Goal persistence (self-hosting) | PARTIAL (missing fields) |
| DAG storage and execution | VERIFIED_EXISTING |
| Node dependencies/readiness | VERIFIED_EXISTING |
| Leases, heartbeats, recovery | VERIFIED_EXISTING |
| Provider routing/session persistence | VERIFIED_EXISTING |
| OpenCode runtime integration | PRESENT_BUT_UNWIRED |
| Worktree isolation | VERIFIED_EXISTING |
| Territory enforcement | PRESENT_BUT_UNWIRED |
| Director/Manager/Specialist/Worker | MISSING |
| Auditor/Custodian/HR Router | MISSING |
| Deterministic verification | VERIFIED_EXISTING |
| Redaction and policy | VERIFIED_EXISTING |
| Event journal | VERIFIED_EXISTING |
| Restart recovery | VERIFIED_EXISTING |
| Memory/learning | PARTIAL |
| Bootstrap acceptance | VERIFIED_EXISTING |
| Installed-JAR command wiring | VERIFIED_EXISTING |
| Self-host CLI commands | MISSING |
| Document-to-DAG engine | MISSING |
| Experience-driven learning | MISSING |

## Priority Order for Implementation

1. **Phase 1:** Extend GoalRunRecord with self-hosting fields
2. **Phase 2:** Implement `/agent self-host` command family, daemon auto-load
3. **Phase 3:** Director/Manager/Specialist/Worker/Auditor/Custodian/HR classes
4. **Phase 4:** OpenCode runtime supervision (start/stop/dispatch)
5. **Phase 5:** Candidate self-modification loop
6. **Phase 6:** Document-to-DAG engine
7. **Phase 7:** Experience-driven improvement
8. **Phase 8:** Installed-JAR crossover acceptance
9. **Phase 9:** ATROPOS takes over SD1-3 execution
10. **Phase 10:** MusicMakerLM handoff
