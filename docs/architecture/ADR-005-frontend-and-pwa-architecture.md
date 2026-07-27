# ADR-005 Frontend and PWA Architecture

Status: Accepted for implementation.

## Context

The repository has no `apps/web/`, no production frontend, no generated API client, and no PWA implementation. Later groups need one fixed web stack for authentication, data fetching, forms, caching rules, and mobile behavior.

## Decision

- Use `apps/web/` with Next.js App Router and strict TypeScript.
- Use Supabase SSR authentication.
- Use TanStack Query for remote state.
- Use React Hook Form plus Zod for forms and validation.
- Use an OpenAPI-derived client.
- Build a mobile-first responsive PWA.
- URL state owns navigation and filters.
- Server state owns domain truth.
- Service worker caches only safe shell and static assets and never tokens, private payloads, or signed URLs beyond lifetime.

## Detailed Topology or Contract

- `apps/web/` contains the Vercel-deployed Next.js application.
- Route handlers and server components manage authenticated SSR session bootstrapping.
- Browser API calls use the generated client against the Python API origin.
- TanStack Query keys reflect project, source, research, planning, export, execution, routing, and operation resources.
- Forms use Zod-backed schemas aligned with OpenAPI request contracts.
- Navigation state, filters, and selected resource identifiers live in the URL so deep links and refresh recovery are deterministic.
- Service worker scope is limited to shell assets, icons, fonts, and explicitly safe cacheable responses.

## Security Consequences

- Supabase SSR authentication avoids exposing privileged backend credentials in the browser.
- Service worker caching rules must exclude access tokens, signed uploads, signed downloads, and private API payloads.
- Client code must treat the server as the authority for ownership, readiness, and verification states.

## Data/Migration Consequences

- No domain data authority moves to the client.
- Generated client updates become part of schema and contract changes.
- Offline behavior is shell-only until later groups deliberately add safe bounded offline features.

## Testing Consequences

- Web tests must cover SSR auth recovery, route protection, URL state restoration, query invalidation, and mobile layouts.
- PWA tests must verify that cached assets never include tokens or private payloads.
- Contract tests must ensure generated client types align with server responses.

## Operational Consequences

- Frontend deployments are decoupled from backend revisions but must be contract-compatible.
- Preview environments need protected authentication and safe API targets.
- Performance budgets must account for mobile networks and large project payloads.

## Rejected Alternatives

- Building the frontend inside the Python server: rejected because deployment, caching, and frontend tooling become coupled unnecessarily.
- Handwritten fetch utilities without generated types: rejected because contract drift becomes likely.
- Broad offline caching of API responses: rejected because authority data, signed URLs, and private content cannot be safely cached by default.

## Dependencies on Later Groups

- Group 10 for web foundation
- Groups 11-18 for application features, responsive finish, accessibility, and PWA implementation
- Group 02 for OpenAPI-derived client generation
