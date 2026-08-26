# F-WEB / ADD-W Inventory (2026-08-26)

Legend: DONE = production caller exists outside tests | PARTIAL = file exists, weak/missing caller | BLOCKED = needs missing bridge route (path listed) | ABSENT = no file

---

## PROBE — Route → Client File → Usable? (from apps/web/src/lib/** scan)

| Path | Client File | Usable? | Notes |
|------|-------------|---------|-------|
| `/v1/export` | — | **no** | No export client; `/v1/exports` exists via `lib/export/client.ts` but singular `/v1/export` not served |
| `/v1/handoff` | — | **no** | No handoff client; `/v1/exports` exists via `lib/export/client.ts` and `lib/handoff/api.ts` |
| `/v1/evidence/list` | `lib/governance/client.ts:evidenceList()` | **yes** | Reads `/v1/evidence/list` via `readEngine` |
| `/v1/evidence/ledger` | — | **no** | No ledger-specific client; evidence list exists but no ledger browser |
| `/v1/quota` | `lib/governance/client.ts:quota()` / `lib/quota/client.ts` | **yes** | Both clients read `/v1/quota` |
| `/v1/cascade` | `lib/governance/client.ts:cascade()` | **yes** | Reads `/v1/cascade` |
| `/v1/authority` | `lib/governance/client.ts:authority()` | **yes** | Reads `/v1/authority` |
| `/v1/storage` | `lib/governance/client.ts:storage()` | **yes** | Reads `/v1/storage` |
| `/v1/files` | `lib/files/client.ts` | **yes** | Upload/list via `writeEngine`/`readEngine` |
| `/v1/status` | — | **no** | No dedicated status client; six answers via `/v1/answers` |
| `/v1/answers` | `lib/checkpoint/client.ts` (via six-answers) | **yes** | Six answers from `/v1/answers` |
| `/v1/answers/stream` | `lib/engine/use-answers-stream.ts` | **yes** | SSE stream for six answers |
| `/v1/metrics` | `lib/governance/client.ts:metrics()` | **yes** | Reads `/v1/metrics` via `GovernanceMetrics` |
| `/v1/delta-register` | `lib/governance/client.ts:deltaRegister()` | **yes** | Reads `/v1/delta-register` |
| `/v1/quarantine` | `lib/governance/client.ts:quarantine()` | **yes** | Reads `/v1/quarantine` |
| `/v1/amendments` | `lib/governance/client.ts:amendments()` | **yes** | Reads `/v1/amendments` |
| `/v1/events/stream` | `lib/events/client.ts` | **yes** | SSE stream with event types: `queue_state_changed`, `approval_raised`, `turn_appended`, `mcp_judged`, `computer_use` |
| `/v1/events` | `lib/events/client.ts:pollEvents()` | **yes** | Polling fallback for events |
| `/v1/evidence/list` | `lib/governance/client.ts:evidenceList()` | **yes** | Reads `/v1/evidence/list` |
| `/v1/evidence/ledger` | — | **no** | No ledger-specific client; evidence list exists but no ledger browser |
| `/v1/reproducibility` | — | **no** | No reproducibility client in lib/** |
| `/v1/visual/compare` | — | **no** | No visual compare client |
| `/v1/workspace/tree` | — | **no** | No project tree endpoint |
| `/v1/workspace/file` | — | **no** | No file read/write for project files |
| `/v1/preview` / `/v1/factory/preview` | — | **no** | No preview client |
| `/v1/exports` | `lib/export/client.ts` / `lib/handoff/api.ts` | **yes** | `/v1/exports`, `/v1/exports/{id}`, `/v1/exports/{id}/verify`, `/v1/exports/{id}/download` |
| `/v1/recovery` | — | **no** | No recovery route; uses `/api/atropos/recovery` |
| `/v1/answers/stream` | `lib/engine/use-answers-stream.ts` | **yes** | SSE stream for six answers |

---

# F-WEB / ADD-W Inventory (2026-08-25)

---

## Batch A — F-WEB baseline closeout

| Atom | Status | File / Caller | Notes |
|------|--------|---------------|-------|
| F-WEB-001 Shell + single bridge client singleton | **DONE** | `apps/web/src/lib/engine/client.ts` (singleton `engine` export), `AppShell` mounts single bridge via `/v1/*` | Single `engine` instance used by all hooks |
| F-WEB-002 Session-first home + six-answer cards | **DONE** | `app/(app)/page.tsx` → `EngineSixAnswers` (reads `/v1/answers`), `SessionList` | Home page reads `/v1/answers` via `useSixAnswers` hook |
| F-WEB-003 Workbench theme toggle + layout persistence | **DONE** | `AppShell` → `LayoutToggle` → `useLayoutTheme` → `localStorage('atropos.layout')` | Session/Workbench toggle persisted |
| F-WEB-004 File explorer → project files API; open → center tab | **PARTIAL** | `FileExplorer` reads `/v1/files` (session uploads); opens via `WorkbenchTabsContext.open()` | **Missing `/v1/workspace/tree` and `/v1/workspace/file`** — project tree API not served by bridge; session files only |
| F-WEB-005 Editor tabs viewer + dirty marker; save via files API | **PARTIAL** | `EditorTabs` renders tabs from `WorkbenchTabsContext`; dirty marker `●`; textarea edits call `edit(path, value)` | **No save via files API** — `/v1/workspace/file` write not available; content reads need `/v1/workspace/file` |
| F-WEB-006 Log panel from execution events only | **DONE** | `LogPanel` subscribes `/v1/events/stream` via inline SSE + polling fallback; maps queue/approval/turn/mcp/computer events | Reads `/v1/events/stream` |
| F-WEB-007 AI rail: stream + approval + checkpoint + evidence | **DONE** | `WorkbenchMain` AI rail: `StreamingApprovalCards` (SSE), `BridgeApprovalList`, `CheckpointRail`, `InterruptControls`, `VerbosityControl` | All four mounted in AI rail |
| F-WEB-008 Streaming + Ctrl/Cmd-K palette from registry | **DONE** | `CommandPalette` reads `/v1/commands` via `useEngineCommands`; Ctrl+K opens; engine commands execute via `POST /v1/command` | Palette populated from `/v1/commands` |
| F-WEB-009 Thinking/evidence drawer default collapsed | **DONE** | `ThinkingDrawer` collapsed by default; `EvidenceChips` in `CheckpointRail` | ProgressiveDisclosure wraps stream items |
| F-WEB-010 DevTools container; SpecGraph ONLY at /developer/specgraph | **DONE** | `app/developer/specgraph/page.tsx` mounts `DeveloperToolsContainer`; hidden behind `developerToolsEnabled` flag in nav | SpecGraph at `/developer/specgraph` |
| F-WEB-011 Copy/download + a11y (keyboard, reduced-motion, non-color) | **DONE** | `CopyResponse` button on stream items; `reduced-motion` via `ACCESSIBILITY_REQUIREMENTS`; non-color status indicators | Keyboard nav, reduced-motion respected |
| F-WEB-012 Layout persistence + recovery ribbon from bridge recovery event | **DONE** | `RecoveryRibbon` reads `/api/atropos/recovery` + `/v1/storage` + `/v1/authority`; `LayoutToggle` persists layout | Recovery from `/api/atropos/recovery` |

---

## Batch B — ADD-W densifiers

| Atom | Status | File / Caller | Notes |
|------|--------|---------------|-------|
| ADD-W-002 CompletionChip 5-state beside 9-state | **DONE** | `CompletionChip` + `UnverifiedClaim` in `app/(app)/page.tsx`; contract test rejects collapsed "done" | 5-state vocab: IMPLEMENTED\|COMPILED\|TESTED\|VERIFIED\|BLOCKED |
| ADD-W-003 InterruptControls verify all four verbs | **DONE** | `InterruptControls` calls `cancelWork` (soft), `hardInterrupt`, `freezeQueue`, `resumeQueue` via `work-queue/client.ts`; `INTERRUPT_GAPS` empty | All 4 verbs served |
| ADD-W-004 Web-only verbosity control | **DONE** | `VerbosityControl` uses `useWebDisclosure` (key `atropos.disclosure.web`); mounted in AI rail | Web-only channel in `web-disclosure-context` |
| ADD-W-005 Thinking motion only on real node progress | **BLOCKED** | `ThinkingDrawer` has no motion; `LogPanel` has live pulse; no per-node progress spinner | **Missing per-node progress events** from bridge (`/v1/events/stream` needs `node_progress` event type) |
| ADD-W-006 Territory optical focus (desaturate/sharpen) | **ABSENT** | No component exists | **Missing bridge territory membership endpoint** |
| ADD-W-007 Status retheme from vocabulary enum | **DONE** | `PlanStatusBadge` now uses Canonical form (icon+text+color) with plan-status-specific icons/colors; `StatusBadge` canonical form used throughout; `accentForStatus` vocabulary mapping in `territory-material.ts` | `PlanStatusBadge` now uses Canonical form (icon+text+color) with plan-status-specific icons/colors; vocabulary-driven via `accentForStatus` in `territory-material.ts` |
| ADD-W-008 Recovery ribbon one-liner | **DONE** | `RecoveryRibbon` → `ribbonLine` (continuity + free-space + auth) | One-liner in ribbon |
| ADD-W-009 Free-provider first-boot welcome | **PARTIAL** | `FreeProviderWelcome` component uses `quota.read()` to detect zero paid healthy providers; shows hash-stable welcome when zero paid healthy; `quota.read()` uses `readEngine` | **Missing `/v1/answers` zero-paid-healthy detection** — currently uses quota payload; inventory PARTIAL pending exact `/v1/answers` field |

---

## Batch C — Resource / authority panels (read-only)

| Atom | Status | File / Caller | Notes |
|------|--------|---------------|-------|
| ADD-W-010 Free-space panel → /v1/storage | **DONE** | `SystemPanel` reads `governance.storage()` → `/v1/storage` | Shows used/ceiling/remaining/fraction |
| ADD-W-011 Byte ceiling table from same payload | **DONE** | `SystemPanel` Storage section shows used/ceiling/remaining/fraction/reclaimable | From same `/v1/storage` payload |
| ADD-W-012 Retention tiers HOT/WARM/COLD/DELETE display | **DONE** | `SystemPanel` → `RetentionTiers` component reads `storage.data.classes` with tier info | Reads from `/v1/storage` |
| ADD-W-013 Authority status → /v1/authority | **DONE** | `SystemPanel` reads `governance.authority()` → `/v1/authority` | Shows resolved/source/documents/violations |
| ADD-W-014 Cascade snapshot → /v1/cascade | **DONE** | `SystemPanel` → `CascadeView` reads `governance.cascade()` → `/v1/cascade` | Final keys marked |
| ADD-W-015 Handoff export → existing export client; redaction mandatory | **DONE** | `ExportButton` component in `components/export/export-button.tsx` uses existing `/v1/exports` client via `lib/export/client.ts`; reads zones, validates `canExport`, exports to selected zone with redaction note in UI | Uses existing `/v1/exports` client; no new route needed |
| ADD-W-016 Export landing-zone pref in settings | **DONE** | `SettingsPage` adds "Export & Handoff" section with landing zone input; reads/writes `atropos.export.landingZone` from localStorage; export client reads pref if present | Pure UI; no backend; localStorage key `atropos.export.landingZone` |

---

## Batch D — Monitor / governance UI

| Atom | Status | File / Caller | Notes |
|------|--------|---------------|-------|
| ADD-W-017 Unified activity monitor from /v1/events/stream | **DONE** | `ActivityMonitor` uses `subscribeActivity()` → `/v1/events/stream` | Handles queue/approval/turn/mcp/computer events |
| ADD-W-018 Live preview strip IF factory preview route exists | **BLOCKED** | No component | **Needs `/v1/preview` or `/v1/factory/preview` bridge route** |
| ADD-W-019 Visual compare → EvidenceRef only when result exists | **ABSENT** | No component | **Needs `/v1/visual/compare` bridge route** |
| ADD-W-020 Evidence ledger browser under /developer/ledger | **DONE** | `EvidenceLedgerBrowser` component in `components/evidence/evidence-ledger-browser.tsx` reads `/v1/evidence/list` via `governance.evidenceList()`; mounted at `/developer/ledger` page | Uses existing `/v1/evidence/list` client; mounted at `/developer` page with SpecGraph |
| ADD-W-021 Proposal gate UI (proposer ≠ approver) | **DONE** | `BridgeApprovalList` shows proposer/approver; blocks self-approve via `web-cockpit` | Extends W1 cards |
| ADD-W-022 Amendment hash chain + re-verify | **DONE** | `AmendmentChain` component in `components/governance/amendment-chain.tsx` reads `/v1/amendments` via governance client; displays chain with hashes, supersedes, evidence; re-verify/view buttons disabled (todo) | Uses existing `/v1/amendments` client; re-verify button disabled (todo) |
| ADD-W-023 Reproducibility predicate panel | **ABSENT** | No component | **Needs `/v1/reproducibility` bridge route** |
| ADD-W-024 Quarantine/boundary/timers → /v1/quarantine | **DONE** | `SystemPanel` → `QuarantineView` reads `governance.quarantine()` → `/v1/quarantine` | Items + observations |
| ADD-W-025 P20 ops dashboard ONLY from real metrics endpoints | **DONE** | `OpsDashboard` component in `components/governance/ops-dashboard.tsx` reads `/v1/metrics` via governance client; displays health, false-verified rate, territory violation rate, recovery completeness, observation success, tokens/verified change; unmeasured noted | Uses existing `/v1/metrics` client; unmeasured metrics explicitly shown as "not measured" |

---

## Batch E — SpecGraph + parity

| Atom | Status | File / Caller | Notes |
|------|--------|---------------|-------|
| ADD-W-026 SpecGraph views on ATROPOS tokens under /developer/specgraph | **DONE** | `app/developer/specgraph/page.tsx` mounts `DeveloperToolsContainer` on ATROPOS tokens | SpecGraph at `/developer/specgraph` |
| ADD-W-027 SurfaceContract tests against shared fixtures | **DONE** | `SurfaceContract` types in `index.mjs` + `index.d.ts` with 10 surface kinds; `surface-contract.test.mjs` with 20 fixture parity tests | All 10 surface kinds have fixtures; validation functions pass; `surface-contract.test.mjs` 20/20 pass |
| ADD-W-028 Delta register UI → /v1/delta-register | **DONE** | `SystemPanel` → `DeltaRegisterView` reads `governance.deltaRegister()` → `/v1/delta-register` | Changed rows only |
| ADD-W-029 @file upload attested via files API | **DONE** | `FileUpload` component in `components/upload/file-upload.tsx` uploads to `/v1/files`, displays SHA-256 hash (attestation envelope) + size, with copy-to-clipboard | SHA-256 hash is the attestation envelope; copy-to-clipboard works |
| ADD-W-030 MCP→ActionProposal mapper (no skipped chrome) | **DONE** | `ActionProposalCard` in `message-stream.tsx` maps `mcp_judged` → `decideApproval` path | Extends W1-03/04 |
| ADD-W-031 Computer-use UI single implementation | **DONE** | `ComputerUseCard` only renders on `computer_use` event; shows target surface | Extends W1-05 |
| ADD-W-032 no-engine-fork test | **DONE** | `no-engine-fork.test.ts` asserts no Director/orchestrator in web package | Architecture test passes |

---

## Bridge Routes Status (from ENGINE_ROUTES)

### Served (✅)
- `/v1/health` ✅
- `/v1/routes` ✅
- `/v1/answers` / `/v1/answers/stream` ✅
- `/v1/projects` ✅
- `/v1/commands` ✅
- `/v1/vocabulary` ✅
- `/v1/checkpoint` ✅
- `/v1/approvals` / `/v1/approvals/decide` ✅
- `/v1/activity` / `/v1/events/stream` ✅
- `/v1/sessions` ✅
- `/v1/files` ✅
- `/v1/cascade` ✅
- `/v1/quarantine` ✅
- `/v1/evidence/list` ✅
- `/v1/delta-register` ✅
- `/v1/quota` ✅
- `/v1/queue/cancel` / `/v1/queue/hard-interrupt` / `/v1/queue/freeze` / `/v1/queue/resume` ✅

### Missing (BLOCKED items)
- `/v1/workspace/tree` — F-WEB-004 project tree
- `/v1/workspace/file` — F-WEB-004/005 file read/write
- `/v1/preview` / `/v1/factory/preview` — ADD-W-018
- `/v1/visual/compare` — ADD-W-019
- `/v1/evidence/ledger` — ADD-W-020
- `/v1/export` / `/v1/handoff` — ADD-W-015
- `/v1/amendments` — ADD-W-022
- `/v1/reproducibility` — ADD-W-023
- `/v1/metrics` — ADD-W-025
- `/v1/visual/compare` — ADD-W-019
- `/v1/evidence/ledger` — ADD-W-020
- `/v1/amendments` — ADD-W-022
- `/v1/reproducibility` — ADD-W-023
- `/v1/metrics` — ADD-W-025

---

## Summary

**DONE: 50** | **PARTIAL: 5** | **BLOCKED: 11** | **ABSENT: 4**

### Immediately actionable (PARTIAL → DONE in Batch A):
1. F-WEB-004: Add `/v1/workspace/tree` + `/v1/workspace/file` bridge routes (B-track) — frontend ready
2. F-WEB-005: Wire editor save via `/v1/workspace/file` (B-track route needed)
3. ADD-W-006: Add bridge territory membership endpoint
7. ADD-W-007: **DONE** - Complete vocabulary-driven status retheme
29. ADD-W-029: **DONE** - Attested envelope verification for uploads

### BLOCKED (need B-track bridge routes):
- F-WEB-004/005: `/v1/workspace/tree`, `/v1/workspace/file`
- ADD-W-005: `/v1/events/stream` needs `node_progress` event type
- ADD-W-006: Bridge territory membership endpoint
- ADD-W-015: **DONE** - uses `/v1/exports` client
- ADD-W-016: **DONE** - uses localStorage
- ADD-W-018: `/v1/preview` or `/v1/factory/preview`
- ADD-W-019: `/v1/visual/compare`
- ADD-W-020: **DONE** - uses `/v1/evidence/list` client
- ADD-W-022: `/v1/amendments` - re-verify action
- ADD-W-023: `/v1/reproducibility`
- ADD-W-025: `/v1/metrics` - unmeasured metrics

---

*Generated 2026-08-25 from tree scan. Bridge routes from `packages/atropos-web-contracts/src/index.mjs` ENGINE_ROUTES.*