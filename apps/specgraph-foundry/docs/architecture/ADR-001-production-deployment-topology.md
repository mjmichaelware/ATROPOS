# ADR-001 Production Deployment Topology

Status: Accepted for implementation.

## Context

The repository has a Python backend, local Supabase configuration, and CI, but no production deployment manifests, frontend application, or durable worker topology. Later groups need one fixed hosting model before API contracts, uploads, workers, and release automation can be implemented consistently.

## Decision

Use a split production topology:
- Next.js App Router with TypeScript frontend on Vercel
- Existing Python API on Google Cloud Run
- Supabase PostgreSQL, Supabase Auth, and private Supabase Storage as hosted data services
- GitHub Actions for CI/CD orchestration only
- Cloud Run worker or Cloud Run Job for durable long-running operations
- Separate staging and production environments
- GitHub Actions is not a hosting surface

## Detailed Topology or Contract

- Frontend origin:
  - Production: `https://app.specgraph.example`
  - Staging: `https://staging-app.specgraph.example`
- API origin:
  - Production: `https://api.specgraph.example`
  - Staging: `https://staging-api.specgraph.example`
- Vercel hosts only the Next.js web application and server-side rendering layer.
- Cloud Run hosts the authenticated Python HTTP API and exposes only the stable application API.
- Supabase hosts PostgreSQL, Auth, and private Storage buckets used by the application.
- Durable background execution runs in a separate Cloud Run worker or Cloud Run Job deployment so long operations do not share request lifecycle limits with the API service.
- GitHub Actions runs tests, audits, builds, and deployment steps against Vercel and Google Cloud using short-lived identity.

## Security Consequences

- Browser traffic is isolated to the web origin; backend secrets stay on Cloud Run and Supabase server surfaces.
- Frontend and API origins can be explicitly allowlisted for CORS, CSP, and Supabase redirect configuration.
- Worker credentials can be scoped separately from request-serving API credentials.
- GitHub Actions must never hold long-lived cloud JSON keys.

## Data/Migration Consequences

- Supabase remains the system of record for hosted relational data and object storage.
- Production and staging require separate Supabase projects, storage buckets, and migration application history.
- Deployment design must preserve compatibility between frontend, API, worker, and migration rollouts.

## Testing Consequences

- CI must validate backend, web, migrations, and generated client artifacts separately and together.
- End-to-end tests must cover browser-to-Vercel-to-Cloud-Run-to-Supabase flows in staging.
- Worker flows require integration tests separate from request/response tests.

## Operational Consequences

- API scaling, web scaling, and worker scaling are independent.
- Release management must coordinate Vercel deployments, Cloud Run revisions, worker revisions, and Supabase migrations.
- Staging must mirror production topology closely enough to prove authentication, storage, and long-running operations before release.

## Rejected Alternatives

- Single-process monolith on Cloud Run: rejected because it couples browser delivery, API limits, and long-running work.
- GitHub Actions as execution host: rejected because CI is not durable application hosting.
- Keeping SQLite as hosted runtime storage: rejected because hosted multi-user ownership depends on Supabase PostgreSQL and RLS.

## Dependencies on Later Groups

- Group 10 and Groups 11-18 for the Vercel-hosted frontend
- Groups 05-08 for storage and worker infrastructure
- Group 19 for CI/CD and rollback implementation
- Group 20 for hosted acceptance and release evidence
