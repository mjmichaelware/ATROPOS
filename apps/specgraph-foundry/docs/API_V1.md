# SpecGraph Foundry API v1

Authoritative contract: `openapi/specgraph-v1.yaml`

## Run the application API

From the repository root:

    scripts/run_application_api.sh

The server listens on the configured host and port. The default local address is:

    http://127.0.0.1:8787

## Public endpoints

- `GET /health`
- `GET /health/live`
- `GET /health/startup`
- `GET /health/ready`
- `GET /version`

Public responses omit database users, server versions, table names, credentials, and other sensitive operational details.

`/health/live` is process-only and does not query dependencies. `/health/startup` checks startup configuration and local schema coherence. `/health/ready` performs bounded readiness checks for database, storage, and operations subsystems and returns `503` with generic `ready` or `unavailable` checks when not ready.

## Authentication

Every `/v1/*` endpoint requires a valid Supabase access token:

    Authorization: Bearer SUPABASE_ACCESS_TOKEN

The API verifies the token through Supabase Auth. Missing, malformed, oversized, invalid, or expired tokens are rejected.

## Current user

### `GET /v1/me`

Returns the authenticated Supabase user identity used for request-scoped ownership.

## Request IDs

Every `AuthenticatedApi` response includes:

- `x-request-id` response header
- `cache-control: no-store`
- For idempotent protected mutations, `Idempotency-Replayed: false` on the original success and `Idempotency-Replayed: true` on a replayed success
- For editable routing policy, binding, provider, and renderer representations, `ETag`
- For the four paginated collection routes, `x-page-limit`, `x-page-count`, `x-has-more`, and `x-next-cursor` when another page exists
- For source-upload intent and finalize mutations, `Idempotency-Replayed`

Failure responses repeat the same request ID inside the JSON error envelope.

## Resource and transport controls

The hosted server validates Host, request target, header count, aggregate header bytes, content type, body size, JSON depth, JSON item count, JSON string size, finite JSON numbers, concurrent requests, and a per-instance rate limit before expensive routing work.

Resource-control failures use the stable error envelope. Common statuses include:

- `414 REQUEST_TARGET_TOO_LARGE`
- `431 HEADERS_TOO_LARGE`
- `413 PAYLOAD_TOO_LARGE` or `JSON_LIMIT_EXCEEDED`
- `429 TOO_MANY_REQUESTS` with `Retry-After`
- `503 SERVER_BUSY` with `Retry-After`
- `504 REQUEST_TIMEOUT` when the application detects deadline expiry

The built-in rate limiter is defense-in-depth for one API instance. Distributed/global enforcement remains an edge/deployment responsibility for Group 19.

## Logs, traces, and metrics

Server logs are one-line JSON records with a strict safe-field allowlist. They use normalized route templates and do not include raw paths with identifiers, query strings, headers, bearer tokens, cookies, idempotency keys, cursors, request or response bodies, source/evidence/plan/artifact/receipt content, SQL, stack traces, filenames, project names, or signed URLs.

Telemetry is disabled by default and safe to run as a no-op. When enabled later, spans and metrics use bounded finite labels such as route template, method, status, operation type, dependency category, and stable error code. There is no public metrics endpoint.

## Idempotent mutations

The authenticated hosted boundary requires `Idempotency-Key` for these retry-sensitive mutation families:

- source ingestion and extraction
- source upload intent creation and source upload finalization
- research claim, evidence creation, and completion
- plan synthesis and verification
- binding creation/configuration
- export generation and verification
- execution-run start, claim, receipt submission, and verification
- provider configuration and provider health recording
- renderer configuration and renderer selection
- paid unlock creation and route-decision creation

The key must be visible ASCII, 16-200 characters, and owner-scoped. The raw key is never persisted. A successful repeated request with the same owner, operation, key, and canonical JSON payload replays the original safe success response. Reusing the same key for a different canonical request returns `409 IDEMPOTENCY_KEY_REUSED`. Active in-flight reuse returns `409 IDEMPOTENCY_IN_PROGRESS`. Failed records are retryable under a bounded persisted retry policy rather than being replayed as success.

## Optimistic concurrency

Editable hosted resources use strong ETags and `If-Match`:

- `GET /v1/projects/{project_id}/routing-policy` returns `ETag`
- `GET /v1/projects/{project_id}/bindings`
- `GET /v1/projects/{project_id}/providers`
- `GET /v1/projects/{project_id}/renderers`

The three collection routes above expose an `etag` field on each independently editable item. Updating an existing routing policy, binding, provider, or renderer requires `If-Match` with the current strong ETag.

- Missing `If-Match`: `428 PRECONDITION_REQUIRED`
- Malformed `If-Match`: `400 INVALID_PRECONDITION`
- Stale or non-matching `If-Match`: `412 PRECONDITION_FAILED`

New creates do not require `If-Match`.

## Request-scoped ownership

For PostgreSQL requests, the authenticated Supabase user UUID is applied as the request owner through these transaction-local claims:

- `request.jwt.claim.sub`
- `request.jwt.claim.role`
- `request.jwt.claims`

The request uses the PostgreSQL `authenticated` role. Supabase Row Level Security remains responsible for enforcing project ownership.

SQLite remains available for local single-user development and testing.

Owner-scoped lookups remain non-enumerating through 404 behavior.

## Stable error schema

All failures returned through the authenticated gateway use one nested top-level error object:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "slug is required",
    "request_id": "9fbecf32-1a2b-4f5a-8f1d-2ac1eaf5f0c7",
    "details": {}
  }
}
```

The OpenAPI contract in `openapi/specgraph-v1.yaml` is the machine-readable authority for status codes, request bodies, success schemas, and reusable error responses.

## Cursor pagination

These existing collection routes now use bounded cursor pagination:

- `GET /v1/projects`
- `GET /v1/projects/{project_id}/documents`
- `GET /v1/documents/{document_id}/atoms`
- `GET /v1/projects/{project_id}/research-tasks`
- `GET /v1/projects/{project_id}/relations`

Query parameters:

- `limit`: optional integer, default `50`, minimum `1`, maximum `100`
- `cursor`: optional opaque continuation token

The body shape remains unchanged as `{ "items": [...] }`. Pagination metadata is carried in response headers so existing consumers do not need a new success envelope.

Invalid limits, duplicate pagination parameters, unsupported collection query parameters, blank cursors, malformed cursors, altered cursors, and cross-scope cursor reuse are rejected as `400 VALIDATION_ERROR`.

## Private source uploads

The authenticated source-upload lifecycle uses private Supabase Storage and three hosted routes:

- `POST /v1/projects/{project_id}/source-uploads`
- `GET /v1/source-uploads/{upload_id}`
- `POST /v1/source-uploads/{upload_id}/finalize`

The API stores raw source bytes outside the application process in a private owner-scoped bucket path of the form `{owner_id}/{project_id}/{upload_id}/source`. Upload bytes go directly to Storage by short-lived signed URL; the application does not proxy normal upload bodies.

Create-intent requests require:

- `filename`
- `media_type`
- `byte_size`
- `sha256`
- `Idempotency-Key`

Finalize also requires `Idempotency-Key`.

Supported Group 6 formats:

- direct UTF-8 text: `text/plain`, `text/markdown`, `application/json`, YAML text forms, and the allowlisted source-code text media types documented in `openapi/specgraph-v1.yaml`
- sanitized HTML: `text/html`, `application/xhtml+xml`
- DOCX: `application/vnd.openxmlformats-officedocument.wordprocessingml.document`
- PDF: `application/pdf`

Finalization re-downloads the private object, enforces the configured byte limit, recomputes exact SHA-256 over the uploaded bytes, verifies byte count, checks the declared media type against filename and detected format, runs a bounded adapter, and then ingests only the derived UTF-8 text through the existing immutable source-document pipeline.

Raw uploaded bytes remain the immutable authority. Derived text is recorded separately with its own byte length and SHA-256. Finalize success responses now include both `raw_authority` and `derivation` summaries. The provenance route exposes the same distinction together with bounded page or part locators.

PDF OCR is still out of scope. Image-only or no-text PDFs return `NO_EXTRACTABLE_TEXT`. Encrypted PDFs and hostile or malformed DOCX/HTML inputs fail safely without exposing parser internals, signed URLs, hashes from mismatched uploads, or raw content.

Status responses remain owner-scoped and omit signed URLs, credentials, raw source bytes, and provider diagnostics. Expired intents return `409 UPLOAD_EXPIRED`. Integrity mismatches return `409 UPLOAD_INTEGRITY_MISMATCH`. Unsupported formats return `415 UNSUPPORTED_MEDIA_TYPE`. Invalid containers or extracted-text failures return stable nested error codes such as `INVALID_DOCUMENT`, `DOCUMENT_ENCRYPTED`, `DOCUMENT_LIMIT_EXCEEDED`, `NO_EXTRACTABLE_TEXT`, and `INVALID_SOURCE_ENCODING`.

## Durable export artifacts

Hosted export generation no longer treats local filesystem output as artifact authority. `POST /v1/plans/{plan_id}/exports` still requires `Idempotency-Key`, but the hosted boundary now generates deterministic export files in bounded temporary storage, uploads fixed artifact names into the private `export-artifacts` bucket, persists `storage_objects` and `artifact_manifests`, re-downloads stored bytes, and verifies byte length plus SHA-256 before marking the export durable and `VERIFIED`.

The persisted manifest contains only safe metadata:

- logical artifact name
- media type
- byte length
- SHA-256
- private storage object reference
- aggregate manifest SHA-256

It never stores local paths, signed URLs, bearer tokens, service keys, source content, or temporary names. Temporary export directories are cleanup-only implementation detail and are not authority.

`POST /v1/exports/{export_id}/verify` re-downloads private artifacts and recomputes checksums. Upload success alone is not verification. Tampered, truncated, missing, oversized, or media-type-mismatched artifacts return stable nested errors such as `ARTIFACT_INTEGRITY_FAILED`, `ARTIFACT_NOT_VERIFIED`, `ARTIFACT_LIMIT_EXCEEDED`, or `STORAGE_UNAVAILABLE`.

`GET /v1/exports/{export_id}/download` returns short-lived signed download URLs only for owner-visible exports whose manifest and storage objects are `VERIFIED`. The route is a safe authenticated `GET`; it does not require `Idempotency-Key`, does not proxy artifact bytes, and does not persist or log signed URLs. Default URL lifetime is 300 seconds and is capped at 900 seconds.

Hosted execution start with an `export_id` requires the existing verified plan/export checks plus a `VERIFIED` durable artifact manifest. A legacy filesystem path alone is insufficient for hosted execution readiness.

## Durable operations

Expensive hosted mutations are accepted as durable database-backed operations instead of being executed inside the HTTP request. The converted families are:

- source upload finalization
- document atom extraction
- research completion
- plan synthesis and verification
- durable export generation and verification
- execution-run start and execution-run verification

These requests still require `Idempotency-Key`. A successful submission returns `202 Accepted` with:

- `operation`: safe operation status
- `Location: /v1/operations/{operation_id}`
- `Retry-After`: bounded polling interval in seconds
- `Idempotency-Replayed`: `false` for the original accepted request or `true` for an idempotent replay

The domain mutation is not executed inline. Workers claim queued operations from the database with a hashed lease token, heartbeat while running, update bounded phase/progress, and complete with safe result references only. Operation records never store bearer tokens, raw idempotency keys, source content, artifact bytes, signed URLs, local paths, stack traces, SQL, or raw exception text.

Operation states:

- `QUEUED`
- `CLAIMED`
- `RUNNING`
- `SUCCEEDED`
- `FAILED`
- `CANCEL_REQUESTED`
- `CANCELLED`
- `TIMED_OUT`

Operation APIs:

- `GET /v1/operations/{operation_id}`
- `POST /v1/operations/{operation_id}/cancel`
- `GET /v1/projects/{project_id}/operations`

Project operation listing uses the same bounded cursor pagination contract as other collection routes. Unknown or other-owner operations return 404.

Cancellation is cooperative. A queued operation becomes `CANCELLED` immediately. A claimed or running operation becomes `CANCEL_REQUESTED` and is cancelled at a safe worker checkpoint when possible. If an irreversible domain commit has already completed, the operation may finish with a truthful safe result rather than falsely claiming rollback.

Run a local worker with:

    scripts/run_operation_worker.sh --once

or:

    scripts/run_operation_worker.sh --drain

The Cloud Run Job manifest under `infra/cloud-run/worker/job.yaml` is a nondeployed template for Group 19 deployment work.

## Project workspace

### `GET /v1/projects/{project_id}/workspace`

Returns an aggregate view of the project using records already persisted by the SpecGraph Foundry backend.

The response includes domain counts, latest records, readiness state, and the next required action.

### `GET /v1/projects/{project_id}/readiness`

Returns the project’s deterministic readiness state and next required action.

Readiness states:

- `SOURCE_REQUIRED`
- `EXTRACTION_REQUIRED`
- `RESEARCH_REQUIRED`
- `READY_TO_PLAN`
- `READY_TO_EXPORT`
- `INTEGRATION_BINDING_REQUIRED`
- `READY_TO_EXECUTE`
- `VERIFIED`

Readiness advances in this order:

1. A project without source documents is `SOURCE_REQUIRED`.
2. A project with source documents but no atoms is `EXTRACTION_REQUIRED`.
3. A project with open completeness dimensions is `RESEARCH_REQUIRED`.
4. A project with closed research but no verified plan is `READY_TO_PLAN`.
5. A project with a verified plan but no verified export is `READY_TO_EXPORT`.
6. A project with a verified export but no enabled integration binding is `INTEGRATION_BINDING_REQUIRED`.
7. A project with a verified export and enabled integration binding but no verified execution run is `READY_TO_EXECUTE`.
8. A project with a verified execution run is `VERIFIED`.

## Specialized workspace bounds

The specialized source, research, planning, handoff, and provenance workspace routes return bounded previews for nested collections.

- Every preview list is capped at `5` items.
- Each preview includes the corresponding `*_count` total and `*_has_more` boolean.
- Where an authoritative route exists today, the workspace response includes a stable `*_route` link back to that API.
- The source workspace now includes a bounded upload-intent preview with `uploads`, `uploads_count`, `uploads_has_more`, and `uploads_route`.
- Provenance responses keep the same top-level sections, chunks, atoms, and run arrays, but large nested collections are truncated to preview size and large document content is returned as a bounded preview with `content_truncated`.
- Upload-derived provenance distinguishes raw authority from derived text and exposes bounded `locators_preview`, `locators_count`, and `locators_has_more` for page or part provenance.

## Existing domain endpoints

Other authenticated `/v1/*` requests are delegated to the existing SpecGraph Foundry domain API. The hosted gateway does not duplicate backend domain behavior.

Current route families:

- Public: `/health`, `/health/live`, `/health/startup`, `/health/ready`, `/version`
- Identity: `/v1/me`
- Projects: `/v1/projects`, `/v1/projects/{project_id}`
- Sources: `/v1/projects/{project_id}/documents`, `/v1/documents/{document_id}`, `/v1/documents/{document_id}/verify`, `/v1/documents/{document_id}/extract`, `/v1/documents/{document_id}/atoms`
- Source uploads: `/v1/projects/{project_id}/source-uploads`, `/v1/source-uploads/{upload_id}`, `/v1/source-uploads/{upload_id}/finalize`
- Workspaces: `/v1/projects/{project_id}/workspace`, `/v1/projects/{project_id}/readiness`, `/v1/projects/{project_id}/source-workspace`, `/v1/projects/{project_id}/research-workspace`, `/v1/projects/{project_id}/planning-workspace`, `/v1/projects/{project_id}/handoff-workspace`, `/v1/documents/{document_id}/provenance`
- Atoms: `/v1/atoms/{atom_id}`
- Research: `/v1/projects/{project_id}/research-tasks`, `/v1/projects/{project_id}/research-tasks/claim`, `/v1/projects/{project_id}/gap-matrix`, `/v1/research-tasks/{task_id}`, `/v1/research-tasks/{task_id}/heartbeat`, `/v1/research-tasks/{task_id}/evidence`, `/v1/research-tasks/{task_id}/complete`, `/v1/research-tasks/{task_id}/fail`
- Planning: `/v1/projects/{project_id}/relations`, `/v1/projects/{project_id}/plans`, `/v1/plans/{plan_id}`, `/v1/plans/{plan_id}/verify`
- Handoff: `/v1/projects/{project_id}/bindings`, `/v1/projects/{project_id}/exports`, `/v1/plans/{plan_id}/exports`, `/v1/exports/{export_id}`, `/v1/exports/{export_id}/verify`, `/v1/exports/{export_id}/download`
- Execution: `/v1/plans/{plan_id}/execution-runs`, `/v1/projects/{project_id}/execution-runs`, `/v1/execution-runs/{run_id}`, `/v1/execution-runs/{run_id}/claim`, `/v1/execution-runs/{run_id}/verify`, `/v1/execution-nodes/{run_node_id}/heartbeat`, `/v1/execution-nodes/{run_node_id}/receipts`
- Routing: `/v1/projects/{project_id}/routing-policy`, `/v1/projects/{project_id}/providers`, `/v1/providers/{provider_id}/health`, `/v1/projects/{project_id}/renderers`, `/v1/projects/{project_id}/renderers/select`, `/v1/projects/{project_id}/paid-unlocks`, `/v1/projects/{project_id}/route-decisions`, `/v1/route-decisions/{decision_id}`

## Application settings

- `SPECGRAPH_ALLOWED_ORIGINS` defines the comma-separated CORS allowlist.
- `SPECGRAPH_ALLOWED_HOSTS` defines the exact allowed Host values for the application server.
- `SPECGRAPH_MAX_REQUEST_BYTES` defines the maximum request body size.
- `SPECGRAPH_MAX_REQUEST_TARGET_BYTES`, `SPECGRAPH_MAX_HEADER_COUNT`, `SPECGRAPH_MAX_HEADER_BYTES`, `SPECGRAPH_MAX_JSON_DEPTH`, `SPECGRAPH_MAX_JSON_ITEMS`, `SPECGRAPH_MAX_JSON_STRING_BYTES`, `SPECGRAPH_MAX_CONCURRENT_REQUESTS`, and `SPECGRAPH_REQUEST_DEADLINE_SECONDS` define local resource controls.
- `SPECGRAPH_RATE_LIMIT_ENABLED`, `SPECGRAPH_RATE_LIMIT_REQUESTS`, and `SPECGRAPH_RATE_LIMIT_WINDOW_SECONDS` configure the bounded per-instance rate limiter.
- `SPECGRAPH_LOG_LEVEL`, `SPECGRAPH_LOG_FORMAT`, `SPECGRAPH_OTEL_ENABLED`, `SPECGRAPH_OTEL_SERVICE_NAME`, `SPECGRAPH_OTEL_EXPORTER_OTLP_ENDPOINT`, `SPECGRAPH_OTEL_EXPORT_TIMEOUT_SECONDS`, and `SPECGRAPH_TRACE_SAMPLE_RATIO` configure local structured logging and optional telemetry. Export is disabled by default.
- `SPECGRAPH_AUTH_TIMEOUT_SECONDS` defines the Supabase authentication timeout.
- `SPECGRAPH_CURSOR_SIGNING_KEY` defines the server-side cursor-signing secret used for protected paginated routes.
- `SPECGRAPH_SOURCE_BUCKET` defines the private source-object bucket name.
- `SPECGRAPH_UPLOAD_URL_TTL_SECONDS` defines the signed upload URL lifetime.
- `SPECGRAPH_MAX_SOURCE_BYTES` defines the maximum accepted source object size.
- `SPECGRAPH_STORAGE_TIMEOUT_SECONDS` defines the bounded Storage API timeout.

Browser clients must be allowed to send `Authorization`, `Content-Type`, `Idempotency-Key`, `If-Match`, and `X-Request-ID`. The server exposes `ETag`, `Idempotency-Replayed`, `X-Request-ID`, and the pagination headers for browser reads.

## Contract parity testing

The repository checks the contract in two layers:

- `tests/test_openapi_parity.py` loads `openapi/specgraph-v1.yaml` with the Python standard library and verifies implemented route parity, security declarations, path-parameter coverage, and stable error-envelope use.
- `tests/test_error_schema.py` exercises the real authenticated gateway and verifies the stable nested error algebra, request ID behavior, non-leaking internal failure handling, and compatibility of successful responses.
