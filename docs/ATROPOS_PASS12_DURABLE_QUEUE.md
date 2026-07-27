# ATROPOS Pass 12 Durable Queue

Pass 12 adds a synchronous, operator-triggered durable queue around the Pass 11 agent job pipeline. It does not add a daemon, scheduler, wake lock, remote worker, swarm, worktree orchestration, or background shell.

## Purpose

The queue lets an operator record bounded agent work now and execute it later after process restart. Queue entries supervise jobs; they do not replace `AgentJobRecord`.

Providers may supply plans, reasoning, patches, and repairs. ATROPOS owns queue state, leases, retries, cancellation, patch application, verification, smoke execution, recovery, and final status.

## State vs Checkpoint

Queue state answers whether an entry can be selected and whether it is terminal.

Execution checkpoint answers which durable stage has completed.

States:

- `QUEUED`
- `LEASED`
- `RUNNING`
- `RETRY_WAIT`
- `COMPLETED`
- `FAILED`
- `REFUSED`
- `CANCELLED`
- `CORRUPT`

Checkpoints:

- `QUEUED`
- `CLAIMED`
- `PREFLIGHT_PASSED`
- `PLANNED`
- `PATCH_GENERATED`
- `PATCH_APPLIED`
- `VERIFIED`
- `REPAIR_GENERATED`
- `REPAIR_APPLIED`
- `REVERIFIED`
- `SMOKE_PASSED`
- `SMOKE_FAILED`
- `FINALIZED`

## Commands

- `/agent enqueue <task>`
- `/agent enqueue --smoke "<safe-command>" <task>`
- `/agent queue`
- `/agent queue show <queue-id>`
- `/agent queue show latest`
- `/agent queue show <queue-id> --raw`
- `/agent queue show latest --raw`
- `/agent queue run next`
- `/agent queue run --max <count>`
- `/agent queue resume <queue-id>`
- `/agent queue resume latest`
- `/agent queue cancel <queue-id>`
- `/agent queue cancel latest`
- `/agent queue recover`
- `/agent queue doctor`

Direct `/agent run` remains synchronous and unchanged.

## Storage Layout

Queue files live under the ignored runtime root:

```text
.atropos/agent/queue/
  entries/<queue-id>.meta
  events/<queue-id>.events
  locks/queue.lock
  doctor/
```

Each queue entry has an independent metadata file. A damaged entry cannot corrupt the whole queue.

## Atomic Writes

Mutable queue records are written by:

1. Rendering the full record to a sibling temporary file.
2. Flushing and closing that file.
3. Moving it into place with `ATOMIC_MOVE` and `REPLACE_EXISTING`.
4. Falling back to `REPLACE_EXISTING` if atomic move is unsupported.

Temporary files are not interpreted as queue entries. Malformed records become `CORRUPT`, are not executed, and remain inspectable.

## Lease Model

Queue selection uses a short-held JDK filesystem lock at:

```text
.atropos/agent/queue/locks/queue.lock
```

The lock is held only while recovering stale entries, selecting one eligible item, and persisting its lease. It is released before provider calls, Gradle, patch application, verification, and smoke commands.

Each lease records:

- token
- owner identifier
- acquired timestamp
- heartbeat timestamp
- expiration timestamp

The owner combines process id, hostname where available, and a random UUID. The default lease duration is 15 minutes. `ATROPOS_AGENT_LEASE_SECONDS` may override it within bounded limits.

## Heartbeats

Pass 12 has stage-boundary heartbeats only. There is no background heartbeat thread.

The lease is renewed around:

- claim
- preflight
- planning
- patch generation
- patch application
- verification
- repair generation
- repair application
- smoke
- finalization

Known long-running apply/verification and smoke boundaries extend the lease with a bounded operation duration.

## Retry Policy

The default maximum attempts is `2`. An attempt starts only after a lease is acquired.

Retries are for interrupted or recovered work only. Queueing, showing, cancellation, unsafe preflight refusal, and failed live-lease acquisition do not count as attempts.

No automatic retry is performed for unsafe smoke refusal, explicit cancellation, corrupt metadata, or completed work.

`RETRY_WAIT` uses deterministic bounded backoff. Recovery never sleeps.

## Cancellation

For `QUEUED` and `RETRY_WAIT`, cancellation transitions directly to `CANCELLED` and the task never executes.

For `LEASED` and `RUNNING`, cancellation persists `cancellationRequested=true`. The synchronous worker checks this at stage boundaries and stops before the next destructive or expensive stage. Pass 12 does not claim to kill an already-running external process.

Terminal entries are not rewritten by cancellation.

## Recovery

`/agent queue recover` scans queue entries, detects expired or missing leases on `LEASED` and `RUNNING` entries, clears stale ownership, preserves checkpoints, and transitions eligible work to `RETRY_WAIT` or exhausted work to `FAILED`.

Recovery is idempotent because the stale lease is cleared during the first recovery. A second recovery scan does not increment recovery accounting again.

Live leases are preserved.

## Resume Boundaries

Resume refuses terminal entries. It also refuses entries with a live lease owned by another worker.

Resume can continue through the Pass 11 pipeline from non-destructive boundaries before patch application. If the last durable checkpoint is `PATCH_APPLIED` or later and the entry is nonterminal, ATROPOS requires operator review instead of guessing. This prevents duplicate patch application, duplicate passed verification, and duplicate passed smoke execution.

Completed entries cannot resume and cannot execute again.

## Corrupt Records

Malformed or truncated metadata does not crash queue listing or recovery. The entry is rendered as `CORRUPT`, is terminal for selection purposes, and requires manual inspection.

## Operator Review Cases

Operator review is required when:

- a nonterminal resume would start after patch application
- durable job evidence and queue checkpoint do not prove a safe next stage
- metadata is corrupt
- repository truth is ambiguous

## Security Boundaries

Queueing does not bypass smoke safety, patch safety, verification, paid-provider locks, or no-commit/no-push policy.

Queue metadata and events do not store provider prompts, source files, API keys, authorization headers, or raw secret-bearing environment values.

No queue command executes raw user shell. Smoke commands still go through the Pass 11 safe smoke runner.

## Transition Table

Allowed transitions:

- `QUEUED -> LEASED`
- `QUEUED -> CANCELLED`
- `QUEUED -> REFUSED`
- `LEASED -> RUNNING`
- `LEASED -> QUEUED` after valid stale recovery
- `LEASED -> FAILED` after retry exhaustion
- `LEASED -> CANCELLED`
- `RUNNING -> COMPLETED`
- `RUNNING -> FAILED`
- `RUNNING -> REFUSED`
- `RUNNING -> CANCELLED`
- `RUNNING -> RETRY_WAIT`
- `RETRY_WAIT -> LEASED`
- `RETRY_WAIT -> CANCELLED`
- `RETRY_WAIT -> FAILED`

Terminal states do not transition back to running states.

## Smoke Evidence

Acceptance smoke evidence is produced by:

- enqueue/restart/run queue smoke creating `docs/pass12-queue-smoke.md`
- cancellation smoke proving `docs/pass12-cancel-should-not-exist.md` is absent
- queue doctor diagnostics
- terminal resume refusal

The queue remains synchronous and operator-triggered in Pass 12.
