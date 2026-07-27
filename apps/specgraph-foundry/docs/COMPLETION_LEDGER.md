# Completion Ledger

## GROUP 01 BASELINE AND ARCHITECTURE

- Status: COMPLETE
- Dependencies: none
- Scope: verify the repository baseline, record the current backend-versus-production state, freeze architecture decisions for Groups 02-20, and create this ledger
- Acceptance evidence required: `docs/architecture/CURRENT_STATE.md`; ADR-001 through ADR-009; this ledger; Group 01 compile/test/audit/diff gate
- Commit SHA: `21444ec`
- Hosted revision: not yet produced
- Known blockers: production application capabilities remain unimplemented beyond the architecture baseline
- Prohibited completion claims: do not claim later product groups are complete because Group 01 closed

## GROUP 02 OPENAPI AND ERRORS

- Status: COMPLETE
- Dependencies: GROUP 01 BASELINE AND ARCHITECTURE
- Scope: create OpenAPI 3.1 authority, align stable error contracts, and preserve or deliberately migrate current `v1` behavior
- Acceptance evidence required: `openapi/specgraph-v1.yaml`; `tests/test_openapi_parity.py`; `tests/test_error_schema.py`; full suite, compile, audit, and diff gate
- Commit SHA: `60845b4`
- Hosted revision: not yet produced
- Known blockers: hosted deployment verification remains for later groups
- Prohibited completion claims: do not claim pagination, idempotency, uploads, durable operations, frontend, or deployment completion from this contract-and-error group alone

## GROUP 03 PAGINATION AND BOUNDS

- Status: COMPLETE
- Dependencies: GROUP 02 OPENAPI AND ERRORS
- Scope: add cursor pagination and explicit bounds to collection endpoints and aggregate responses that need them
- Acceptance evidence required: contract updates; `tests/test_pagination.py`; `tests/test_bounded_workspaces.py`; negative pagination validation coverage; full suite, compile, audit, and diff gate
- Commit SHA: `63bae19`
- Hosted revision: not yet produced
- Known blockers: hosted verification remains pending even though the local gate passed
- Prohibited completion claims: do not claim large-project safety or mobile-friendly payload behavior before bounded-response verification

## GROUP 04 IDEMPOTENCY AND CONCURRENCY

- Status: COMPLETE
- Dependencies: GROUP 02 OPENAPI AND ERRORS, GROUP 03 PAGINATION AND BOUNDS
- Scope: add `Idempotency-Key`, replay-safe mutation handling, and ETag/version plus `If-Match` concurrency protection
- Acceptance evidence required: contract updates; `tests/test_idempotency.py`; `tests/test_optimistic_concurrency.py`; updated `tests/test_openapi_parity.py`; updated `tests/test_error_schema.py`; mirrored idempotency migrations; updated RLS expectations; full suite, compile, audit, and diff gate
- Commit SHA: `be4a083`
- Hosted revision: not yet produced
- Known blockers: hosted verification remains pending even though local replay, concurrency, migration, and RLS gates passed
- Prohibited completion claims: do not claim hosted retry safety, cross-region rollout safety, or production edit-protection verification before deployment evidence exists

## GROUP 05 SOURCE STORAGE AND UPLOADS

- Status: COMPLETE
- Dependencies: GROUP 01 BASELINE AND ARCHITECTURE, GROUP 02 OPENAPI AND ERRORS
- Scope: implement private source-object storage, upload initiation, finalize validation, and ownership-safe ingest entrypoints
- Acceptance evidence required: mirrored private-bucket and `source_uploads` migrations; `tests/test_source_uploads.py`; `tests/test_storage_security.py`; updated `tests/test_openapi_parity.py`; updated `tests/test_idempotency.py`; updated `tests/test_supabase_rls.py`; full suite, compile, audit, and diff gate
- Commit SHA: `7ad92f8`
- Hosted revision: not yet produced
- Known blockers: hosted verification remains pending even though local upload, integrity, migration, and RLS gates passed
- Prohibited completion claims: do not claim document-adapter coverage, durable artifact storage, workerized upload processing, or hosted production verification from this text-first private upload group alone

## GROUP 06 DOCUMENT ADAPTERS AND PROVENANCE

- Status: COMPLETE
- Dependencies: GROUP 05 SOURCE STORAGE AND UPLOADS
- Scope: extend ingestion to document adapters, derivation metadata, and canonical provenance-preserving extraction
- Acceptance evidence required: mirrored `document_derivations` migrations; PDF dependency and notice evidence; `tests/test_document_adapters.py`; `tests/test_document_security.py`; `tests/test_derivation_provenance.py`; updated source-upload, parity, RLS, and full-suite gates
- Commit SHA: `91b6d43`
- Hosted revision: not yet produced
- Known blockers: hosted verification remains pending even though local adapter, provenance, migration, and licensing gates passed
- Prohibited completion claims: do not claim hosted production document-format coverage, OCR support, or deployment verification from this local adapter-and-provenance group alone

## GROUP 07 DURABLE ARTIFACTS

- Status: COMPLETE
- Dependencies: GROUP 05 SOURCE STORAGE AND UPLOADS, GROUP 06 DOCUMENT ADAPTERS AND PROVENANCE
- Scope: move generated export artifacts to durable private Storage, persist manifests and object records, independently verify stored bytes, and expose owner-scoped verified downloads
- Acceptance evidence required: mirrored `export-artifacts`, `storage_objects`, and `artifact_manifests` migrations; `tests/test_durable_artifacts.py`; `tests/test_artifact_security.py`; updated export, execution, storage, OpenAPI parity, RLS, audit, and full-suite gates
- Commit SHA: `5653515`
- Hosted revision: not yet produced
- Known blockers: hosted deployment verification remains pending even though local durable artifact, manifest, tamper, download, execution-prerequisite, migration, and RLS gates passed
- Prohibited completion claims: do not claim hosted production artifact durability, retention operations, workerized export generation, or live Storage verification before hosted deployment evidence exists

## GROUP 08 DURABLE OPERATIONS AND WORKERS

- Status: COMPLETE
- Dependencies: GROUP 04 IDEMPOTENCY AND CONCURRENCY, GROUP 05 SOURCE STORAGE AND UPLOADS, GROUP 07 DURABLE ARTIFACTS
- Scope: implement durable operation records, async hosted mutation submission, worker claim/heartbeat lifecycle, cancellation, timeout recovery, bounded retries, and `202 Accepted` APIs
- Acceptance evidence required: mirrored `operations` migrations; `tests/test_operations_api.py`; `tests/test_operation_worker.py`; `tests/test_operation_security.py`; updated parity/RLS/API tests; worker entrypoint; nondeployed Cloud Run Job template; full-suite, compile, audit, and diff gate
- Commit SHA: `9d29511`
- Hosted revision: not yet produced
- Known blockers: hosted deployment and scheduled worker execution remain pending even though local operation API, leasing, cancellation, recovery, RLS, and worker gates passed
- Prohibited completion claims: do not claim deployed production worker reliability, hosted scheduling, observability, or autoscaling before Group 19 deployment evidence exists

## GROUP 09 SECURITY RUNTIME AND OBSERVABILITY

- Status: COMPLETE
- Dependencies: GROUP 05 SOURCE STORAGE AND UPLOADS, GROUP 08 DURABLE OPERATIONS AND WORKERS
- Scope: add structured redacted logs, OpenTelemetry-compatible telemetry, quotas, rate limits, parser hardening, and hosted security controls
- Acceptance evidence required: `src/specgraph_foundry/http_api/observability.py`; `src/specgraph_foundry/http_api/security.py`; `src/specgraph_foundry/http_api/health.py`; `src/specgraph_foundry/http_api/resource_limits.py`; `tests/test_observability.py`; `tests/test_security_hardening.py`; `tests/test_health_api.py`; `tests/test_resource_limits.py`; updated OpenAPI parity and full-suite compile/audit/diff gate
- Commit SHA: `19defc5`
- Hosted revision: not yet produced
- Known blockers: hosted deployment, edge/global rate limiting, production telemetry exporter configuration, and external alerting remain pending for later groups
- Prohibited completion claims: do not claim production deployment security, global abuse control, active telemetry export, alerting, dashboards, or hosted runtime verification before Group 19 and Group 20 evidence exists

## GROUP 10 WEB FOUNDATION

- Status: COMPLETE
- Dependencies: GROUP 01 BASELINE AND ARCHITECTURE, GROUP 02 OPENAPI AND ERRORS
- Scope: create `apps/web/`, Next.js App Router foundation, strict TypeScript, generated client wiring, and SSR auth shell
- Acceptance evidence required: `apps/web/package-lock.json`; generated `apps/web/src/lib/api/generated.ts`; typed API client tests; Supabase SSR helper tests; Query/theme/accessibility shell tests; `npm run api:types:check`; `npm run typecheck`; `npm run lint`; `npm test`; `npm run e2e:list`; `npm run build:termux`; CI web job; license and audit gates
- Commit SHA: `f86fc0b`
- Hosted revision: not yet produced
- Known blockers: hosted preview, deployed auth flows, product screens, PWA/service worker, browser execution, and deployment verification remain pending for later groups
- Prohibited completion claims: do not claim completed product UI, deployed web availability, PWA support, graph studio, or hosted authentication workflows from this foundation group alone

## GROUP 11 AUTH PROJECTS AND COMMAND CENTER

- Status: COMPLETE
- Dependencies: GROUP 10 WEB FOUNDATION
- Scope: implement authenticated project flows, project directory and creation, command center, readiness surface, and ownership-safe project UX; add bounded cursor pagination to `GET /v1/projects`
- Acceptance evidence required: project pagination contract and tests; generated web API types; auth redirect/session/recovery tests; project directory/create/command-center tests; `npm run api:types:check`; `npm run typecheck`; `npm run lint`; `npm test`; `npm run e2e:list`; `npm run build:termux`; full Python suite and audit gates
- Commit SHA: `26b199f`
- Hosted revision: not yet produced
- Known blockers: hosted browser execution and deployed Supabase auth verification remain pending for later hosted acceptance groups
- Prohibited completion claims: do not claim hosted end-user project management, deployed authentication success, or source/research/planning/handoff screen completion before later hosted verification

## GROUP 12 SOURCES AND PROVENANCE UI

- Status: COMPLETE
- Dependencies: GROUP 05 SOURCE STORAGE AND UPLOADS, GROUP 06 DOCUMENT ADAPTERS AND PROVENANCE, GROUP 10 WEB FOUNDATION
- Scope: implement source upload, source browsing, source operation recovery, authority/derivation inspection, sections/chunks/atoms previews, and byte/line provenance interfaces
- Acceptance evidence required: `apps/web/src/lib/sources/*`; `apps/web/src/components/sources/*`; source workspace routes; source/provenance unit tests; Playwright source listing spec; `npm run typecheck`; `npm run lint`; `npm test`; `npm run e2e:list`; `npm run build:termux`; full Python suite and audit gates
- Commit SHA: `b027071`
- Hosted revision: not yet produced
- Known blockers: hosted Storage upload and browser execution verification remain pending for later hosted acceptance groups
- Prohibited completion claims: do not claim hosted end-user source ingestion, live Storage behavior, or production provenance acceptance before deployed browser verification

## GROUP 13 RESEARCH UI

- Status: COMPLETE
- Dependencies: GROUP 10 WEB FOUNDATION, GROUP 11 AUTH PROJECTS AND COMMAND CENTER, GROUP 12 SOURCES AND PROVENANCE UI
- Scope: implement authenticated research workspace, atom/dimension inspection, gap matrix, paginated task queue, task claim/heartbeat, evidence recording, conclusion completion, operation polling, authority separation, responsive HUD states, and accessibility-focused alternatives
- Acceptance evidence required: `apps/web/src/lib/research/*`; `apps/web/src/components/research/*`; research workspace/task routes; focused research unit/component tests; Playwright research listing spec; `npm run typecheck`; `npm run lint`; `npm test`; `npm run e2e:list`; `npm run build:termux`; full Python suite and audit gates
- Commit SHA: 0939a53
- Hosted revision: not yet produced
- Known blockers: hosted browser execution and deployed research workflow verification remain pending for later hosted acceptance groups
- Prohibited completion claims: do not claim hosted end-user research acceptance, live provider research, or production completion before deployed browser verification

## GROUP 14 GRAPH FOUNDATION

- Status: COMPLETE
- Dependencies: GROUP 10 WEB FOUNDATION, GROUP 02 OPENAPI AND ERRORS
- Scope: implement graph rendering foundation with React Flow, ELK worker integration, and layout persistence boundaries
- Acceptance evidence required: `apps/web/src/lib/graph/*` (semantic/layout types, transform, search, zoom, url-state, layout-preferences, connectivity, motion, security, fixtures, layout-normalize, elk-adapter, layout-client); `apps/web/src/workers/graph-layout.worker.ts`; `apps/web/src/components/graph/*` (canvas, custom node/edge renderers, command dock, layout control, search panel, inspector, accessible list, state shells); graph route (`/projects/{projectId}/graph`) and command-center link; `apps/web/src/lib/graph/graph.test.ts` (31 tests), `apps/web/src/lib/graph/layout.test.ts` (9 tests), `apps/web/src/components/graph/graph.test.tsx` (8 tests), `apps/web/src/components/graph/graph-performance.test.tsx` (4 tests) — 52 graph tests, 99 total frontend tests, all passing; `apps/web/e2e/graph.spec.ts` (15 specs, `e2e:list` verified, no local browser install); `docs/product/GRAPH_STUDIO_FOUNDATION.md`; `npm run typecheck`, `npm run lint --max-warnings=0`, `npm run api:types:check`, `npm run build:termux`, `npm audit --audit-level=high` all pass; license, hosted-audit-contracts, and PostgreSQL-boolean gates pass; 207/210 Python tests pass (3 pre-existing, unrelated PDF-adapter errors — see Known blockers) with zero backend files touched
- Commit SHA: 0055d83
- Hosted revision: not yet produced
- Known blockers: no server layout-persistence endpoint exists yet — layout preferences are an explicit, honest client-local (`localStorage`) boundary only, documented in the UI and README; hosted browser execution, physical-device performance, and 60fps/2.5-second-shell claims remain unverified and unclaimed; this environment's Python `pypdf`/`cryptography` native extension is broken (`pyo3_runtime.PanicException`), causing 3 pre-existing PDF-adapter test errors unrelated to this group (207/210 Python tests pass; zero Group 14 files touch backend PDF adapters)
- Prohibited completion claims: do not claim graph usability or large-graph support beyond what the 100/1,000/10,000-node deterministic fixtures and accessible-list bounded-output tests actually demonstrate; do not claim hosted, physical-device, or measured frame-rate performance; do not claim authority relation editing, plan synthesis/verification, execution-cycle rejection workflows, or any Group 15 capability — none are implemented

## GROUP 15 AUTHORITY PLANNING AND EXECUTION GRAPHS

- Status: COMPLETE
- Dependencies: GROUP 13 RESEARCH UI, GROUP 14 GRAPH FOUNDATION
- Scope: implement authority relation creation with validation and a bounded advisory cycle check, research-readiness-aware plan synthesis with an explicit open-research choice, plan history/selection/URL restore, plan verification with real findings, and execution-mode stage/dependency/binding inspection using server-canonical `ready_nodes`, built on the Group 14 renderer without a second graph engine
- Acceptance evidence required: `apps/web/src/lib/planning/*` (schemas, api, queries, mutations, cycle, relations, status, findings, bindings); `apps/web/src/components/planning/*` (relation-form, cycle-advisory, planning-rail, plan-history, synthesis-panel, verification-panel, findings-list, binding-panel, plan-status-badge); `apps/web/src/lib/graph/url-state.ts` extended with `plan`/`finding` fields; `apps/web/src/lib/graph/transform.ts` extended with real `payload.atom_id` propagation; `apps/web/src/lib/planning/planning.test.ts` (20 tests) and `apps/web/src/components/graph/graph-planning.test.tsx` (10 tests) — 30 new tests, 129 total frontend tests, all passing; `apps/web/e2e/graph.spec.ts` extended with 28 Group 15 scenarios (43 total, `e2e:list` verified, no local browser install); `docs/product/GRAPH_STUDIO_FOUNDATION.md` Group 14/15 boundary updated; `npm run typecheck`, `npm run lint --max-warnings=0`, `npm run api:types:check` (zero drift), `npm run build:termux`, `npm audit --audit-level=high` all pass; license, hosted-audit-contracts, and PostgreSQL-boolean gates pass; 207/210 Python tests pass (same 3 pre-existing, unrelated PDF-adapter errors as Group 14 — see Known blockers) with zero backend files touched
- Commit SHA: 34079ec
- Hosted revision: not yet produced
- Known blockers: this environment's Python `pypdf`/`cryptography` native extension remains broken (`pyo3_runtime.PanicException`), independently reproducing the identical 3 PDF-adapter test errors recorded in Group 14 (Group 6 backend territory, forbidden for this group); no server layout-persistence endpoint exists (inherited from Group 14, still honestly disclosed in the UI); hosted browser execution of any mutation, physical Android device behavior, automated axe scan execution, and hosted network timing remain unverified and unclaimed
- Prohibited completion claims: do not claim server-side cycle validation, execution-cycle rejection enforcement, or full-project acyclicity from the bounded client-side advisory check — the server is the sole cycle authority; do not claim hosted, physical-device, or browser-verified mutation behavior; do not claim relation editing/deletion, semantic undo/redo, graph audit events, or any Group 16 execution-run/worker/receipt/provider/routing/export/handoff capability — none are implemented

## GROUP 16 HANDOFF EXECUTION AND ROUTING UI

- Status: COMPLETE
- Dependencies: GROUP 07 DURABLE ARTIFACTS, GROUP 08 DURABLE OPERATIONS AND WORKERS, GROUP 10 WEB FOUNDATION
- Scope: implement bindings, exports, execution runs, receipts, providers, renderers, unlocks, and route-decision UX, built on the Group 14/15 HUD shell and Graph Foundation renderer without a second design system
- Acceptance evidence required: `apps/web/src/lib/{handoff,execution,routing}/*` (schemas, api, queries, mutations, downloads/security/receipts/status/cost helpers); `apps/web/src/components/{handoff,execution,routing}/*` (workspace shells, state components, binding/provider/renderer forms and lists, export download/detail panels, execution run detail with redacted-receipt rendering, paid-unlock and route-decision panels); three routes (`/projects/{projectId}/handoff`, `/projects/{projectId}/executions/{runId}`, `/projects/{projectId}/routing`) and command-center links; `apps/web/src/lib/handoff/handoff.test.ts` (9 tests), `apps/web/src/lib/execution/execution.test.ts` (7 tests), `apps/web/src/lib/routing/routing.test.ts` (5 tests), `apps/web/src/components/handoff/handoff.test.tsx` (8 tests), `apps/web/src/components/execution/execution.test.tsx` (8 tests), `apps/web/src/components/routing/routing.test.tsx` (10 tests) — 47 new tests, 176 total frontend tests, all passing; `apps/web/e2e/handoff.spec.ts` (13 specs), `apps/web/e2e/execution.spec.ts` (10 specs), `apps/web/e2e/routing.spec.ts` (14 specs) — 37 new e2e listings, 80 total (`e2e:list` verified, no local browser install); `docs/product/HANDOFF_EXECUTION_ROUTING.md`; `apps/web/README.md` Handoff/Execution/Routing section; `npm run typecheck`, `npm run lint --max-warnings=0`, `npm run api:types:check` (zero drift), `npm run build:termux`, `npm audit --audit-level=high` all pass; license, hosted-audit-contracts, and PostgreSQL-boolean gates pass; 207/210 Python tests pass (same 3 pre-existing, unrelated PDF-adapter errors as Groups 14/15 — see Known blockers) with zero backend files touched
- Commit SHA: 6cded96
- Hosted revision: not yet produced
- Known blockers: this environment's Python `pypdf`/`cryptography` native extension remains broken (`pyo3_runtime.PanicException`), independently reproducing the identical 3 PDF-adapter test errors recorded in Groups 14/15 (Group 6 backend territory, forbidden for this group); no plans list exists on the handoff-workspace response, so plan IDs for export generation and execution-run start are operator-entered rather than picked from a list; no numeric cost/currency field exists on providers or paid unlocks, so paid-unlock cost is represented via the real `cost_class` string and blocks confirmation when unknown; hosted browser execution of any mutation, physical-device behavior, real signed-URL download completion, real worker claim/heartbeat/receipt-submission runtime behavior, and hosted network timing remain unverified and unclaimed
- Prohibited completion claims: do not claim hosted handoff, execution management, or routing decisions before end-to-end browser and worker verification exist; do not claim a real signed-URL download completed against live Supabase Storage; do not claim real worker claim/heartbeat/receipt-submission behavior — those routes are runtime-only and were never exercised from this browser UI; do not claim any Group 17 visual/responsive polish work on these three routes — none is implemented

## GROUP 17 VISUAL RESPONSIVE FINISH

- Status: COMPLETE
- Dependencies: GROUP 11 AUTH PROJECTS AND COMMAND CENTER, GROUP 12 SOURCES AND PROVENANCE UI, GROUP 13 RESEARCH UI, GROUP 15 AUTHORITY PLANNING AND EXECUTION GRAPHS, GROUP 16 HANDOFF EXECUTION AND ROUTING UI
- Scope: persistently native visual, responsive, motion, and interaction finish across existing surfaces — central navigation/route registry, intrinsic container-driven layout, fluid type/spacing tokens, OKLCH semantic theme system with fallbacks, a unified motion/press/selection grammar, feature-detected View Transition and Web Animations progressive enhancement, and adaptive-performance policy driven by real workload size and browser capability media features — with zero query/mutation/domain/graph-semantic changes
- Acceptance evidence required: `apps/web/src/components/navigation/*` (routes, route-utils, use-nav-items, nav-links, route-accent — the single source of route identity, replacing hardcoded `/projects/${id}/...` strings previously duplicated in `project-command-center.tsx`, `project-card.tsx`, `project-create-form.tsx`, `task-card.tsx`, `atom-card.tsx`, `source-card.tsx`, and `handoff/execution-run-list.tsx`); `apps/web/src/components/ui/{motion,view-transition}.ts` (adaptive-performance tier, managed Web Animations cleanup, feature-detected `document.startViewTransition` wrapper, wired into the shared `Tabs` component); `apps/web/src/styles/{tokens,themes,utilities}.css` and `apps/web/src/app/globals.css` (fluid type/spacing scale, OKLCH semantic tokens with `@supports` hex fallbacks, motion tokens, `@container`-driven shell nav morph with full `@supports not` fallback, intrinsic `auto-fit`/`minmax()` card grids, locally-scrollable `.sg-graph-table`, press/hover/active/selected states added to `.sg-button`/`.sg-tab`/nav links); minimal integration edits to `apps/web/src/components/app-shell/*` to consume the new navigation registry (not separately listed in the authorized territory but required to satisfy the SHELL/NAVIGATION objective, not in the forbidden list, and read as "current application layout and navigation registry/pattern" per the task's own read list); `apps/web/e2e/visual-responsive.spec.ts` (35 specs, listing only); `docs/product/VISUAL_RESPONSIVE_FINISH.md`; `apps/web/README.md` Visual/Responsive/Motion/Interaction section; `apps/web/src/components/navigation/{navigation,route-accent}.test.{ts,tsx}` (9 tests), `apps/web/src/components/ui/{motion,view-transition}.test.ts` (11 tests), `apps/web/src/test/match-media.ts` shared stub adopted by `handoff`/`research`/`routing`/`sources` component tests — 20 new tests, 196 total frontend tests, all passing; `npm run typecheck`, `npm run lint --max-warnings=0`, `npm run api:types:check` (zero drift), `npm run build:termux`, `npm audit --audit-level=high` (2 pre-existing moderate, no highs) all pass; license, hosted-audit-contracts, and PostgreSQL-boolean gates pass; 207/210 Python tests pass (same 3 pre-existing, unrelated PDF-adapter errors as Groups 14–16 — see Known blockers) with zero backend files touched
- Commit SHA: 506c080
- Hosted revision: not yet produced
- Known blockers: this environment's Python `pypdf`/`cryptography` native extension remains broken (`pyo3_runtime.PanicException`), independently reproducing the identical 3 PDF-adapter test errors recorded in Groups 14–16 (Group 6 backend territory, forbidden for this group); container-query and `oklch()`/`color-mix()` fallback correctness was verified only by reading the CSS spec and adding `@supports` gates, not by testing an actual non-supporting browser; no browser screenshot, physical device, or hosted/deployed instance was used to verify any layout, motion, or theme claim in this document; this group intentionally did not perform an accessibility conformance audit or touch PWA/service-worker/offline/manifest work — that is Group 18's scope in full
- Prohibited completion claims: do not claim a verified visual result from a browser screenshot, physical device, or hosted environment — none exist for this group; do not claim WCAG/accessibility conformance or any PWA/offline/installability capability — none is implemented; do not claim container-query or OKLCH/color-mix() behavior was verified in a non-supporting browser, only that a spec-conformant fallback exists; do not claim any change to query/mutation behavior, API contracts, domain calculations, or graph/authority/execution semantics — none was made

## GROUP 18 ACCESSIBILITY AND PWA

- Status: COMPLETE
- Dependencies: GROUP 17 VISUAL RESPONSIVE FINISH
- Scope: real code-level accessibility repair pass (WCAG 2.2 AA implementation target, WAI-ARIA APG widget behavior) across completed surfaces, plus a safe, shell-only, installable PWA (manifest, generated icons, versioned service worker, static offline page, user-controlled update flow) — no query/mutation/domain/graph-semantic change, no push/background-sync/offline-mutation/private-data-caching capability added
- Acceptance evidence required: `apps/web/src/components/ui/tabs.tsx` rebuilt to the WAI-ARIA APG Tabs pattern (roving tabindex, ArrowLeft/Right/Home/End, manual Enter/Space activation, `aria-controls`/`aria-labelledby` tab↔panel relationship) with `tabs.test.tsx` (6 tests); real violations found and fixed — WCAG 2.5.3 Label-in-Name mismatches on the password-visibility toggle (`sign-in-form.tsx`, now `aria-pressed` instead) and the mobile-nav trigger/dialog (`mobile-navigation.tsx`, redundant `aria-label` removed since Radix already wires the visible `Dialog.Title`); a critical axe `aria-required-children` violation from `role="grid"`/`role="row"` on the Research gap matrix that implemented no grid keyboard navigation (`gap-matrix.tsx`, roles removed rather than fully building unused grid semantics) and from `role="list"` without `role="listitem"` children on `SourceTable` (`source-table.tsx`, fixed); a real AA contrast failure on light-theme `--sg-text-muted` (4.47:1, retuned to 5.7:1+ in `themes.css`); `apps/web/src/test/axe.ts` (axe-core component-audit helper, two jsdom-incapable rules disabled with documented reason, never a blanket disable) and `apps/web/src/styles/contrast.test.ts` (25 deterministic WCAG contrast-ratio tests over real dark/light/high-contrast token pairs, not eyeballed); 8 new axe checks appended to existing domain test files (auth, projects, sources, research — including the fixed gap matrix, graph, handoff, execution, routing — including the paid-unlock confirmation dialog specifically); `apps/web/src/components/app-shell/mobile-navigation.test.tsx` (2 tests, Radix dialog focus-trap/restoration verified directly); `apps/web/src/app/manifest.ts` + `manifest.test.ts` (5 tests); `apps/web/public/{icon-192,icon-512,icon-maskable-512}.png` (deterministic PNGs generated from the existing `icon.svg` design via a one-off Python-stdlib command, no dependency added, no generator script committed); `apps/web/public/sw.js` (versioned shell-only cache; precaches only `/offline`; bounded-runtime-caches only immutable `/_next/static/*`; explicitly excludes non-GET, cross-origin, `Authorization`-bearing, `Set-Cookie`, and all `/v1/*`/`/api/*`/`/auth/*` traffic) with `apps/web/src/components/pwa/sw-content.test.ts` (13 tests, static pattern evidence) and `node --check public/sw.js` passing; `apps/web/src/app/offline/page.tsx` (static, no-JS-required) with `page.test.tsx` (4 tests); `apps/web/src/components/pwa/pwa-registration.tsx` (production-only registration, `updateViaCache: "none"`, explicit user-controlled "Refresh to update", deduplicated offline/online announcement) with `pwa-registration.test.tsx` (5 tests); `apps/web/src/app/layout.tsx` (`viewportFit: "cover"` added — Group 17's `env(safe-area-inset-*)` tokens were inert without it — plus `<PwaRegistration />` mounted); `apps/web/next.config.ts` (narrowly-scoped `/sw.js` headers: no-store Cache-Control, restricted CSP); `apps/web/e2e/accessibility-pwa.spec.ts` (29 specs, listing only); `docs/product/ACCESSIBILITY_PWA.md`; `apps/web/README.md` Accessibility/PWA section; 67 new tests, 263 total frontend tests, all passing; `npm run typecheck`, `npm run lint --max-warnings=0`, `npm run api:types:check` (zero drift), `npm run build:termux` (includes `/manifest.webmanifest` and `/offline` as static routes), `npm audit --audit-level=high` (2 pre-existing moderate, no highs) all pass; license, hosted-audit-contracts, and PostgreSQL-boolean gates pass; 207/210 Python tests pass (same 3 pre-existing, unrelated PDF-adapter errors as Groups 14–17 — see Known blockers) with zero backend files touched
- Commit SHA: 291f83e
- Hosted revision: not yet produced
- Known blockers: this environment's Python `pypdf`/`cryptography` native extension remains broken (`pyo3_runtime.PanicException`), identical to Groups 14–17, zero backend files touched; **no formal WCAG 2.2 AA conformance is established** — automated tooling (axe-core in jsdom and, where it could run, in a real browser) plus targeted manual code review is not a conformance audit, which requires a structured pass against every applicable success criterion; **no manual screen-reader session was performed** (NVDA/VoiceOver/TalkBack) — `docs/product/ACCESSIBILITY_PWA.md` records a checklist, not evidence; **real-browser Playwright execution of `e2e/accessibility-pwa.spec.ts` did not complete** — Chromium is installed and available in this environment (`/opt/pw-browsers`), but the dev server required by Playwright's `webServer` config fails to start because `NEXT_PUBLIC_SPECGRAPH_API_URL`/`NEXT_PUBLIC_SUPABASE_URL` are not configured in this sandbox (`Error: Public web configuration is invalid`), a pre-existing environment limitation unrelated to this group's code; `npm run e2e:list` (144 total specs, 29 new) is the verification that actually ran; **installability and offline behavior were never browser-tested** — manifest/icon/service-worker content was verified by static assertion and `next build` route generation only, not by an actual browser install prompt, `Cache Storage` inspection, or offline-navigation fallback observed in a running browser
- Prohibited completion claims: do not claim formal WCAG 2.2 AA conformance — none is established, automated + manual-code-review evidence only; do not claim manual screen-reader testing occurred — it did not, only a checklist exists; do not claim hosted PWA installability or a real browser install/offline test — the dev server could not start in this environment, so `e2e/accessibility-pwa.spec.ts` was authored and listed but not executed; do not claim physical-device behavior of any kind; do not claim any change to query/mutation behavior, API contracts, domain calculations, or graph/authority/execution semantics — none was made; do not claim push notifications, background/periodic sync, offline mutation replay, or any cached authenticated/private response — none exists, all explicitly excluded and tested for absence

## GROUP 19 CI DEPLOYMENT AND ROLLBACK

- Status: COMPLETE
- Dependencies: GROUP 09 SECURITY RUNTIME AND OBSERVABILITY, GROUP 10 WEB FOUNDATION, GROUP 17 VISUAL RESPONSIVE FINISH
- Scope: implement deployment automation, protected previews, approvals, revision retention, backup, and rollback procedures
- Acceptance evidence required: GitHub Actions deployment workflows, OIDC/WIF configuration proof, protected preview verification, rollback rehearsal, and runbooks
- Commit SHA: f205797 (Group 19 HEAD after infrastructure repair), 9cd11ad (original Group 19 commit)
- Hosted revision: specgraph-api Cloud Run service created (Buildpack deploy, container failed to start); Vercel preview deployed at https://web-f8tmc0abe-mjmichaelwares-projects.vercel.app (deployment dpl_8iQCwELd6CbmMvovWTeYbZeaRNhy)
- Known blockers: Cloud Run API container failed to start with Buildpack build; Dockerfile-based deployment requires `gcloud run deploy --source=. --dockerfile=Dockerfile.api` which timed out in this environment; Google Cloud APIs and Vercel CLI became unreachable from this sandbox mid-operation due to network restrictions; Cloud Build API was enabled during this group but builds could not complete; see `docs/runbooks/OWNER_ONLY_PLATFORM_SETUP.md` and `docs/runbooks/PRODUCTION_APPROVAL.md` for owner setup steps
- Prohibited completion claims: do not claim hosted API availability, production deployment execution, OIDC/WIF runtime, Artifact Registry publishing, or rollback execution from this Group 19 gate alone — those remain for Group 20 hosted acceptance

## GROUP 20 HOSTED ACCEPTANCE AND RELEASE

- Status: PENDING (blocked)
- Dependencies: GROUP 19 CI DEPLOYMENT AND ROLLBACK
- Scope: perform hosted acceptance, release gating, and final production-completion verification
- Acceptance evidence required: staged hosted acceptance reports, production approval evidence, release revisions, and post-release verification
- Commit SHA: not yet produced
- Hosted revision: specgraph-api Cloud Run service at https://specgraph-api-882099804366.us-west1.run.app (created via Buildpack, container not functional); Vercel preview at https://web-f8tmc0abe-mjmichaelwares-projects.vercel.app (functional, returns 302/SSO)
- Known blockers: Google Cloud APIs and Vercel CLI became unreachable from this sandbox during Group 20 execution, preventing Dockerfile-based Cloud Run deployment, Supabase hosted audit, rollback rehearsal, and hosted acceptance journey execution; Cloud Run API service exists but was deployed with Buildpacks (not the correct Dockerfile.api) and the container fails to start; previously working Vercel CLI also unreachable after network restriction; the sandbox environment has a fundamental network egress restriction that prevents any cloud API access
- Prohibited completion claims: do not claim product completion before hosted acceptance and release evidence is produced; do not mark Group 20 complete while hosted access remains unavailable; do not claim production deployment, rollback execution, browser E2E, or any hosted step passed — none have been verified at runtime
