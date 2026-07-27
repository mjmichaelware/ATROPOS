# ADR-007 Authentication, Session, and Ownership

Status: Accepted for implementation.

## Context

The repository already authenticates Supabase bearer tokens on the Python API and applies request-scoped PostgreSQL ownership claims, but there is no browser session architecture, no explicit session recovery flow, and no finalized hosted application contract for object-level ownership protection.

## Decision

- Supabase Auth supplies bearer identity.
- The authenticated user UUID becomes the request owner.
- Cloud Run validates identity through Supabase Auth.
- Request-scoped PostgreSQL claims and role `authenticated` preserve RLS.
- The browser never receives the Supabase service-role key.
- Protected routes must prevent object-level ownership leakage.
- Token refresh and session recovery are explicit application behaviors.

## Detailed Topology or Contract

- Browser sign-in and refresh flow uses Supabase Auth sessions managed by the web application.
- Server-side web code exchanges or refreshes browser sessions without exposing privileged service-role credentials.
- API requests carry bearer access tokens to the Python API.
- Cloud Run validates tokens against Supabase Auth and binds:
  - `request.jwt.claim.sub`
  - `request.jwt.claim.role`
  - `request.jwt.claims`
- PostgreSQL RLS remains the hosted ownership enforcement layer for authenticated project access.
- UI route guards and API responses must avoid leaking whether another user’s object exists; object-level failures resolve through authenticated ownership checks.

## Security Consequences

- Service-role secrets stay server-side only.
- Ownership enforcement is layered: browser session checks, API auth, request-scoped claims, and Supabase RLS.
- Session recovery must avoid storing tokens in unsafe caches or client-visible logs.

## Data/Migration Consequences

- Existing `projects.owner_id` and project-derived RLS functions remain the ownership foundation.
- Session persistence is application-layer state, not a change to domain authority records.

## Testing Consequences

- Tests must cover sign-in, token expiry, token refresh, session recovery after reload, unauthorized route protection, and cross-user object isolation.
- Hosted tests must prove that direct API calls with mismatched ownership cannot bypass RLS.

## Operational Consequences

- Auth outages, token validation latency, and refresh behavior become first-class application concerns.
- Session expiry and recovery states need explicit UX and operational observability.
- Staging and production redirect URLs must be exact and environment-specific.

## Rejected Alternatives

- Using service-role credentials in the browser: rejected because it would bypass hosted ownership security.
- Replacing Supabase Auth with handwritten session tables in this phase: rejected because the repository already depends on Supabase Auth and RLS.
- Treating 404 and 403 behavior casually for owned objects: rejected because ownership leakage must be deliberate and consistent.

## Dependencies on Later Groups

- Group 10 for web auth foundation
- Group 11 for authenticated project flows
- Group 19 for protected preview and deployment configuration
