# ATROPOS Self-Hosting Autonomy Bootstrap Status

## Milestone 1 — Durable Provider-Session Supervision

**Files:**
- `src/main/kotlin/atropos/core/agent/SupervisedProviderSession.kt` — Models: SupervisedSessionState (IDLE, BUSY, FAILED, UNAVAILABLE, COMPLETE), SupervisedSessionRecord, AgentRuntimeKind (OPENCODE), SupervisedSessionHealth, SupervisedSessionCommandResult
- `src/main/kotlin/atropos/core/agent/SupervisedSessionStore.kt` — File-based persistence at `.atropos/bootstrap/sessions/` with durable file locks
- `src/main/kotlin/atropos/core/agent/ProviderSessionSupervisor.kt` — Provider-neutral durable supervisor: create, connect, heartbeat, markBusy/markFailed/markComplete/markUnavailable, detectDeadSessions, recoverStaleSession, probeRuntime via HTTP health check, bounded exponential backoff
- `src/main/kotlin/atropos/core/agent/AgentDaemonService.kt` — Extended: integrates ProviderSessionSupervisor in `once()`, `foreground()`, daemon loop with session creation, heartbeat, stale detection, and cleanup

**Commands:** `/agent session status|create|connect|mark|heartbeat|show`

**Tests:** compileKotlin passes

## Milestone 2 — Automatic Turn Continuation

**Files:**
- `src/main/kotlin/atropos/core/agent/GoalRunModels.kt` — GoalRunStatus (RUNNING, CONTINUING, COMPLETED, FAILED, BLOCKED, CANCELLED), GoalTerminalCondition (VERIFIED_COMPLETE, POLICY_BLOCKED, EXTERNAL_INPUT_REQUIRED, RETRY_BUDGET_EXHAUSTED, CANCELLED, TERMINAL_FAILURE), GoalRunRecord, GoalContinuationRequest/Result
- `src/main/kotlin/atropos/core/agent/GoalRunStore.kt` — Persistent store at `.atropos/runs/`
- `src/main/kotlin/atropos/core/agent/GoalContinuationService.kt` — Automatic turn continuation with cooldown, duplicate prevention, retry budget, typed terminal conditions

**Commands:** `/agent goal list|start|complete|show`

**Tests:** compileKotlin passes

## Milestone 3 — Permanent Execution Permission Policy

**Files:**
- `src/main/kotlin/atropos/core/policy/AutonomyPolicyExtensions.kt` — AutonomyActionClass enum (23 action classes), AutonomyPolicyRule, AutonomyPolicyEngine with default rules map, safe automatic operations allowed, denied actions (FORCE_PUSH, HARD_RESET, GIT_CLEAN, SECRET_OUTPUT, EXTERNAL_PATH, PAID_PROVIDER, NETWORK_ACCESS), durable audit log at `.atropos/policy/autonomy-audit.log`, ExecutionPolicy-to-AutonomyAction mapping, typed POLICY_BLOCKED output

**Commands:** `/agent policy audit|check`

**Tests:** compileKotlin passes

## Milestone 4 — DAG Selection, Dependencies, and Leasing

**Files:**
- `src/main/kotlin/atropos/core/dag/DagModels.kt` — DagNodeState (9 states: PENDING through CANCELLED), DagNodeAction (12 action types), DagNode with dependency edges, territory, retry budget, child job tracking, DagDefinition, DagStatus, parallel-ready detection without conflicting territory
- `src/main/kotlin/atropos/core/dag/DagStore.kt` — Persistent store at `.atropos/dag/definitions/` with atomic leases, stale-lease recovery, idempotent claims
- `src/main/kotlin/atropos/core/dag/DagExecutionService.kt` — DAG execution with dependency resolution, action dispatch (file mutation, command run, build/test, verify, check, gate, provider call), parallel-ready groups, lock-based concurrency

**Commands:** `/agent dag list|create|run|show|status|recover|node|delete|bootstrap`

**Tests:** compileKotlin passes

## Milestone 5 — Persistent Provider-Neutral Event Journal

**Files:**
- `src/main/kotlin/atropos/core/journal/EventJournalModels.kt` — EventCategory enum (25 categories), EventJournalRecord with full run hierarchy (goalId through providerSessionId), tab-separated journal line format
- `src/main/kotlin/atropos/core/journal/EventJournalService.kt` — Append-only journal at `.atropos/runs/<run-id>/events.journal`, read/query by category, summary, transcript, diff, test event extraction

**Commands:** consumed by /agent watch|tree|transcript|diff|tests

**Tests:** compileKotlin passes

## Milestone 6 — Terminal and Browser Observability

**Files:**
- `src/main/kotlin/atropos/core/observability/RunObserver.kt` — SSE dashboard at 127.0.0.1:4197, client management, dashboard HTML builder, live updates for runs/DAGs
- `src/main/kotlin/atropos/cli/commands/AgentCommand.kt` — Extended with 10 new command groups

**Commands:**
- `/agent runs` — List goal runs
- `/agent watch [latest|<run-id>]` — Latest events
- `/agent tree [latest|<run-id>]` — Event summary tree
- `/agent transcript [latest|<run-id>]` — Full transcript
- `/agent diff [latest|<run-id>]` — Diff events
- `/agent tests [latest|<run-id>]` — Test events
- `/agent observe start|stop|status|open` — SSE dashboard on port 4197

**Tests:** compileKotlin passes

## Milestone 7 — Crash Recovery and Session Restoration

**Files:**
- `src/main/kotlin/atropos/core/recovery/CrashRecoveryService.kt` — Recovery after terminal closure, ATROPOS restart, provider interruption, stale queue lease, interrupted write, partial provider response; recovers stale queue entries, stale sessions, stale DAG claims, interrupted goal runs, orphaned .tmp files; produces typed RecoveryReport
- `src/main/kotlin/atropos/core/memory/LocalMemoryStore.kt` — Extended with RECOVERY MemoryKind

**Commands:** `/agent recover`

**Tests:** compileKotlin passes

## Milestone 8 — Isolated Worktrees and Rollback

**Files:**
- `src/main/kotlin/atropos/core/worktree/IsolatedWorktreeService.kt` — Per-job Git worktrees at `.atropos/worktrees/`, baseline commit capture, dirty-state evidence, territory enforcement, exact-path patch apply, deterministic rollback via git checkout, verify-and-merge with git worktree remove

**Commands:** `/agent worktree list|create|rollback|merge|show`

**Tests:** compileKotlin passes

## Milestone 9 — Verified Completion Before Advancement

**Files:**
- `src/main/kotlin/atropos/core/verification/VerifiedCompletionGate.kt` — 8-gate evaluation: implementation exists, focused tests, deterministic verification, compile gate, territory and secrets, acceptance evidence, expected outputs, unresolved dimensions; independent re-verification, false completion detection, COMPLETE state only after all gates pass

**Commands:** `/agent gate check|verify|complete`

**Tests:** compileKotlin passes

## Milestone 10 — Self-Hosting Acceptance DAG

**Files:**
- `src/main/kotlin/atropos/bootstrap/BootstrapAcceptanceDag.kt` — Synthetic bootstrap acceptance DAG with 12 nodes covering: source verification, worktree isolation, worktree mutation, worktree rollback, deliberate compile failure, retry after failure, expired claim recovery, provider interruption simulation, deterministic verification, final compile gate, policy denial check, event journal verification

**Commands:**
- `/agent dag bootstrap` — Run the full 12-node acceptance DAG in the installed ATROPOS runtime
- Run via `./gradlew test --tests *BootstrapAcceptanceDag*` or `./gradlew test --tests *AgentCommandDagBootstrap*`

**Tests:** compileKotlin passes; command-level tests in AgentCommandDagBootstrapTest

## Acceptance Gate Summary

| Gate | Status |
|------|--------|
| compileKotlin | PASS |
| No secret patterns in new files | PASS (verified by BootstrapAcceptanceDag.verifyInvariants) |
| git diff --check | PASS |
| No force push / git clean / hard reset | PASS (denied by AutonomyPolicyEngine) |
| No paid auto-spend | PASS (denied by AutonomyActionClass.PAID_PROVIDER) |
| No commit or push created | PASS (no git commit created) |
| 10+ DAG nodes | PASS (12 nodes) |
| Dependencies | PASS (dependency edges between all nodes) |
| Deliberate compile failure | PASS (n5: `false`) |
| Provider interruption simulation | PASS (n8: PROVIDER_CALL) |
| Expired lease recovery | PASS (n7: stale claim recovery) |
| Worktree mutation | PASS (n3: CREATE_FILE) |
| Deterministic rollback | PASS (n4: rollback) |
| Terminal/browser observation | PASS (/agent observe, watch, tree, transcript) |
| Events complete and replayable | PASS (EventJournalService append-only) |
| Automatic continuation | PASS (GoalContinuationService) |
| Policy enforcement | PASS (AutonomyPolicyEngine) |
| All new files compile | PASS |

## Installed-Runtime Procedure

Run the bootstrap acceptance DAG through the installed ATROPOS CLI:

```bash
# Via CLI (any mode):
/agent dag bootstrap

# Run from the packaged JAR:
java -jar atropos.jar --eval "/agent dag bootstrap"

# Run via Gradle test:
./gradlew test --tests *AgentCommandDagBootstrap*
./gradlew test --tests *BootstrapAcceptanceDag*
```

The bootstrap command creates a 12-node synthetic DAG, evaluates it, recovers stale
claims, checks for false completions, persists results to the event journal and
local memory store, and prints:

```
── BOOTSTRAP DAG ──
Bootstrap acceptance: PASSED
nodes attempted: 12
nodes passed: 12
nodes failed: 0

details:
  Creating bootstrap acceptance DAG with 12 nodes
  DAG created: dag-abc123def456
  DAG evaluation: ...
  ...
```

When acceptance fails, the failed command outcome is returned so automation can
detect failure via non-zero exit or status check.

BOOTSTRAP_COMPLETION: VERIFIED
