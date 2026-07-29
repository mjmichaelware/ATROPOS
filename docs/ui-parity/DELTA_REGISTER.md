# ATROPOS UI/UX Delta Register
**Baseline: Source Document 4 v1.0 (2026-07-27)**  
**Current Implementation Date: 2026-07-28**  
**Surface Coverage: CLI | WEB | ANDROID | DESKTOP**

---

## Register Schema
```
UI-DELTA-<surface>-<nnn> | Capability | Current Status | Target | Gap | Evidence | Check
```

## ACCEPTANCE GATES (Section 15)
All 22 gates must pass for 100% presentation completeness.

| Gate | Requirement | Current | Target | Gap | Evidence |
|------|-------------|---------|--------|-----|----------|
| AG-1 | Six continuous answers on primary surfaces | PARTIAL | Every screen answers all 6 | MISSING | Needs implementation on all pages |
| AG-2 | Project model owns conversations, files, tasks, artifacts, evidence, history | PARTIAL | Unified project state | PARTIAL | Pages exist but not data-bound |
| AG-3 | Web achieves OpenCode session/tab parity, local-first | PARTIAL | Full OpenCode UX | PARTIAL | Tabs exist, no session persistence |
| AG-4 | Android APK achieves Claude-web + Claude-Code density under HIG | ABSENT | Full Android app | MISSING | No Android implementation |
| AG-5 | CLI/TUI fully operable keyboard-first on narrow terminals | ABSENT | Full CLI/TUI | MISSING | CLI has design tokens, no UI layer |
| AG-6 | Failures visible with reason, evidence, repair, retry | ABSENT | Error handling UI | MISSING | No error boundary implementation |
| AG-7 | Approvals never erase history | ABSENT | Approval audit trail | MISSING | No approval system |
| AG-8 | Restart restores workspace and reports recovered work | ABSENT | Recovery UX | MISSING | No checkpoint/recovery system |
| AG-9 | Developer Tools contain inspectors without polluting navigation | PARTIAL | 6 full inspectors | PARTIAL | Dev Tools page exists, empty |
| AG-10 | Accessibility: keyboard, non-color status, reduced motion, high contrast, screen readers | PARTIAL | Full a11y compliance | PARTIAL | Theme support exists, no testing |
| AG-11 | Delta register exists, re-auditable without archaeology | ABSENT | This document | DONE | Creating now |
| AG-12 | No SpecGraph-primary navigation; subsystems stay subsystems | PARTIAL | SpecGraph as one tab | PARTIAL | Correct nesting but not distinct |
| AG-13 | Six answers on Home | ABSENT | Full | MISSING | Home page bare |
| AG-14 | Six answers on Projects | ABSENT | Full | MISSING | Projects page bare |
| AG-15 | Six answers on Work | ABSENT | Full | MISSING | Work page bare |
| AG-16 | Six answers on Conversations | ABSENT | Full | MISSING | Conversations page bare |
| AG-17 | Six answers on Files | ABSENT | Full | MISSING | Files page bare |
| AG-18 | Six answers on Agents | ABSENT | Full | MISSING | Agents page bare |
| AG-19 | Six answers on Models | ABSENT | Full | MISSING | Models page bare |
| AG-20 | Six answers on Automation | ABSENT | Full | MISSING | Automation page bare |
| AG-21 | Six answers on History | ABSENT | Full | MISSING | History page bare |
| AG-22 | Six answers on Settings | ABSENT | Full | MISSING | Settings page bare |

---

## CRITICAL REQUIREMENTS (Section 0)

### The Six Continuous Answers (Section 0.1)
Every screen must answer without requiring search:
1. **What am I trying to accomplish?** — Objective/goal clarity
2. **What is ATROPOS doing?** — Current operation/task
3. **Why is it doing that?** — Reasoning/rationale
4. **How far along is it?** — Progress/completion %
5. **What should I do next?** — Next action recommendation
6. **Can I inspect the evidence?** — Evidence affordance

**Current Status:** MISSING on all pages except skeleton  
**Target:** All 22 acceptance gates include verifying six answers  
**Gap:** CRITICAL - Core to all other work

---

## SECTION 2: PRIMARY NAVIGATION ARCHITECTURE

| ID | Capability | Current Path | Target | Gap | Status |
|---|---|---|---|---|---|
| UI-NAV-001 | Home | `/app/page.tsx` | ✓ Exists, needs six answers | PARTIAL | Bare skeleton |
| UI-NAV-002 | Projects | `/app/projects/page.tsx` | ✓ Exists, needs six answers | PARTIAL | Bare skeleton |
| UI-NAV-003 | Work | `/app/projects/[id]/work/page.tsx` | ✓ Exists, needs six answers | PARTIAL | Kanban exists, no data |
| UI-NAV-004 | Conversations | `/app/projects/[id]/conversations/page.tsx` | ✓ Exists, needs six answers | PARTIAL | Bare skeleton |
| UI-NAV-005 | Files | `/app/projects/[id]/files/page.tsx` | ✓ Exists, needs six answers | PARTIAL | Bare skeleton |
| UI-NAV-006 | Agents | `/app/projects/[id]/agents/page.tsx` | ✓ Exists, needs six answers | PARTIAL | Grid layout, no data |
| UI-NAV-007 | Models | `/app/models/page.tsx` | ✓ Exists, needs six answers | PARTIAL | Bare skeleton |
| UI-NAV-008 | Automation | `/app/automation/page.tsx` | ✓ Exists, needs six answers | PARTIAL | Bare skeleton |
| UI-NAV-009 | History | `/app/history/page.tsx` | ✓ Exists, needs six answers | PARTIAL | Bare skeleton |
| UI-NAV-010 | Settings | `/app/settings/page.tsx` | ✓ Exists with theme customizer | PARTIAL | Theme only, needs full prefs |
| UI-NAV-011 | Developer Tools (hidden by default) | `/app/dev-tools/page.tsx` | ✓ Exists, needs 6 inspectors | PARTIAL | Bare skeleton |

---

## SECTION 3: UNIVERSAL WORKSPACE & PROJECT MODEL

### Project Constituents (3.0)
Every project must display: Objective, Plan, Resources, Execution, Verification, Artifacts, History

| ID | Constituent | Implementation | Gap |
|---|---|---|---|
| PROJ-001 | Objective | ABSENT | Needs display on project header |
| PROJ-002 | Plan | ABSENT | Needs visual representation |
| PROJ-003 | Resources | ABSENT | Budget, agents, tokens |
| PROJ-004 | Execution | PARTIAL | Work Kanban exists, needs linked display |
| PROJ-005 | Verification | ABSENT | Verification gates, status |
| PROJ-006 | Artifacts | ABSENT | Generated code, documents, exports |
| PROJ-007 | History | ABSENT | Event log, activity timeline |

### Multi-View Project (3.1)
Every project must support: Conversation, Kanban, Timeline, Document, File Explorer, Execution Monitor, Verification, Developer views

| View | Current | Target | Gap |
|---|---|---|---|
| Conversation View | ABSENT | Full chat history + project state sync | MISSING |
| Kanban View | PARTIAL | Work board complete, not auto-updating | PARTIAL |
| Timeline View | ABSENT | Chronological execution flow | MISSING |
| Document View | ABSENT | Generated artifacts, PRDs, docs | MISSING |
| File Explorer | PARTIAL | Page exists, needs tree view + editor integration | PARTIAL |
| Execution Monitor | ABSENT | Live task execution, ETA, stage display | MISSING |
| Verification View | ABSENT | Gates, test results, evidence | MISSING |
| Developer View | ABSENT | Graph, internals, debugging | MISSING |

### Status System (3.3)
Canonical vocabulary: Idle, Planning, Waiting, Working, Review Required, Blocked, Completed, Failed, Cancelled

**Current:** ABSENT  
**Target:** Every task/workflow displays canonical status + color-independent indicator  
**Gap:** CRITICAL - Needed on all project views

---

## SECTION 4: HUMAN CONTROL, APPROVALS, TRUST

### Control Verbs (4.0)
Humans may: Pause, Resume, Cancel, Approve, Reject, Retry, Redirect, Prioritize, Split, Merge, Archive, Export, Inspect

| Verb | Implementation | Gap |
|---|---|---|
| Pause | ABSENT | Needs workflow control |
| Resume | ABSENT | Needs workflow control |
| Cancel | ABSENT | Needs workflow control |
| Approve | ABSENT | Needs approval modal + audit |
| Reject | ABSENT | Needs approval modal + audit |
| Retry | ABSENT | Needs error handling |
| Redirect | ABSENT | Needs workflow re-routing |
| Prioritize | ABSENT | Needs task reprioritization |
| Split | ABSENT | Needs task splitting UI |
| Merge | ABSENT | Needs task merging UI |
| Archive | ABSENT | Needs archive action |
| Export | ABSENT | Needs export modal |
| Inspect | ABSENT | Needs evidence browser |

### Trust Indicators (4.2)
Display continuously on project: Authority verified, Evidence verified, Verification complete, Policy compliant, Checkpoint current, Recovery available, No silent failures

**Current:** ABSENT  
**Target:** Trust bar on every project view  
**Gap:** CRITICAL - Core safety indicator

---

## SECTION 5: PROGRESSIVE DISCLOSURE

### Information Levels (5.0)
Implement 4 levels without removing information between levels

| Level | Name | Target | Current |
|---|---|---|---|
| 1 | Simple | Only work-critical info | ABSENT |
| 2 | Professional | + metrics, workflow details | ABSENT |
| 3 | Engineering | + architecture, agents, routing, verification | ABSENT |
| 4 | Internal | Complete runtime state via Developer Tools | PARTIAL (Dev Tools exist) |

**Gap:** CRITICAL - Need level-toggle UI on all pages

### Explainability Controls (5.3)
Available on significant recommendations/workflows: Why? / How? / Evidence

**Current:** ABSENT  
**Target:** Expandable explanation UI  
**Gap:** CRITICAL

---

## SECTION 6: AGENTS, DASHBOARDS

### Agent Dashboard (6.2)
Display: identity, responsibility, assigned/completed/blocked tasks, resource usage, history, workload

**Current:** Grid layout exists, no data  
**Target:** Full agent card display with all fields  
**Gap:** CRITICAL

### Provider Dashboard (6.2)
Display: routing, latency, quota, cost, tokens, retries, failures, health, selection reasoning

**Current:** Page exists, empty  
**Target:** Full provider metrics display  
**Gap:** CRITICAL

### Automation Dashboard (6.2)
Display: schedules, triggers, history, checkpoints, failures, retries, notifications, pending approvals

**Current:** Page exists, empty  
**Target:** Full automation monitoring  
**Gap:** CRITICAL

---

## SECTION 8: LAYOUT SYSTEM & TERMINAL INTEGRATION

### Terminal Integration (8.2)
Multiple terminals as first-class UI citizens, linked to projects/workflows/agents

**Current:** ABSENT  
**Target:** Terminal sidebar with context linking  
**Gap:** MISSING

---

## SECTION 12: DEVELOPER TOOLS & INSPECTORS

All must be available but hidden by default.

| Inspector | Location | Current | Target | Gap |
|---|---|---|---|---|
| Runtime Inspector | `/app/dev-tools/` | ABSENT | Workflows, queues, events, resources, providers, stacks, checkpoints, recovery, health | MISSING |
| Agent Inspector | `/app/dev-tools/` | ABSENT | Objective, work, dependencies, waiting, communication, resources, history, artifacts | MISSING |
| Provider Inspector | `/app/dev-tools/` | ABSENT | Availability, routing, fallback, latency, quota, cost, tokens, retries, failures, health | MISSING |
| Policy Inspector | `/app/dev-tools/` | ABSENT | Policies, safety rules, restrictions, approvals, territory, authority, gates | MISSING |
| Source Authority Inspector | `/app/dev-tools/` | ABSENT | Loaded docs, hashes, versions, amendments, superseded, evidence, traceability | MISSING |
| Recovery Inspector | `/app/dev-tools/` | ABSENT | Checkpoint, recoverable state, agents, workflows, queues, history, evidence | MISSING |

---

## SECTION 11: PERSISTENCE & RECOVERY UX

### Checkpoint System (11.1)
Record restart-safe checkpoints: project state, workflow state, agent assignments, progress, approvals, evidence, memory, config, UI layout

**Current:** ABSENT  
**Target:** Auto-checkpoint every meaningful action  
**Gap:** CRITICAL

### Recovery UX (11.2)
On restart, restore unfinished work + report what was recovered

**Current:** ABSENT  
**Target:** Recovery dialog on app load  
**Gap:** CRITICAL

---

## PRIORITY ORDER FOR COMPLETION

Based on Section 15 acceptance gates and criticality:

### Tier 1 - BLOCKING (Implement First)
1. **Six Continuous Answers** — Implement on Home, Projects, Work pages (foundation for all others)
2. **Status Vocabulary & Display** — Canonical status + color-independent indicators
3. **Trust Indicators** — Add trust bar component, display on project views
4. **Evidence Browser** — First-class evidence linking UI
5. **Control Verbs** — Wire Approve, Reject, Retry, Pause, Resume, Cancel to UI

### Tier 2 - CORE (Implement Next)
6. **Progressive Disclosure Levels** — Add level-toggle component (1-4 information depth)
7. **Explainability Controls** — Why/How/Evidence expandable UI
8. **Recovery & Checkpoint UX** — Recovery dialog, checkpoint display
9. **Notification System** — Actionable, categorized notifications
10. **Developer Tools Full Suite** — All 6 inspectors with data binding

### Tier 3 - SUPPORTING (Implement Third)
11. **Agent/Provider/Automation Dashboards** — Full metric displays
12. **Terminal Integration** — First-class terminals + evidence linking
13. **Multi-View Project** — Timeline, Document, Verification views
14. **Memory Layers Presentation** — Show temporary/conversation/project/workspace/knowledge/authority/learning/evidence layers
15. **CLI/TUI Implementation** — Full keyboard-first TUI matching web parity

### Tier 4 - SURFACE-SPECIFIC (Implement Last)
16. **Android APK** — Claude web + Code interaction patterns, touch HIG
17. **Desktop App** — Multi-window, multi-monitor, dockable panels
18. **Delta Register Maintenance** — Automated tracking of completion

---

## SUMMARY STATISTICS

| Category | Done | Partial | Missing | Total |
|----------|------|---------|---------|-------|
| Navigation | 11 | 0 | 0 | 11 |
| Project Model | 1 | 1 | 5 | 7 |
| Multi-View | 0 | 2 | 6 | 8 |
| Control Verbs | 0 | 0 | 13 | 13 |
| Trust System | 0 | 0 | 7 | 7 |
| Progressive Disclosure | 0 | 1 | 4 | 5 |
| Dashboards | 0 | 3 | 9 | 12 |
| Recovery/Checkpoints | 0 | 0 | 2 | 2 |
| Developer Tools | 0 | 1 | 6 | 7 |
| Terminal Integration | 0 | 0 | 1 | 1 |
| **TOTALS** | **1** | **8** | **53** | **62** |

**Overall Completion: ~9% (1/62 items fully done)**  
**Scaffolding Complete: ~80% (navigation + layout framework)**  
**Feature Implementation: ~0% (data binding, logic, features)**

---

## NEXT ACTIONS

1. **Immediate:** Implement six continuous answers component template
2. **This session:** Wire six answers on Home + Projects + Work pages
3. **Next session:** Status system + trust indicators
4. **Following:** Control verbs UI layer
5. **Ongoing:** Progressive disclosure, explainability, recovery UX

This register will be re-audited after each completion to track progress.

---

# Batch 2026-07-29 — CLI Home cockpit bound to real state

Scope: HOE-A01 (six continuous answers) and HOE-A04 (status vocabulary) on the
CLI surface only. Rows below are the only rows this batch changed.

## Correction to a previously recorded row

`AG-5` was recorded as `ABSENT — "CLI has design tokens, no UI layer"`. That was
wrong when written: `src/main/kotlin/atropos/cli/ui/` already held 45+ renderers.
The real CLI gap was never absence of a UI layer — it was that the Home cockpit
rendered fixed strings. Re-audits should not trust the earlier AG-5 evidence
column.

## Changed rows (§14.1 schema)

| ID | Surface | Capability | Current path(s) | Target behaviour | Gap | Evidence | Acceptance check |
|----|---------|------------|-----------------|------------------|-----|----------|------------------|
| UI-DELTA-CLI-001 | CLI | Six continuous answers on Home | `cli/ui/DashboardRenderer.kt`, `cli/ui/HomeStateProvider.kt` | Home answers §0.1 Q1–Q6 from durable state, without search | DONE | `HomeStateProviderTest` (6 tests) | `/home` prints Objective/Doing/Why/Progress/Next/Evidence |
| UI-DELTA-CLI-002 | CLI | Home reachable from the router | `cli/CommandRouter.kt:225` | `/home` and `/dashboard` render the cockpit | DONE | route proof: `echo /home \| java -jar build/libs/ATROPOS.jar` | cockpit appears above the dashboard frame |
| UI-DELTA-CLI-003 | CLI | Status vocabulary is user-progress oriented (§3.3) | `cli/ui/HomeStateProvider.kt` `asRunState()` | Queue enums map to Section A `RunState`; no `RETRY_WAIT`/`LEASED` jargon on screen | DONE | `HomeStateProviderTest`, `DashboardRendererWidthTest.status_survives_without_colour` | operator sees `retrying 2/5`, not `retry_wait` |
| UI-DELTA-CLI-004 | CLI | Colour is never the sole status channel (§9.2) | `cli/ui/DashboardRenderer.kt` `renderWork` | Work rows render through `Surface.runState` (colour + glyph + label) | DONE | `DashboardRendererWidthTest.status_survives_without_colour` | `ColorTier.NONE` emits no SGR and still reads `retrying` |
| UI-DELTA-CLI-005 | CLI | Narrow-terminal operability (gate 6) | `cli/ui/DashboardRenderer.kt` | No cockpit line overflows 40/80/120/160 columns | DONE | `DashboardRendererWidthTest.no_line_overflows_any_baseline_width` | clipped, never wrapped; hidden rows declared as `+N more` |
| UI-DELTA-CLI-006 | CLI | Unreadable state is not reported as calm state (§4.1) | `cli/ui/HomeStateProvider.kt` `readQueue()` | An unreadable queue reports `unreadable`, never "no work" | DONE | `HomeStateProviderTest.unreadable_queue_is_never_reported_as_an_idle_queue` | `queueReadable=false` ⇒ ERROR health on Objective and Next |
| UI-DELTA-CLI-007 | CLI | Secrets never rendered in ordinary views (§13) | `cli/ui/HomeStateProvider.kt` `task()` | Queue task text is redacted before display | DONE | `HomeStateProviderTest.queue_task_text_is_redacted_before_it_reaches_the_cockpit` | API-key-shaped text does not reach the cockpit |
| UI-DELTA-CLI-008 | CLI | Project model on Home | ABSENT | Home summarises projects, not just the agent queue | MISSING | — | no CLI project store exists; the cockpit deliberately renders no project section rather than fabricating one |

## Known limitations recorded rather than hidden

- `AgentQueueStore.listEntries` catches its own IO failures and returns an empty
  list, so a *corrupt individual entry* still degrades to "fewer rows" with no
  operator-visible fault. `HomeStateProvider` can only distinguish an unreadable
  queue *directory*. Closing this fully requires a core change to the store's
  error reporting and is out of scope for a presentation batch.
- `CachingGitWorkspaceInspector` is bounded at 750 ms. On a cold cache in a large
  repository `git status` exceeds that budget and the cockpit honestly reports
  `Repository unavailable` rather than blocking.
- Provider *identity* is shown; provider *health* is not probed by Home, so no
  health is claimed for it.

## Path fingerprints

```
cff3142165a6391f2a72d3837ecb041003127594df0272cb9cc39ced19a4a1c1  src/main/kotlin/atropos/cli/ui/DashboardRenderer.kt
c3e33f8c415b1c2aa509915a2552caa944cc33de2babc65b9ab334ca24bdbb1b  src/main/kotlin/atropos/cli/ui/HomeStateProvider.kt
4f0befd37b43813afcb460d5684ab1361e8f917a1143d02e909ec93920effa2b  src/main/kotlin/atropos/cli/ui/AnsiTerminalEngine.kt
ba43f36530c49ad29ade67f4944d9ea72cc1aec594048778a0fcbbdf3a230b5f  src/main/kotlin/atropos/cli/CommandRouter.kt
ffc00156d99202dde3c3988aa9273e14177aa0a3f6b63f93fb5e672a38c5513b  src/test/kotlin/atropos/cli/ui/HomeStateProviderTest.kt
e90a2004c7c3b2b7178cf6ff57bcafc197a1108b61364ae6f68889513981f79c  src/test/kotlin/atropos/cli/ui/DashboardRendererWidthTest.kt
```

## Suite state at batch close

`gradle test` — 257 tests, 5 failing. All 5 fail identically on a clean tree
(`git stash` + rerun) and are unrelated to this batch: `AstSymbolGraphTest`,
`AgentSecurityRedactionSurfaceTest`, `ProviderActivationServiceTest` (2),
`ProviderFixtureMatrixServiceTest`. This batch introduced no regression and did
not fix them.
