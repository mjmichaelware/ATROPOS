# ADR-002 API Versioning and Contracts

Status: Accepted for implementation.

## Context

The repository exposes a working authenticated JSON API, but its authority is Python route code plus prose documentation in `docs/API_V1.md`. Later frontend, client-generation, pagination, idempotency, and concurrency work need one machine-readable contract and stable mutation semantics.

## Decision

- OpenAPI 3.1 becomes the machine-readable API authority.
- Existing `v1` behavior is preserved unless an intentional migration changes it.
- Stable error shape is `error`, `message`, `request_id`, `details`.
- Generated TypeScript client derives from OpenAPI, not handwritten wrappers.
- Collection endpoints use cursor pagination with explicit bounds.
- Replayable expensive mutations accept `Idempotency-Key`.
- Editable records use version or ETag plus `If-Match`.

## Detailed Topology or Contract

- Store the authoritative contract under `openapi/` and version it with the repository.
- Keep the `/v1/` namespace for existing semantics; any incompatible change requires an explicit migration plan or a new API version surface.
- Every non-2xx application error returns:
  - `error`
  - `message`
  - `request_id`
  - `details`
- List endpoints adopt a shared envelope with bounded `limit`, stable ordering, and cursor-based continuation.
- Expensive or externally visible mutation endpoints require `Idempotency-Key`, server-side deduplication scope, and replay-safe responses.
- Editable resources expose a version token or ETag; clients must send `If-Match` for updates that can race.
- Generated TypeScript client artifacts are rebuilt from the OpenAPI source and consumed by the Next.js app.

## Security Consequences

- Contract-driven validation reduces ad hoc request parsing divergence between server and clients.
- Stable error shapes limit accidental leakage of stack traces or internal structures.
- Idempotency and concurrency controls reduce duplicate side effects under retries or flaky networks.

## Data/Migration Consequences

- Existing endpoints remain supported during the transition to contract-backed handlers.
- Schema changes that affect wire format need explicit migration notes and compatibility windows.
- Version or ETag fields become part of editable record persistence where updates are introduced.

## Testing Consequences

- Contract tests must validate route coverage against OpenAPI.
- Generated client tests must fail when the contract and implementation diverge.
- Pagination, idempotency, and `If-Match` semantics require dedicated integration coverage.

## Operational Consequences

- OpenAPI becomes the release gate for frontend/backend integration.
- API changes need changelog discipline because generated clients and web builds depend on them.
- Contract publication becomes part of CI output and deployment verification.

## Rejected Alternatives

- Keeping prose Markdown as the only API authority: rejected because it cannot drive validation or client generation.
- Handwritten frontend fetch wrappers without generated contracts: rejected because drift is likely and hard to audit.
- Offset pagination for all collections: rejected because later large datasets need stable continuation semantics.

## Dependencies on Later Groups

- Group 02 for OpenAPI and error-shape implementation
- Group 03 for bounded pagination
- Group 04 for idempotency and concurrency controls
- Group 10 for generated client consumption in the web app
