# Current State

Inspection date (UTC): 2026-07-12T19:36:40Z
Repository: `/data/data/com.termux/files/home/specgraph-foundry`
Branch: `phase-3-production-application`
HEAD SHA: `9c9c67b03b90d26a3420c7a63f7df72ff33359e2`
Worktree status: clean immediately after switching from `main` to `phase-3-production-application`

## Baseline

- Expected merged baseline commit was verified: `9c9c67b03b90d26a3420c7a63f7df72ff33359e2`
- Tracked files: `106`
- Tracked code-line count: `55,105`
- Included extensions for tracked code-line count: `.py`, `.sql`, `.toml`, `.yml`, `.yaml`, `.json`
- Current test files: `16`
- Current test total: `69`

## Python Package Layout

- Package root: `src/specgraph_foundry/`
- Top-level modules: `api.py`, `atoms.py`, `cli.py`, `config.py`, `database.py`, `doctor.py`, `errors.py`, `execution.py`, `exports.py`, `ingestion.py`, `planning.py`, `research.py`, `routing.py`, `services.py`
- HTTP gateway package: `src/specgraph_foundry/http_api/`
- HTTP gateway modules: `auth.py`, `database.py`, `gateway.py`, `handoff_workspace.py`, `models.py`, `planning_workspace.py`, `research_workspace.py`, `server.py`, `source_workspace.py`, `workspace.py`

## HTTP Gateway Layout

- `http_api/server.py` builds the authenticated application, initializes the shared database adapter, reads runtime environment variables, and serves the threaded HTTP server.
- `http_api/gateway.py` exposes public health/version routes, authenticates Supabase bearer tokens, applies request-scoped ownership for PostgreSQL, serves workspace aggregation routes, and delegates the rest of `/v1/*` to the existing backend API in `api.py`.
- `http_api/auth.py` validates Supabase bearer tokens against `/auth/v1/user`.
- `http_api/database.py` sets PostgreSQL transaction-local claims for `request.jwt.claim.sub`, `request.jwt.claim.role`, and `request.jwt.claims`, and uses role `authenticated`.
- `workspace.py`, `source_workspace.py`, `research_workspace.py`, `planning_workspace.py`, and `handoff_workspace.py` provide read-only aggregate workspace views over existing backend records.

## Existing Routes

### Public routes

- `GET /health`
- `GET /version`

### Protected identity route

- `GET /v1/me`

### Workspace, readiness, and provenance routes

- `GET /v1/projects/{project_id}/workspace`
- `GET /v1/projects/{project_id}/readiness`
- `GET /v1/projects/{project_id}/source-workspace`
- `GET /v1/projects/{project_id}/research-workspace`
- `GET /v1/projects/{project_id}/planning-workspace`
- `GET /v1/projects/{project_id}/handoff-workspace`
- `GET /v1/documents/{document_id}/provenance`

### Delegated domain route inventory

Project:
- `GET /v1/projects`
- `POST /v1/projects`
- `GET /v1/projects/{project_id}`

Source:
- `GET /v1/projects/{project_id}/documents`
- `POST /v1/projects/{project_id}/documents`
- `GET /v1/documents/{document_id}`
- `GET /v1/documents/{document_id}/verify`
- `POST /v1/documents/{document_id}/extract`
- `GET /v1/documents/{document_id}/atoms`

Research:
- `GET /v1/projects/{project_id}/research-tasks`
- `POST /v1/projects/{project_id}/research-tasks/claim`
- `GET /v1/projects/{project_id}/gap-matrix`
- `GET /v1/research-tasks/{task_id}`
- `POST /v1/research-tasks/{task_id}/heartbeat`
- `POST /v1/research-tasks/{task_id}/evidence`
- `POST /v1/research-tasks/{task_id}/complete`
- `POST /v1/research-tasks/{task_id}/fail`

Planning:
- `GET /v1/projects/{project_id}/relations`
- `POST /v1/projects/{project_id}/relations`
- `GET /v1/projects/{project_id}/plans`
- `POST /v1/projects/{project_id}/plans`
- `GET /v1/plans/{plan_id}`
- `POST /v1/plans/{plan_id}/verify`

Export:
- `GET /v1/projects/{project_id}/bindings`
- `POST /v1/projects/{project_id}/bindings`
- `GET /v1/projects/{project_id}/exports`
- `POST /v1/plans/{plan_id}/exports`
- `GET /v1/exports/{export_id}`
- `POST /v1/exports/{export_id}/verify`

Execution:
- `POST /v1/plans/{plan_id}/execution-runs`
- `GET /v1/projects/{project_id}/execution-runs`
- `GET /v1/execution-runs/{run_id}`
- `POST /v1/execution-runs/{run_id}/claim`
- `POST /v1/execution-runs/{run_id}/verify`
- `POST /v1/execution-nodes/{run_node_id}/heartbeat`
- `POST /v1/execution-nodes/{run_node_id}/receipts`

Routing:
- `GET /v1/projects/{project_id}/routing-policy`
- `POST /v1/projects/{project_id}/routing-policy`
- `GET /v1/projects/{project_id}/providers`
- `POST /v1/projects/{project_id}/providers`
- `POST /v1/providers/{provider_id}/health`
- `GET /v1/projects/{project_id}/renderers`
- `POST /v1/projects/{project_id}/renderers`
- `POST /v1/projects/{project_id}/renderers/select`
- `POST /v1/projects/{project_id}/paid-unlocks`
- `POST /v1/projects/{project_id}/route-decisions`
- `GET /v1/route-decisions/{decision_id}`

## Supabase Migrations

- `supabase/migrations/20260712000100_core.sql`
- `supabase/migrations/20260712000200_ingestion.sql`
- `supabase/migrations/20260712000300_atoms.sql`
- `supabase/migrations/20260712000400_research.sql`
- `supabase/migrations/20260712000500_planning.sql`
- `supabase/migrations/20260712000600_exports.sql`
- `supabase/migrations/20260712000700_execution.sql`
- `supabase/migrations/20260712000800_routing.sql`
- `supabase/migrations/20260712000900_auth_rls.sql`

## Hosted Database and Ownership

- Hosted table count from `supabase/migrations/*.sql`: `35`
- RLS ownership model: Supabase Auth user UUID is written to `projects.owner_id`, helper functions derive project ownership for related records, `authenticated` gets table access only through `project_owner_all` policies, and anonymous access is revoked.
- Request-scoped PostgreSQL ownership model: the HTTP gateway validates the bearer token, sets `request.jwt.claim.sub`, `request.jwt.claim.role`, and `request.jwt.claims`, and runs as PostgreSQL role `authenticated`.
- Local development mode: SQLite remains available for local single-user development and tests.

## Tests and Workflows

- Test files:
  - `tests/test_application_workspaces_api.py`
  - `tests/test_atoms.py`
  - `tests/test_core.py`
  - `tests/test_database.py`
  - `tests/test_execution.py`
  - `tests/test_exports.py`
  - `tests/test_hosted_release_audit.py`
  - `tests/test_http_api.py`
  - `tests/test_ingestion.py`
  - `tests/test_planning.py`
  - `tests/test_postgres_adapter.py`
  - `tests/test_project_workspace_api.py`
  - `tests/test_research.py`
  - `tests/test_research_api.py`
  - `tests/test_routing.py`
  - `tests/test_supabase_rls.py`
- GitHub workflows:
  - `.github/workflows/ci.yml`

## Deployment and Runtime Configuration

- Runtime environment variables implemented in code:
  - `SPECGRAPH_DATABASE_PATH`
  - `SPECGRAPH_HOST`
  - `SPECGRAPH_PORT`
  - `SPECGRAPH_DATABASE_URL`
  - `SPECGRAPH_OWNER_ID`
  - `SPECGRAPH_ALLOWED_ORIGINS`
  - `SPECGRAPH_MAX_REQUEST_BYTES`
  - `SPECGRAPH_AUTH_TIMEOUT_SECONDS`
  - `SUPABASE_URL`
  - `SUPABASE_ANON_KEY`
- Local hosted stack configuration exists in `supabase/config.toml` with API, Auth, Realtime, and Storage enabled for local development.
- CI currently compiles Python, runs `unittest`, verifies licenses, and runs CLI smoke checks.
- No repository evidence of deployment manifests for Vercel or Cloud Run was found.

## Presence and Absence Checks

- `apps/web/`: absent
- `openapi/`: absent
- `infra/cloud-run/`: absent
- Durable Storage upload pipeline for original binaries: absent
- Durable operations queue: absent
- PWA implementation: absent
- Frontend application: absent
- OpenTelemetry instrumentation: absent
- Vercel configuration: absent
- Cloud Run configuration: absent
- Generated API client: absent

## Verified Capabilities

- Project creation, listing, retrieval, and ownership-backed isolation
- Immutable source ingestion with SHA-256, byte counts, line counts, duplicate-source rejection, sections, chunks, coverage validation, and provenance retrieval
- Atom extraction with dimensions and project-scoped research task generation
- Research task claim, heartbeat, evidence, completion, failure, and gap-matrix workflows
- Authority relations, deterministic planning, execution-cycle rejection, plan synthesis, and plan verification
- Integration bindings with secret rejection, deterministic exports, and export verification
- Execution runs, node claims, leases, receipts, tamper detection, and independent verification checks
- Provider configuration, provider health, renderer configuration and selection, routing policy, paid unlocks, and route decisions
- Supabase authentication gateway with request-scoped PostgreSQL ownership
- Workspace aggregation APIs for project readiness, source, research, planning, and handoff status

## Missing Production Capabilities

- Machine-readable OpenAPI 3.1 contract
- Generated TypeScript API client
- Cursor pagination and explicit collection bounds on existing list endpoints
- Idempotency protection for replayable expensive mutations
- ETag/version with `If-Match` for editable resources
- Binary upload initiation/finalize pipeline backed by private Supabase Storage
- Separate private artifact storage for exports
- Durable long-running operations records, queueing, workers, heartbeats, and `202 Accepted` operation URLs
- Next.js frontend, mobile-first application shell, and PWA
- Interactive graph UI and deterministic browser layout worker
- Structured production observability with OpenTelemetry-compatible traces and metrics
- Deployment, staging, release, and rollback configuration for Vercel and Cloud Run

## Known Risks Confirmed by Repository Evidence

- Unbounded workspace responses: workspace and list routes return full aggregates without cursor pagination.
- Ephemeral export paths: export creation accepts an `output_root` filesystem path and does not persist artifact storage in Supabase.
- Synchronous long work: ingestion, extraction, planning, export, execution, and routing mutations run inline in request handlers; no durable operations resource exists.
- Missing machine-readable API contract: `docs/API_V1.md` is prose only; no OpenAPI authority exists.
- Missing production frontend and deployment: `apps/web/`, Vercel configuration, and Cloud Run deployment configuration are absent.

## Explicit Distinction

- Verified now: the repository contains a substantial domain backend with authenticated gateway support, request-scoped PostgreSQL ownership, Supabase RLS migrations, domain services, tests, and local hosted configuration.
- Not complete yet: the production application defined for later groups is incomplete because there is still no machine-readable API authority, no durable storage/upload pipeline, no durable operations system, no frontend/PWA, and no deployment/release implementation for a hosted production application.
