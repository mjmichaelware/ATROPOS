# SpecGraph Foundry Web

Manual Next.js App Router foundation for the SpecGraph Foundry production application.

## Requirements

- Node.js `>=20.9.0`
- npm `>=10`
- Python backend contract at `../specgraph-foundry/openapi/specgraph-v1.yaml`

On Termux or proot environments, use the webpack scripts because native Turbopack/SWC optional binaries may not be available.

## Setup

```bash
cd apps/web
npm ci
npm run api:types:check
```

## Common Commands

```bash
npm run dev
npm run dev:webpack
npm run api:types
npm run typecheck
npm run lint
npm test
npm run e2e:list
npm run build:termux
```

Do not run Playwright browser installation on Android/Termux. `npm run e2e:list` verifies the browser specs without requiring local browsers.

## Environment

Copy `.env.example` to a local ignored environment file.

- `NEXT_PUBLIC_SPECGRAPH_API_URL`: public Python API origin
- `NEXT_PUBLIC_SUPABASE_URL`: Supabase project URL
- `NEXT_PUBLIC_SUPABASE_ANON_KEY`: Supabase anon key only
- `SPECGRAPH_WEB_BASE_URL`: server-side web origin for safe redirects

No Supabase service-role key, database URL, bearer token, refresh token, or signed URL belongs in web source or public environment variables.

## Security Boundaries

- API types are generated from `../specgraph-foundry/openapi/specgraph-v1.yaml`.
- The browser Supabase client uses only public anon credentials.
- Server helpers call Supabase `getUser()` rather than trusting unverified local claims.
- Auth tokens are not stored in `localStorage`.
- Query keys must not include tokens, emails, cursors, signed URLs, or source content.
- Private API fetches use `cache: no-store`.

## Auth and Projects

- Public routes live under `/` and `/auth/*`.
- Application routes under `/projects` require a verified Supabase user.
- Project directory data comes from `GET /v1/projects` with cursor pagination.
- Project creation uses the real `POST /v1/projects` contract and navigates to `/projects/{projectId}` on success.
- The command center reads project, workspace, readiness, and operation APIs without fabricating counts or readiness.

## Sources Workspace

- `/projects/{projectId}/sources` uses the real source workspace, document listing, upload-intent, finalize, operation, and atoms APIs.
- Upload bytes go directly to the signed Storage URL returned by the API; the signed URL is validated for Supabase origin and never displayed or persisted.
- Client hashes and format checks are preflight convenience only. Server finalization remains the authority for bytes, media type, SHA-256, derivation, and operation completion.
- `/projects/{projectId}/sources/{documentId}` distinguishes raw authority from derived text and provides bounded provenance, section/chunk, and atom previews.
- Browser tests use fakes/MSW only; do not contact real Supabase, Storage, or API services during local development.

## Research Workspace

- `/projects/{projectId}/research` uses the real research workspace, gap-matrix, and paginated research-task APIs.
- `/projects/{projectId}/research/tasks/{taskId}` supports task claim, heartbeat, evidence creation, and asynchronous completion polling through the authenticated API.
- Lease worker identity is generated per tab, kept in memory only, and cleared on unmount or lease loss. Lease tokens are never displayed or persisted.
- Evidence and conclusion text are rendered as plain text and are never stored in localStorage, query keys, telemetry, or local files.
- NOT_APPLICABLE requires an explicit justification and is visually distinct from unresolved or unknown work.
- Source authority, research evidence, and research conclusions remain separate in the interface and test coverage.

## Graph Foundation

- `/projects/{projectId}/graph` renders the real planning-workspace, plan-detail (`authority_graph`/`execution_graph`), and paginated authority-relation APIs. See `docs/product/GRAPH_STUDIO_FOUNDATION.md` for the full design reference.
- React Flow (`@xyflow/react`) is the renderer and interaction surface only; it is never treated as semantic authority. Node movement changes visual layout data only and can never create, delete, or alter an authority relation, execution dependency, readiness, verification, or provenance fact.
- Semantic graph data (`src/lib/graph/schemas.ts` `SemanticGraph*` types) and visual layout data (`VisualLayoutState`) are structurally separate types with pure transformation boundaries in `src/lib/graph/transform.ts` and `src/lib/graph/layout-preferences.ts`. No server layout-persistence endpoint exists yet; layout preferences are saved to a bounded, per-device `localStorage` boundary only, and the UI states this honestly (see the Freeform layout control copy).
- Deterministic automatic layout (Blueprint, Compact, Focus) runs through `elkjs` inside a dedicated Web Worker (`src/workers/graph-layout.worker.ts`), driven by a generation-numbered request/response protocol (`src/lib/graph/layout-client.ts`) that discards stale/superseded responses and never lets a slower prior response overwrite newer state. The worker terminates on unmount. Freeform layout is manual positioning and does not invoke the worker.
- Node/edge categories (`atom`, `plan-stage`, `execution-stage`, `verification-stage`, etc.) are derived only from real `node_type`/`edge_type` values the backend actually returns (`ATOM`, `CONTRACT`, `IMPLEMENTATION`, `VERIFICATION`, `MUST_PRECEDE`, and the seven `RELATION_TYPES`). Unrecognized types render as a safe, neutral `unknown` category rather than being guessed.
- Search and filters operate only over the currently loaded, bounded subset and are labeled as such — this is not server-wide search.
- An accessible list/table alternative (`src/components/graph/graph-accessible-list.tsx`) presents the same loaded/filtered subset with keyboard navigation, non-color status/type distinctions, and client-side pagination (50 rows/page) so large graphs never render an unbounded DOM table.
- Large-graph safe mode: deterministic fixtures at 100/1,000/10,000 nodes (`src/lib/graph/fixtures.ts`, test-only, never imported by production modules — enforced by a dedicated test) drive size-tiered rendering budgets (`src/lib/graph/zoom.ts`). The 10,000-node fixture is never rendered as 10,000 simultaneous detailed DOM nodes.
- Tests: `src/lib/graph/graph.test.ts`, `src/lib/graph/layout.test.ts`, `src/components/graph/graph.test.tsx`, `src/components/graph/graph-performance.test.tsx`, and `e2e/graph.spec.ts` (listing only, no local browser install).
- **Unverified in this foundation:** 60fps proof, physical Android device behavior, hosted network timing, and a measured 2.5-second shell budget. Only fixture-based, locally-measured proof exists so far.

## Authority Planning and Execution Graphs

- Built on top of the Graph Foundation renderer/layout/camera/accessibility system (`apps/web/src/components/graph/graph-workspace.tsx`); no second renderer or duplicate graph engine was created. Planning-specific logic lives in `src/lib/planning/*` and UI in `src/components/planning/*`.
- Authority relation creation (`POST /v1/projects/{projectId}/relations`) supports all seven real relation types (`REQUIRES`, `REFINES`, `CONFLICTS_WITH`, `DUPLICATES`, `IMPLEMENTS`, `VERIFIES`, `RELATES_TO`), validates source/target/type/confidence/rationale client-side before submission, and always sends `inferred: false` for manual creation. The endpoint does not define an Idempotency-Key header in the OpenAPI contract, so none is fabricated; duplicate-submit is prevented by disabling the form while a mutation is pending.
- A pure, deterministic, iterative (non-recursive) cycle-advisory utility (`src/lib/planning/cycle.ts`) evaluates only whether a proposed `REQUIRES` relation would close a cycle in the currently loaded relation subset. Non-`REQUIRES` relation types are explicitly marked not-applicable, since only `REQUIRES` contributes to execution ordering. A "no cycle in the loaded subset" result is never presented as proof the full project graph is acyclic — the server performs the authoritative check on submission, and server rejection is what actually blocks an invalid relation.
- Plan synthesis (`POST /v1/projects/{projectId}/plans`) requires an explicit, un-preselected choice of `allow_open_research` before the Synthesize button enables — choosing to allow open research does not resolve unresolved research dimensions, it only permits synthesis despite them, and the UI explains this and links back to Research.
- Plan history, explicit plan selection via the `?plan=` URL parameter, and plan verification (`POST /v1/plans/{planId}/verify`) all poll the real async operation to a terminal state. An explicitly URL-selected plan that cannot be found or is not accessible is reported honestly and is never silently replaced with the latest plan.
- Execution-mode inspection shows real `CONTRACT`/`IMPLEMENTATION`/`VERIFICATION` stages, `MUST_PRECEDE` dependency edges, and plan bindings (`src/lib/planning/bindings.ts`). Canonical node readiness comes only from the server's `ready_nodes` list on the plan detail response — it is never derived from loaded predecessor edges.
- Verification findings show only real server-returned `severity`/`code`/`message`/`entity_id` fields, grouped and filterable by severity (`src/lib/planning/findings.ts`). A finding's "Focus in graph" action only appears when its `entity_id` actually matches a currently loaded graph node ID.
- Shareable, non-sensitive planning state (`plan`, `finding` severity filter) is added to the same graph-route URL state as Group 14's `mode`/`layout`/`selected`/`view` (`src/lib/graph/url-state.ts`). Relation rationale, finding messages, plan payloads, and atom statements are never placed in the URL or in `localStorage` — only positions/viewport/algorithm remain in the client-local layout-preference boundary.
- A failed mutation (relation creation, synthesis, or verification) surfaces a normalized error and leaves the previously rendered graph and plan state completely unchanged — no optimistic semantic updates are made.
- **Not implemented (Group 16 scope):** execution-run start/control, worker leases, receipts, runtime attempts, providers, routing, paid unlocks, exports, and handoff. None of these controls exist on the graph route.
- Tests: `src/lib/planning/planning.test.ts` (cycle detection, relation validation, plan/finding status normalization, binding/readiness lookups), `src/components/graph/graph-planning.test.tsx` (relation creation, cycle advisory, synthesis choice gating, plan selection/URL restore, verification/findings/focus, execution bindings and server-ready status, honest missing-plan handling, mutation-failure graph preservation, Group 16 control absence), and 28 additional `e2e/graph.spec.ts` scenarios (listing only).
- **Unverified in this feature:** hosted browser execution of any mutation, physical Android device behavior, axe automated scan execution, and hosted network timing — only local Vitest/Testing Library coverage and a Playwright listing exist so far.

## Handoff, Execution, and Routing

- Three routes built on the Graph Foundation's HUD shell, no second design system: `/projects/{projectId}/handoff` (bounded workspace overview, bindings, exports, execution-run start), `/projects/{projectId}/executions/{runId}` (real run detail, receipts, findings, independent verification), `/projects/{projectId}/routing` (policy, providers, renderers, paid unlocks, route decisions). Logic lives in `src/lib/{handoff,execution,routing}/*`, UI in `src/components/{handoff,execution,routing}/*`.
- Handoff-workspace (`GET /v1/projects/{projectId}/handoff-workspace`) is bounded (preview + count + `has_more` per section); it has no `plans` list, so export generation and execution-run start require the operator to paste a real plan ID (with copy pointing to Graph → Plans as the source) rather than fabricating a plan picker the contract doesn't support.
- Bindings, providers, and renderers are create-or-update resources: creation sends a fresh `Idempotency-Key` (via `client.createIdempotencyKey()`) and no `If-Match`; editing an existing record sends the same fresh idempotency key plus the `If-Match` ETag captured from the prior list/get response. A stale `If-Match` (412) or a missing one where the server requires it (428) is surfaced as a concise, non-destructive conflict with a Reload action — the in-progress form value is never silently overwritten. The routing policy form uses the same rule and is keyed by its own `etag` (remount-on-fetch, not `useEffect`-driven sync) so edits always start from the version that was actually loaded.
- Export generation (`POST /v1/plans/{planId}/exports`), export verification (`POST /v1/exports/{exportId}/verify`), and execution-run start (`POST /v1/plans/{planId}/execution-runs`) are all durable async operations: the mutation posts with a fresh idempotency key, then polls the returned operation `Location` via `client.pollOperation` to a terminal state. Execution-run start only ever fires from an operator-entered plan ID and runtime system/run ID — there is no client-inferred "eligible to run" state, and a failed start leaves prior workspace state untouched.
- Downloads (`GET /v1/exports/{exportId}/download`) are gated on the export's real server-reported `VERIFIED` status. The returned signed URLs are opened only via an explicit user click (`openSignedDownload`, which validates HTTPS + same Supabase origin + no embedded credentials first) and are never written to component state beyond the triggering render, URL state, `localStorage`, `sessionStorage`, logs, or toasts — requesting again always calls the endpoint fresh rather than reusing a cached link.
- Execution-run detail (`GET /v1/execution-runs/{runId}`) renders only real, safely-typed fields: timeline/status, stage/node table with lease owner (never a lease token), server-`ready_nodes` membership (never client-inferred), attempts, findings, and receipts. Receipts are passed through a dedicated redaction layer (`src/lib/execution/receipts.ts`) before rendering — the backend returns a raw `evidence` object on each receipt, which is deliberately never rendered; only its SHA-256 hash and a top-level field count are shown. Claim, heartbeat, and receipt-submission are worker/runtime operations that require a `worker_id` the browser has no business fabricating, so no UI form or button exists for them anywhere in this route — only their resulting safe state (node status, lease owner, attempt/receipt metadata) is visible. Independent verification (`POST /v1/execution-runs/{runId}/verify`) uses the same idempotency-key + operation-polling pattern as exports.
- Paid routing is never automatic. The route/provider/paid-unlock contracts expose no numeric price or currency field, so cost is represented honestly via each provider's real `cost_class` string (`src/lib/routing/cost.ts`); a provider with an unknown or empty cost class blocks the "Review unlock" action entirely. Granting an unlock always requires an explicit two-step confirmation (Review → Confirm) showing the real provider identity and cost class before any request is sent, and any risk (disabled provider, non-`READY` health) is surfaced as a warning. Route decisions (`POST /v1/projects/{projectId}/route-decisions`, `GET /v1/route-decisions/{decisionId}`) show only the server's real selected provider/renderer/status/reason code/cost/risk/timestamp fields — unknown values render as literal "Unknown" text, never a guess, and the UI states plainly that it holds no client-side routing authority.
- State law is unchanged from prior groups: TanStack Query owns all server state (`src/lib/{handoff,execution,routing}/queries.ts` / `mutations.ts`), no raw `fetch` appears in any component, and connectivity uses the existing `useOnlineStatus` hook (`src/lib/graph/connectivity.ts`) rather than a new implementation.
- Tests: `src/lib/handoff/handoff.test.ts`, `src/lib/execution/execution.test.ts`, `src/lib/routing/routing.test.ts` (pure logic — redaction, cost gating, download-URL validation); `src/components/handoff/handoff.test.tsx`, `src/components/execution/execution.test.tsx`, `src/components/routing/routing.test.tsx` (workspace-level behavior: offline/error states, idempotency/If-Match on mutations, operation polling, receipt redaction, ETag conflict handling, paid-unlock gating and confirmation); `e2e/handoff.spec.ts`, `e2e/execution.spec.ts`, `e2e/routing.spec.ts` (listing only, no local browser install).
- **Not implemented in this group:** the Group 17 visual/responsive polish pass over these three routes.
- **Unverified in this feature:** hosted browser execution of any mutation, physical-device behavior, real signed-URL download completion, real worker claim/heartbeat/receipt-submission runtime behavior, and hosted network timing — only local Vitest/Testing Library coverage and a Playwright listing exist so far.

## Visual, Responsive, Motion, and Interaction Finish

Full architecture record: `docs/product/VISUAL_RESPONSIVE_FINISH.md`.

- **Central navigation**: `src/components/navigation/routes.ts` is the only place a `/projects/{id}/...` path is assembled — every card, form redirect, and nav surface imports a builder from it instead. `use-nav-items.ts` computes one shared nav-item list (route, label, active state) consumed identically by the header, sidebar, and mobile sheet, and `route-accent.tsx` derives the shell's route-context accent from the same registry.
- **Intrinsic responsiveness**: viewport `@media` queries are kept only as `@supports`-gated, fully-fallback outer-shell safeguards; the shell nav morph (rail vs. mobile sheet) responds to `@container sg-shell` (the shell's own measured width), card grids use `auto-fit`/`minmax()`, and `.sg-graph-table` scrolls within its own generated wrapper box instead of ever forcing page-level horizontal overflow.
- **Motion**: tokenized durations/easing/distance/scale in `src/styles/tokens.css`; `.sg-pressable` and `[data-selected]` utilities in `src/styles/utilities.css`; `prefers-reduced-motion: reduce` collapses every motion token to near-zero plus a global `!important` backstop, so components never need their own reduced-motion branch. `src/components/ui/motion.ts` adds a workload-driven (never device-driven) adaptive-performance tier and a Web Animations cleanup hook. `src/components/ui/view-transition.ts` wraps the platform `document.startViewTransition` (feature-detected, never React's experimental `<ViewTransition>`) and is wired into the shared `Tabs` component used by Handoff, Routing, and Sources.
- **Theme**: OKLCH semantic domain tokens with `@supports`-gated hex fallbacks; light is tuned independently (not an inverted dark theme); `forced-colors`/`prefers-reduced-transparency` get dedicated, explicit treatments.
- Tests: `src/components/navigation/{navigation,route-accent}.test.{ts,tsx}`, `src/components/ui/{motion,view-transition}.test.ts`, plus a shared `src/test/match-media.ts` stub adopted by `handoff`/`research`/`routing`/`sources` component tests now that they transitively exercise `prefers-reduced-motion` through `Tabs`. `e2e/visual-responsive.spec.ts` (listing only) samples representative dimensions from 320×568 through a 2560×1080 ultra-wide as verification points, not as the layout architecture.
- **Not implemented in this group:** any query/mutation/domain/graph-semantic change, and all of Group 18's scope (accessibility conformance audit, PWA/service-worker/offline/manifest work).
- **Unverified in this feature:** browser screenshots, physical-device testing, and hosted/deployed verification — only local Vitest/Testing Library/tsc/eslint coverage and a Playwright listing exist so far.

## Accessibility Conformance and Safe Shell-Only PWA

Full record: `docs/product/ACCESSIBILITY_PWA.md`. WCAG 2.2 AA is the implementation target — **this is not a formal conformance claim**; automated tooling only catches a subset of possible issues.

- **Keyboard/APG**: the shared `Tabs` primitive now implements the WAI-ARIA APG Tabs pattern with manual activation (`ArrowLeft`/`ArrowRight`/`Home`/`End` move focus, `Enter`/`Space` activates) — arrow-browsing never fires the real data fetch a tab switch triggers. Dialogs/sheets rely on Radix's native focus trap and restore-to-trigger behavior, verified directly in `mobile-navigation.test.tsx`.
- **Real violations found and fixed**: a WCAG 2.5.3 (Label in Name) mismatch on the password-visibility toggle and the mobile-nav trigger (removed, replaced with `aria-pressed` where relevant); an `aria-required-children` (critical, axe) violation from `role="grid"`/`role="row"` on the Research gap matrix that didn't implement grid keyboard semantics (roles removed rather than fully building out unused grid navigation); the same violation class on `SourceTable`'s `role="list"` (fixed with explicit `role="listitem"` wrappers); a real AA contrast failure on light-theme `--sg-text-muted` (4.47:1, retuned to 5.7:1+).
- **Automated audit**: `src/test/axe.ts` wraps `axe-core` for component-level checks (two jsdom-incapable rules disabled, documented, never a blanket disable) across the command center, source/research/graph/handoff/execution/routing workspaces, and the paid-unlock confirmation dialog specifically. `src/styles/contrast.test.ts` computes real WCAG contrast ratios for every dark/light/high-contrast token pair — not eyeballed.
- **PWA**: `src/app/manifest.ts` (truthful identity, real 192/512/maskable PNG icons generated from the existing SVG mark via Python stdlib, no dependency added), `public/sw.js` (versioned shell-only cache: precaches only `/offline` and bounded-runtime-caches only immutable `/_next/static/*`; every `/v1/*`, `/api/*`, `/auth/*`, non-GET, cross-origin, `Authorization`-bearing, or `Set-Cookie` response is explicitly excluded), `src/app/offline/page.tsx` (static, no-JS-required, explains data is intentionally not cached), `src/components/pwa/pwa-registration.tsx` (production-only registration, explicit user-controlled "Refresh to update" — never a silent `skipWaiting`/reload).
- Tests: `src/components/ui/tabs.test.tsx` (APG keyboard behavior), `src/components/app-shell/mobile-navigation.test.tsx` (dialog focus trap/restoration), `src/components/pwa/{pwa-registration,sw-content}.test.{tsx,ts}`, `src/app/manifest.test.ts`, `src/app/offline/page.test.tsx`, plus 8 new axe checks across existing domain test files. `e2e/accessibility-pwa.spec.ts` (29 scenarios, listing only).
- **Not implemented in this group:** push notifications, background/periodic sync, offline mutation queues, any cached authenticated/API/private response, custom UA-based install prompts, and all of Groups 19–20's scope.
- **Unverified in this feature:** formal WCAG conformance, manual screen-reader testing (NVDA/VoiceOver/TalkBack), real-browser Playwright execution (the dev server cannot start in this sandbox — missing `NEXT_PUBLIC_*` config, a pre-existing environment limitation), and hosted/physical-device installability.
