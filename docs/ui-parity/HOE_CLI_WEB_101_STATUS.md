# ATROPOS HOE — CLI + Web Presentation Status

**Authority:** Source Document 4 (Human Operating Environment) v1.0, 2026-07-27, plus the HOE 100% Completeness Handoff v2.
**Scope:** CLI/TUI and ATROPOS Web. Android is deferred by operator instruction.
**Register:** `docs/ui-parity/phase0/HOE_DELTA_REGISTER.tsv` — 37 rows, all terminal: 26 DONE, 9 MISSING, 2 DEFERRED. No PARTIAL rows remain.
**Fingerprints:** `docs/ui-parity/phase0/UI_PATH_FINGERPRINTS.sha256`

**Proof at time of writing:** `next build --webpack` compiles (33 routes) · 304/304 vitest · `tsc --noEmit` clean · Kotlin `gradle test` 257 tests, 5 pre-existing failures unrelated to presentation (AST, provider activation ×2, fixture matrix, redaction surface — all reproduce on a clean tree).

---

## §15 acceptance gates

The specification numbers these 11–22; they are listed here in that order.

| # | Gate | CLI | Web | Evidence |
|---|------|-----|-----|----------|
| 11 | Six continuous answers without search | **DONE** | **DONE** | `HomeStateProviderTest` (6); `SixAnswersPanel` on Home/Projects/Work/Agents/Files/Conversations/Models/Automation/History/Settings |
| 12 | Project model owns conversations, files, tasks, artifacts, evidence, history | **MISSING** (CLI-008) | **MISSING** (WEB-016) | No project store exists on either surface. Neither fabricates one. |
| 13 | Web OpenCode-class session/tab parity, local-first | n/a | **DONE** | Session tabs, persisted workspace state, command palette, local bridge to the JAR |
| 14 | Android APK Claude-density under HIG | n/a | n/a | **DEFERRED** (AND-001) — out of scope by instruction |
| 15 | CLI keyboard-first on narrow terminals | **DONE** | n/a | `DashboardRendererWidthTest` — no overflow at 40/80/120/160 columns |
| 16 | Failures visible with reason, evidence, repair, retry | **DONE** | **DONE** | `ErrorRenderer`; `FailureVisibility` via `EngineStatusBanner` — engine offline states reason and remedy |
| 17 | Approvals never erase history | n/a | **MISSING** (WEB-015) | No approve/reject API and no approval record store. Two components that would have implied a history were deleted rather than fed invented records. |
| 18 | Restart restores workspace and reports what was recovered | **DONE** | **DONE** | `RecoveryRibbon` reports the engine's own `continuity:` line; `recovery.test.ts` (4) |
| 19 | Developer Tools contain inspectors, no navigation pollution | n/a | **DONE** | `/developer` with `AllInspectors`; hidden until the Settings preference opts in |
| 20 | Accessibility: keyboard, non-colour status, reduced motion, high contrast, labels | **DONE** | **DONE** | `Surface.runState` emits colour + glyph + label; `prefers-reduced-motion` and high-contrast theme; skip link and landmarks; `contrast.test.ts` |
| 21 | Delta register re-auditable without archaeology | **DONE** | **DONE** | `HOE_DELTA_REGISTER.tsv` + `UI_PATH_FINGERPRINTS.sha256` |
| 22 | No SpecGraph-primary navigation | n/a | **DONE** | SpecGraph serves only from `/developer/specgraph/**`; old paths redirect |

**Score: 9 gates DONE, 2 MISSING (both blocked on an absent project store / approvals API), 1 DEFERRED (Android).**

---

## HOE-0001 … HOE-0020

| ID | Requirement | Status | Note |
|----|-------------|--------|------|
| HOE-0001 | Six answers on Home and Project views | DONE | CLI cockpit reads the durable agent queue; web panel reads `six_answers` |
| HOE-0002 | Navigation spine | DONE | CLI commands; web sidebar, header, mobile sheet and palette all derive from `navigationSpine` |
| HOE-0003 | Project as durable boundary | MISSING | No project store (CLI-008 / WEB-016) |
| HOE-0004 | Status vocabulary, colour-independent | DONE | `RunState` on CLI; `StatusBadge` canonical form on web |
| HOE-0005 | Completion claims afford evidence | DONE | `WorkItemCard` marks a completion carrying no evidence as unverifiable |
| HOE-0006 | Why / How / Evidence actions | DONE | All three mounted; How reads "not provided" (WEB-020) |
| HOE-0007 | Command palette reaches every primary action | DONE | Derived from the spine, so it cannot drift from navigation |
| HOE-0008 | Progressive disclosure 1–4, nothing removed | DONE | Persisted level consumed by `WorkItemCard`; 5 tests pin the additive rule |
| HOE-0009 | Workspace layout persists across restart | DONE | `SessionStateProvider` (WEB-005) |
| HOE-0010 | Recovery reports restored work | DONE | `RecoveryRibbon` (WEB-012) |
| HOE-0011 | Terminals first-class, linkable to evidence | MISSING | Web has no terminal surface; the bridge exposes no PTY and will not without an approval flow |
| HOE-0012 | Notifications actionable and categorised | DONE | `NotificationDisplay` mounted in the provider tree |
| HOE-0013 | Trust indicators per project | DONE | Unknown renders as unknown; nothing claimed without a probe |
| HOE-0014 | Developer Tools hidden by default, inspectors complete | DONE | Preference-gated; inspectors present |
| HOE-0015 | Web session model matches OpenCode patterns | DONE | Tabs, persistence, palette |
| HOE-0016 | Android interaction density | DEFERRED | Out of scope |
| HOE-0017 | Secrets never rendered in ordinary views | DONE | CLI redacts queue task text before display; test pins it |
| HOE-0018 | Delta register maintained | DONE | This batch |
| HOE-0019 | Accessibility gates enforced | DONE | See gate 20 |
| HOE-0020 | SpecGraph stays Developer Tools | DONE | `/developer/specgraph` only |

---

## What is MISSING, and why each is closed without fabrication

Nine rows are permanently MISSING. Every one is blocked on a producer that does not exist in the engine. None is closed by inventing data.

1. **CLI-008 — project model on CLI Home.** No CLI project store. The cockpit renders no project section.
2. **CLI-009 — corrupt queue entry reporting.** `AgentQueueStore.listEntries` swallows per-entry IO errors. An unreadable queue *directory* is distinguished (CLI-006); per-entry faults need a core change to the store's error contract.
3. **WEB-015 — approvals history.** No approve/reject API. Components implying an approval history were deleted.
4. **WEB-016 — project store.** No backing store; project surfaces render engine-offline rather than sample projects.
5. **WEB-020 — How? pipeline.** No pipeline field on any engine entity. The control is wired, visible and tested in its "not provided" state.
6. **WEB-022 — project creation.** `/projects/new` belonged to SpecGraph and moved with it. The surface states creation is unavailable rather than linking to a subsystem form.
7. **WEB-024 — multi-view project (§3.1).** Conversation, Timeline and Execution Monitor views had no producer; deleted rather than fed invented records.
8. **WEB-027 — unimplemented bridge endpoints.** `/session`, `/message`, `/events`, `/approve`, `/reject`, `/evidence/:id`, `/files`.
9. **HOE-0011 — terminals on web.** No PTY over the bridge; exposing one is the RCE risk the allowlist exists to prevent.

**Deferred (2):** AND-001 Android; WEB-025 unused non-HOE primitives (`spinner`, `toast`) — dead code that claims no gate.

---

## Bridge surface (exact)

Implemented, all read-only:

- `GET /api/atropos/status` — engine reachability, jar path, workspace
- `GET /api/atropos/recovery` — the engine's startup `continuity:` line
- `POST /api/atropos/command` — one allowlisted command

Allowlist: `/home`, `/dashboard`, `/status`, `/status endpoints`, `/status quota`, `/providers`, `/tabs`, `/agent status`, `/agent queue list`, `/help`.

Unrestricted argv passthrough is **deliberately not implemented**. The CLI reaches `/shell`, `!command` and `/cd`; an open passthrough on a localhost port is remote code execution against the operator's machine from any page in their browser. Widening the allowlist belongs with an approval flow, not a convenience edit.

---

## False greens removed during this work

Recorded so a future audit does not reintroduce them:

- CLI six answers were six hardcoded strings, and the renderer was unreachable from any command.
- Web `(app)` route group had never compiled: 76 TypeScript errors, a missing `lucide-react` dependency, and two route groups claiming `/projects` with different slug names.
- Nine pages advertised evidence links to logs that do not exist.
- `work/page.tsx` asserted `authorityVerified` / `policyCompliant` / `checkpointCurrent` as literal `true`; `TrustIndicators` silently dropped unknowns.
- The Settings "Show Developer Tools" checkbox was an uncontrolled input wired to nothing; "Enable Debug Logging" had no facility behind it.
- Developer Tools carried four `href="#"` placeholders, including SpecGraph as a documentation stub.
- The `InformationLevels` picker described four disclosure levels and drove nothing.
- Every `(app)` route was served **unauthenticated**: the auth guard existed only on the `(application)` layout.
- `COMMON_SHORTCUTS` entries were typed as actions while carrying no handler and would have thrown if used.

---

*Generated at the close of the CLI + Web presentation engagement. Android remains deferred.*
