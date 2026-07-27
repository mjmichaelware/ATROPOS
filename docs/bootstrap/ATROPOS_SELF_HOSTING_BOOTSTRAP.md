# ATROPOS Self-Hosting Autonomy Bootstrap

## Mission

Use OpenCode as the temporary external implementation runtime to make
ATROPOS capable of executing future Source Document 1–3 DAGs itself.

Do not execute, complete, or modify the Source Document 1–3 DAGs during this
bootstrap. Do not modify their completion ledgers.

Read AGENTS.md first and obey all repository safety, compile-cadence,
dirty-work-preservation, exact-path, verification, and no-commit/no-push laws.

Extend the existing ATROPOS queue, daemon, provider, verification, policy,
memory, and agent-run architecture. Do not create a parallel duplicate system.

Implement all ten milestones sequentially. After every milestone:

1. Run focused deterministic tests.
2. Run compileKotlin after the coherent slice.
3. Repair all failures.
4. Record files, commands, results, and evidence in:
   docs/bootstrap/ATROPOS_SELF_HOSTING_BOOTSTRAP_STATUS.md
5. Immediately continue to the next milestone.

Do not stop after producing recommendations or a next-session plan.

## Milestone 1 — Durable Provider-Session Supervision

Implement a provider-neutral durable supervisor owned by ATROPOS.

It must:

- launch, connect to, or resume agent runtimes;
- persist provider session identifiers;
- record PID/server/session/heartbeat state;
- detect dead servers and sessions;
- reconnect with bounded exponential backoff;
- distinguish provider idle, busy, failed, unavailable, and complete;
- use durable leases to prevent duplicate supervisors;
- survive terminal and viewer disconnection;
- avoid storing credentials;
- integrate with AgentDaemonService rather than duplicate it.

OpenCode is the first AGENT_RUNTIME implementation.

## Milestone 2 — Automatic Turn Continuation

Implement ATROPOS-owned continuation after a provider response ends.

A goal run must continue until one of these typed conditions occurs:

- VERIFIED_COMPLETE
- POLICY_BLOCKED
- EXTERNAL_INPUT_REQUIRED
- RETRY_BUDGET_EXHAUSTED
- CANCELLED
- TERMINAL_FAILURE

Normal completion of one model response is not completion of the goal.

Continuation must:

- resume the same durable run;
- include compact persisted state;
- avoid context-blind repetition;
- track continuation count;
- apply cooldown and retry limits;
- prevent duplicate continuation messages;
- stop only on a typed terminal condition.

## Milestone 3 — Permanent Execution Permission Policy

Move autonomy decisions into ATROPOS policy rather than interactive prompts.

Implement:

- safe automatic read/edit/test/build operations;
- explicit denial of force push, hard reset, git clean, secret output,
  uncontrolled external paths, paid auto-spend, and destructive actions;
- durable policy decisions and audit records;
- provider-runtime permission translation;
- no user prompt for actions already allowed by policy;
- typed POLICY_BLOCKED output for denied actions.

## Milestone 4 — DAG Selection, Dependencies, and Leasing

Implement a generic DAG execution service independent of SD1–3.

Support:

- node IDs and dependency edges;
- READY, CLAIMED, RUNNING, VERIFYING, COMPLETE, FAILED, BLOCKED,
  NOT_APPLICABLE, and CANCELLED states;
- deterministic dependency-ready selection;
- atomic leases with expiry and renewal;
- retry budgets;
- idempotent claims;
- stale-lease recovery;
- child jobs and attempts;
- parallel-ready detection without conflicting territory execution.

Integrate with the existing queue and daemon.

## Milestone 5 — Persistent Provider-Neutral Event Journal

Implement an append-only event spine for all providers.

Events must include run hierarchy coordinates where available:

- goalId
- projectId
- dagId
- atomId
- jobId
- attemptId
- runId
- parentRunId
- providerId
- providerSessionId
- sequence
- timestamp

Support events for:

- lifecycle/status
- visible text and reasoning summaries
- tool calls
- commands
- stdout/stderr
- file reads and mutations
- diffs
- tests
- verification
- todos
- heartbeats
- warnings/errors
- continuation
- child runs
- completion/failure/cancellation

Persist under:

.atropos/runs/<run-id>/

The append-only journal is authoritative. Mutable projections must use atomic
temporary-file replacement.

Never claim access to hidden chain-of-thought. Store only output actually
exposed by the provider.

## Milestone 6 — Terminal and Browser Observability

Add provider-neutral viewers consuming the same persisted event journal.

Terminal commands:

- /agent runs
- /agent watch latest
- /agent watch <run-id>
- /agent tree
- /agent transcript <run-id>
- /agent diff <run-id>
- /agent tests <run-id>

Local browser commands:

- /agent observe start
- /agent observe stop
- /agent observe status
- /agent observe open

Default dashboard:

127.0.0.1:4197

Provide SSE live updates for:

- run state;
- current DAG node;
- provider/model;
- tool activity;
- commands and output;
- edits and diffs;
- tests;
- todos;
- heartbeat;
- continuation count;
- completion state.

Closing either viewer must never stop execution.

## Milestone 7 — Crash Recovery and Session Restoration

Prove recovery after:

- terminal closure;
- ATROPOS restart;
- OpenCode restart;
- provider transport interruption;
- stale queue lease;
- interrupted write;
- partial provider response.

Recovery must:

- reload durable run state;
- reconcile provider sessions;
- avoid replaying completed mutations;
- resume the first incomplete safe step;
- preserve events and evidence;
- produce a typed recovery report.

## Milestone 8 — Isolated Worktrees and Rollback

Implement per-job isolated Git worktrees or an equivalent safe isolation layer.

Requirements:

- preserve every pre-existing tracked and untracked byte;
- assign allowed paths/territory;
- prevent cross-job collisions;
- capture baseline commit and dirty-state evidence;
- apply exact-path patches only;
- support deterministic rollback of the job’s own mutations;
- never run git reset --hard or git clean;
- never stage unrelated files;
- merge back only after verification.

## Milestone 9 — Verified Completion Before Advancement

A DAG node may become COMPLETE only when:

- implementation exists and is not a stub;
- required focused tests pass;
- deterministic verification passes;
- compile gate passes;
- territory and secret checks pass;
- acceptance evidence is persisted;
- expected outputs exist;
- no unresolved required dimension remains.

A failed gate must prevent dependent-node advancement.

Implement independent re-verification and detection of falsely completed nodes.

## Milestone 10 — Self-Hosting Acceptance and Handoff

Create a synthetic bootstrap acceptance DAG containing at least ten nodes with:

- dependencies;
- one deliberate compile failure;
- one deliberate provider interruption;
- one expired lease;
- one retry;
- one isolated worktree mutation;
- one deterministic rollback;
- terminal and browser observation;
- final verification.

Run it without manual intervention.

Acceptance requires:

- all synthetic nodes complete correctly;
- automatic continuation works across multiple provider turns;
- restart recovery works;
- no permission prompt occurs for allowed work;
- denied operations remain denied;
- viewers can detach and reconnect;
- events are complete and replayable;
- no duplicate job execution occurs;
- full tests pass;
- compileKotlin passes;
- final jar gate passes;
- git diff --check passes;
- no commit or push is created.

Document exact commands for using ATROPOS to ingest and execute a future
Source Document 1–3 DAG.

Only after every requirement above is independently verified, write this exact
line by itself in:

docs/bootstrap/ATROPOS_SELF_HOSTING_BOOTSTRAP_STATUS.md

BOOTSTRAP_COMPLETION: VERIFIED

Do not write the marker early.

Begin implementation immediately. Continue until all ten milestones and the
self-hosting acceptance DAG pass.
