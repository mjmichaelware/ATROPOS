# Handoff, Execution, and Routing (Group 16)

Concise product/contract boundary document for the Handoff, Execution-Run-Detail, and Routing routes. It records the contract constraints that shaped the implementation and the explicit Group 16 / Group 17 boundary. Extend this document in later groups rather than re-deriving it; the full graph interaction/visual design system lives in `docs/product/GRAPH_STUDIO_FOUNDATION.md` and is reused, not duplicated, by these routes.

## Scope

Three routes, all built on the existing Graph Foundation HUD shell:

- `/projects/{projectId}/handoff` — bounded workspace overview, integration bindings, export generation/verification/download, execution-run start.
- `/projects/{projectId}/executions/{runId}` — real execution-run detail: timeline, stage/node table, attempts, receipts, findings, independent verification.
- `/projects/{projectId}/routing` — routing policy, providers, renderers, paid unlocks, route decisions.

## Contract boundaries this group had to honor

- **No `plans` list on the handoff workspace.** `GET /v1/projects/{projectId}/handoff-workspace` never returns a plans array. Rather than fabricate a plan picker, export generation and execution-run start require the operator to paste a real plan ID, with UI copy pointing back to Graph → Plans as the source of that ID.
- **Idempotency-Key vs. If-Match are two different questions.** Every retry-sensitive create/update mutation (bindings, providers, renderers, exports, execution-run start, verification, paid unlocks, route decisions) sends a fresh `Idempotency-Key`. Only mutations that edit an already-loaded record (bindings, providers, renderers, routing policy) additionally send `If-Match` with the ETag captured from the prior read — never fabricated, never reused across a different record.
- **A stale write is a conflict, not a silent overwrite.** A 412 or 428 response is shown as a concise conflict state with a Reload action. The form's in-progress values are preserved; nothing is optimistically merged into server state.
- **Receipts expose raw evidence server-side; the client must not re-expose it.** `execution_receipts.evidence` is not redacted by the backend. `src/lib/execution/receipts.ts` strips it before any receipt is rendered, keeping only `evidence_sha256` and a top-level field count.
- **Claim/heartbeat/receipt-submission are not human actions.** Their request schemas require a `worker_id`, which the browser has no legitimate way to supply. No UI form or button exists for them; only the resulting node/attempt/receipt state (already server-normalized) is shown, read-only.
- **No numeric cost or currency field exists anywhere in the routing/provider/paid-unlock contract.** Cost is represented honestly via each provider's real `cost_class` string. An unknown or empty cost class blocks the paid-unlock confirmation action outright rather than presenting a fabricated price.
- **Route decisions and paid unlocks have no list/GET-all endpoint.** The UI shows the just-created record inline, plus — for route decisions only, since a real `GET /v1/route-decisions/{decisionId}` exists — a manual lookup-by-ID form. No client-side history is invented for either.
- **Signed download URLs are ephemeral by design.** They are requested only on explicit user action, opened only via a validated (HTTPS, same Supabase origin, no embedded credentials) click, and never written to URL state, `localStorage`, `sessionStorage`, logs, toasts, or any cache. Requesting again always calls the download endpoint fresh.

## Paid-routing confirmation law

Paid unlocks are never automatic and never optimistic. The flow is strictly: select a real provider → see its real cost class and any real risk warning → Review (disabled until cost class is known and required fields are filled) → explicit Confirm step naming the provider and cost class → request sent only on that second, deliberate action. A failure at any step leaves the confirmation dialog open with the server's real error, never a fabricated success.

## Group 16 vs. Group 17 boundary

Group 16 ships the three routes above with full state coverage (loading/empty/unavailable/offline/stale/partial/forbidden-not-found/operation-running/operation-failed/conflict/rate-limit/dependency-unavailable), keyboard operation, and reuse of every existing HUD primitive and CSS class from Groups 14/15 — no new visual system, no new CSS rules were needed.

Group 16 does **not** ship the Group 17 visual/responsive polish pass. Nothing in this group should be read as a finished visual treatment of these three routes — only as functionally complete, contract-correct implementations of them.

## Unverified browser/device requirements

Not claimed or measured in Group 16: hosted browser execution of any mutation on these routes, physical-device behavior, a real signed-URL download actually completing against live Supabase Storage, real worker claim/heartbeat/receipt-submission runtime behavior, or hosted network timing. Only local Vitest/Testing Library component and unit coverage and a Playwright listing (`e2e/handoff.spec.ts`, `e2e/execution.spec.ts`, `e2e/routing.spec.ts`) exist so far.
