# ADR-004 Durable Operations and Workers

Status: Accepted for implementation.

## Context

Current request handlers execute ingestion, extraction, planning, export, execution, and routing work inline. The repository has execution leases for domain runs, but it has no general durable operations resource, no worker deployment, no queue-backed lifecycle, and no `202 Accepted` response pattern for long application work.

## Decision

- Long operations use durable database records with statuses `QUEUED`, `CLAIMED`, `RUNNING`, `SUCCEEDED`, `FAILED`, `CANCEL_REQUESTED`, `CANCELLED`, and `TIMED_OUT`.
- Workers use leases, heartbeats, bounded retries, idempotent transactional writes, progress updates, cancellation handling, timeout handling, result references, and immutable events.
- Ordinary HTTP requests return `202 Accepted` plus an operation URL for long work.

## Detailed Topology or Contract

- Operation record fields include:
  - operation type
  - owner and project scope
  - request id
  - idempotency scope
  - current status
  - progress summary
  - attempt count
  - lease owner
  - lease expiry
  - timeout deadline
  - result reference
  - failure details
- Worker flow:
  - claim eligible queued operations
  - atomically establish lease ownership
  - heartbeat progress before lease expiry
  - write durable intermediate progress
  - commit idempotent results
  - append immutable operation events
  - release or transition on success, failure, cancel, or timeout
- API flow:
  - validate request
  - create or reuse operation from idempotency key
  - return `202 Accepted`
  - include canonical operation URL and current state payload

## Security Consequences

- Operation records must stay project-scoped under the same ownership model as the underlying domain objects.
- Workers must never log secrets, signed URLs, or raw authority payloads.
- Cancellation and retry controls must not allow one user to interfere with another user’s operations.

## Data/Migration Consequences

- New durable operation tables and immutable event tables are required.
- Existing domain tables remain authoritative for completed ingestion, planning, export, and execution outputs.
- Idempotent result references must point to domain records rather than duplicating payloads.

## Testing Consequences

- Tests must cover retries, lease expiration, worker crash recovery, cancellation, timeout, replay under idempotency, and duplicate-claim prevention.
- Integration tests must prove that `202` workflows eventually converge to the same durable domain outputs as today’s synchronous paths.

## Operational Consequences

- Cloud Run workers or jobs need concurrency, lease timing, retry policy, and dead-letter visibility.
- Operations dashboards and alerts are required for stuck, retrying, cancelled, and timed-out work.
- Request-serving latency becomes bounded because long work leaves the HTTP request path.

## Rejected Alternatives

- Continuing synchronous request handling: rejected because request lifetimes and user retries make long work unreliable.
- In-memory queueing inside the API process: rejected because it is not durable across restarts or scale-out.
- Reusing execution-run tables as the generic operations queue: rejected because domain execution runs are not the same abstraction as application-level long work.

## Dependencies on Later Groups

- Group 08 for durable operations and worker implementation
- Groups 05-07 for storage-backed operations
- Groups 10-16 for operation polling and cancellation UX
