ATROPOS Advanced HOE + Surface/Backend Obligation DAG v1
Authority: HOE UI/UX Gap Map v2 (unimplemented only) · Source Doc 6 · competitive UX harvest (Aider, Cursor, OpenCode, OpenHands, Claude, Codex, Antigravity) · production tree commit 94d473ad
Defaults locked: Android = Jetpack Compose · Web first paint = session-first · VS Code four-pane = switchable theme · Terminal v1 = log/output panel (PTY later)
Exclusion: Only unimplemented work. Skip HOE atoms already PARTIAL/WIRED in tree unless WIRE is still zero.
Orphan rule: Every atom’s WIRE names a production caller. Acceptance = callers ≥ 1 outside tests (or named bootstrap).
Parallel tracks: Track S (shared) → Track B (backend)  Track F (surfaces). FE and BE agents may run concurrent nodes with no edge between them.
LOC targets: ~40k UI/surface · ~10–20k backend.

How to use this DAG

Field
Meaning
ID
Stable atom id
Track
S / B / F-CLI / F-WEB / F-AND / F-X
dependsOn
Node ids that must be green first
RESEARCH
1–3 lines — LLM research prompt / competitive harvest
IMPL
1–3 lines — build rule, no new parallel systems
WIRE
1–3 lines — exact caller / registry; non-orphan acceptance
Critical path: S-001…S-012 → (B-001… and F-CLI-001… in parallel) → F-WEB → F-AND
→ F-X → GATE.

SHARED CONTRACT ATOMS (Track S)
· Six-answers status contract
dependsOn: []
RESEARCH: Diff Source Doc 4 six questions against HomeStateProvider/LandingRenderer fields; list missing answers on current CLI screenshot.
IMPL: Single typed StatusAnswers model in shared module; no second status system. WIRE: HomeStateProvider + Web status endpoint + Android bridge status all call StatusAnswers; doctor fails if any surface omits a field.

· Primary nav spine registry
dependsOn: [S-001]
RESEARCH: Diff CommandRouter/help vs
Home·Projects·Work·Conversations·Files·Agents·Models·Automation·History·Settings·Deve loper Tools.
IMPL: One spine list in command registry; Developer Tools hidden by default.
WIRE: CommandCatalog + Web nav + Android bottom nav read same spine source file.
· Project identity restart-safe
dependsOn: []
RESEARCH: Measure what project/session fields survive JAR restart today via StateSnapshot.
IMPL: Bind UI project id to existing durable store only; no new project DB.
WIRE: CLI project switch, Web project routes, Android project list all resolve via same ProjectRegistry API.
· Status vocabulary non-color
dependsOn: [S-001]
RESEARCH: Extract DesignTokens status strings; find color-only reliance. IMPL: Idle/Planning/Waiting/Working/Review
Required/Blocked/Completed/Failed/Cancelled; every color paired with icon+text. WIRE: DesignTokens + Web theme tokens + Android Material roles consume one vocabulary enum.
· Evidence affordance schema
dependsOn: []
RESEARCH: List all completion-claim render sites lacking evidence link.
IMPL: EvidenceRef { casHash, claimId, gateIds[] }; reuse EvidenceCollector paths.
WIRE: Completion cards CLI/Web/Android must render EvidenceRef or fail UI contract test.
· Disclosure L1–L4 contract
dependsOn: []
RESEARCH: Inventory disclosure levels; ban information-removal paths.
IMPL: Default collapsed; expand adds detail only; full depth stored in engine once.
WIRE: Thinking/Plan/Evidence/Engine/Checkpoint rows all implement DisclosureLevel API.
· Checkpoint product object
dependsOn: [S-003]
RESEARCH: Map StateSnapshot + .atropos/handoffs fields to chip + resume panel schema. IMPL: CheckpointViewModel { age, goalFp, nextAction, evidenceCount }; one primary
Continue.
WIRE: CLI chip, Web rail, Android sheet all bind CheckpointViewModel from RestartCoordinator.
· packages/atropos-web-contracts (and multi-surface contracts)
dependsOn: [S-001,S-004,S-005,S-006,S-007]

RESEARCH: Inventory existing contracts vs bridge API needs.
IMPL: Shared TS + Kotlin-serializable mirrors for status, evidence, checkpoint, approval. WIRE: Web app, bridge JSON, Android models import contracts; CI fails on drift.
· Secret-safe render path
dependsOn: []
RESEARCH: Grep render paths for secret leakage before UI paint.
IMPL: All renderers call RedactionFilter before paint; ordinary views never show raw secrets.
WIRE: AnsiTerminalEngine, Web message renderer, Android stream adapter each invoke redaction once.
· Recovery ribbon model
dependsOn: [S-007]
RESEARCH: RestartCoordinator report fields on first paint after recovery. IMPL: Ribbon { restored[], rebuilt[], failed[] }; never silent resume.
WIRE: CLI first frame, Web shell, Android launch all subscribe to recovery event.
· Command palette = registry
dependsOn: [S-002]
RESEARCH: Diff palette entries vs all primary actions.
IMPL: Generate palette from single registry; never hard-code.
WIRE: CLI palette, Web Ctrl/Cmd-K, Android search sheet share registry.
· Orphan doctor gate for new UI symbols
dependsOn: []
RESEARCH: Current orphan rate (125/869 pattern); define UI-symbol caller graph check.
IMPL: atropos doctor --orphans --ui fails PR if new surface file has zero production callers.
WIRE: CI job invokes doctor; merge blocked on red.

BACKEND / BRIDGE / INTEGRATION ATOMS (Track B) ~10–20k LOC
· One-command installer get.atropos.dev
dependsOn: []
RESEARCH: Compare Aider/OpenCode install friction; Termux aarch64 + linux + macOS paths.
IMPL: curl | sh detects OS/arch, installs accepted JAR/wrapper, creates config dir, runs doctor once.
WIRE: Public URL serves script; script writes binary onto PATH; CI publishes artifact hash.
· Provider env auto-discovery
dependsOn: []
RESEARCH: Fixed env table (OPENAI, ANTHROPIC, GROQ, XAI, GEMINI, OPENROUTER, OLLAMA_HOST, …).
IMPL: Scan once at start; cheap health check; persist providers.json; never require 30

exports.
WIRE: ProviderActivationService.startup() owns discovery; RoutePolicy reads healthy set only.
· providers refresh/list/test/prefer CLI
dependsOn: [B-002]
RESEARCH: Power-user control without launch-flag hell.
IMPL: Subcommands mutate preferred order and disable flags in config. WIRE: CommandRouter registers providers::*; doctor prints cascade.
· Parallel provider workers under Director
dependsOn: [B-002]
RESEARCH: Hierarchy research — providers as Worker backends, not flat chat.
IMPL: Director fans territories to provider workers; merge under VerifiedCompletionGate.
WIRE: AutonomousOrchestrator dispatches ProviderWorker; no provider expands territory.
· Local engine bridge API
dependsOn: [S-008]
RESEARCH: Minimal latency binder vs localhost; OpenHands agent-server separation.
IMPL: session/message/events/status/approve/evidence/files/cli endpoints; thin client protocol.
WIRE: Web SSE client + Android Compose client + optional editor extension call bridge only.
· Evidence substrate host boundary (Source Doc 6 correction)
dependsOn: []
RESEARCH: Host CAS + governance ledger + amendment chain; keep planner open (already published).
IMPL: Optional cloud sync of evidence/ledger only; local-only mode fully functional offline. WIRE: DloiService/EvidenceStore local paths remain authoritative; sync is additive module.
· Local-only mode flag
dependsOn: [B-006]
RESEARCH: Headline claim vs Cursor/Copilot cloud dependence.
IMPL: config localOnly=true disables network research planes; gates/hierarchy still run. WIRE: RoutePolicy + research planes check flag; UI shows Local badge from S-001.
· Sentry integration (Tier-0 differentiator)
dependsOn: [B-005]
RESEARCH: stack trace → file:line → territory patch → evidence bundle loop. IMPL: MCP/public API under BoundedAgencyGate; never second planner.
WIRE: Integration registry loads Sentry tool; doctor lists it; sample goal uses it in CI.
· GitHub Actions verify component
dependsOn: [B-005,S-005]
RESEARCH: Unattended CI is where restart-continuity proves value.

IMPL: Action runs atropos verify on PR diff; posts evidence + territory report as check. WIRE: Published action.yml in repo; example workflow calls it on pull_request.
· GitHub + local git MCP tools (table stakes, gated)
dependsOn: [B-005]
RESEARCH: Public OAuth/PAT only; no special backend access myth. IMPL: Issues/PRs/checks via user credentials; local git offline.
WIRE: ToolExecutor registers git/github; SecretSinkMatrix covers tokens.
· MCP client config schema
dependsOn: [B-005]
RESEARCH: Standard MCP JSON config; registry has 100k+ servers — support schema not each server.
IMPL: Accept MCP server list from config; territory-safe allowlist.
WIRE: Bridge loads MCP manager; atropos mcp list shows configured servers.
· Quota ledger visible API
dependsOn: [B-002]
RESEARCH: Quota is strategic asset; expose per-provider remaining + cost-per-verified-predicate.
IMPL: QuotaLedger metrics endpoint; free-first cascade already preferred. WIRE: StatusAnswers includes quota summary; Web/Android chips bind it.
· Thin VS Code / JetBrains / Neovim extension host
dependsOn: [B-005,S-001,S-007]
RESEARCH: Cursor win was zero migration — do not fork IDE; extension is window only.
IMPL: Status + six answers + checkpoint + send selection to ATROPOS; agent process external.
WIRE: Extension talks to local bridge; marketplace package optional later.
· atropos import cursor-rules / copilot-instructions
dependsOn: [S-003]
RESEARCH: Migration path without claiming to be Cursor.
IMPL: Import as hash-attested high-order context; non-overridable without amendment. WIRE: CLI command writes attested context pack; Director loads pack on project open.
· Zero-data-retention enterprise plane flag
dependsOn: [B-006]
RESEARCH: Source Doc 6 privacy controls.
IMPL: Research plane option that retains no customer code after response. WIRE: Policy gate on cloud calls; audit log records mode.
· Bit-identical artifact publish
dependsOn: [B-001]
RESEARCH: Same JAR hash Termux/desktop/CI.
IMPL: Release pipeline pins accepted hash; installer verifies. WIRE: doctor –version prints hash; mismatch fails install.


CLI TUI ATOMS (Track F-CLI) ~6–8k LOC
F-CLI-001 · Sticky header + anchored input
dependsOn: [S-001,S-004]
RESEARCH: Antigravity sticky prompt; measure redraw bounce on Termux aarch64.
IMPL: Extend LandingRenderer/HomeStateProvider; AnsiTerminalEngine sole canvas owner.
WIRE: Main render loop always paints header/input; golden snapshot 80-col.
F-CLI-002 · Disclosure rows Thinking/Plan/Evidence/Engine/Checkpoint
dependsOn: [S-006,S-005]
RESEARCH: Default collapsed; prototype cost at 40/80/120/160 cols. IMPL: ▸ rows on transcript renderer; L1 on first expand.
WIRE: TranscriptRenderer registers five row types; keybind toggles.
F-CLI-003 · Multi-level thinking filter
dependsOn: [F-CLI-002]
RESEARCH: L1 outline → L2 → L3; independent of Web/Android verbosity. IMPL: UI filter only; engine stores full depth.
WIRE: ThinkingStream consumer in CLI applies level filter.
F-CLI-004 · Checkpoint chip + resume panel
dependsOn: [S-007]
RESEARCH: One primary continue; no re-prompt mythology. IMPL: Present StateSnapshot as product object.
WIRE: Status bar chip opens panel; Continue calls RestartCoordinator.restore.
F-CLI-005 · Partial-command Enter-to-select
dependsOn: [S-011]
RESEARCH: Aider/Codex completion friction; Enter accepts highlight. IMPL: Suggestion list Enter-accept; never force full retype.
WIRE: PromptState completion UI bound to CommandCatalog.
F-CLI-006 · Providers one-line healthy summary
dependsOn: [B-002]
RESEARCH: Screenshot provider wall is failure mode.
IMPL: Default compact line; full matrix on expand or /providers full. WIRE: /providers command uses compact renderer by default.
F-CLI-007 · Thinking chip animation only
dependsOn: [F-CLI-001]
RESEARCH: ~30 FPS budget; reduced-motion; never stream engine logs into transcript. IMPL: Animate running chip only.
WIRE: Status chip subscribes to node progress events.
F-CLI-008 · Copy response card
dependsOn: []

RESEARCH: Termux clipboard APIs.
IMPL: Copy action on response bounds.
WIRE: Keybind + action registered in CommandCatalog.
F-CLI-009 · Territory + attestation optical focus
dependsOn: [S-004]
RESEARCH: Desaturate out-of-territory; sharpen valid attestation.
IMPL: DesignTokens weight/intensity roles only — no decorative glow. WIRE: Transcript theme applies territory state from TerritoryService.
F-CLI-010 · Responsive COMPACT/MEDIUM/WIDE + golden snapshots
dependsOn: [F-CLI-001]
RESEARCH: Antigravity resize breakage — dividers must be dynamic. IMPL: Breakpoints <60 / mid / 100+; NO_COLOR scannable.
WIRE: CI golden 40/80/120/160; fail on overflow.
F-CLI-011 · Competitive error ledger tests (CLI)
dependsOn: [F-CLI-010]
RESEARCH: Codify competitor failures as tests (bounce, color-only, always-expanded thinking).
IMPL: Automated UI contract tests. WIRE: test suite in CI required green.

WEB ATOMS (Track F-WEB) ~12–15k LOC
F-WEB-001 · Shell routes local-first
dependsOn: [S-008,B-005]
RESEARCH: OpenCode session model + WEB_MERGE_ARCHITECTURE; SpecGraph only
/developer/specgraph.
IMPL: Own apps/web shell; thin presentation over bridge.
WIRE: Next.js app router mounts shell; bridge client singleton.
F-WEB-002 · Session-first home (default theme)
dependsOn: [F-WEB-001,S-001]
RESEARCH: OpenCode homepage sessions list; six answers on Home/Projects/Work. IMPL: Session list + six-answer cards; no business logic in Web.
WIRE: Pages call status/session APIs only.
F-WEB-003 · VS Code layout theme (switchable)
dependsOn: [F-WEB-001,S-003]
RESEARCH: VS Code parts: sidebar explorer, editor tabs, panel, auxiliary bar; Cursor spatial memory without fork.
IMPL: Theme “workbench”: left file tree · center tabs · bottom log panel · right AI/evidence. Layout persistence.
WIRE: ThemeProvider toggles workbench vs session; user pref in local store.

F-WEB-004 · File explorer bound to project/git
dependsOn: [F-WEB-003,B-010]
RESEARCH: Explorer tree from local project + optional GitHub tree via user token. IMPL: Read-only tree v1; open file opens center tab.
WIRE: Explorer component calls project files API; tab state in session store.
F-WEB-005 · Editor tabs (viewer + light edit scope)
dependsOn: [F-WEB-004]
RESEARCH: Do not build full LSP IDE; viewer + agent-driven edits.
IMPL: Tab strip, buffer view, dirty marker; agent patches apply as diffs. WIRE: Tab model in front-end store; save goes through bridge file API.
F-WEB-006 · Bottom log/output panel v1
dependsOn: [F-WEB-003]
RESEARCH: VS Code panel; OpenHands terminal tab — v1 logs only, PTY later. IMPL: Streaming logs from bridge events.
WIRE: Panel subscribes to execution event stream.
F-WEB-007 · Right AI rail (conversation + checkpoint + evidence)
dependsOn: [S-005,S-007,F-WEB-002]
RESEARCH: Secondary sidebar pattern (Copilot chat position) + ATROPOS proof rail. IMPL: Stream, approval cards, checkpoint chip, evidence drawer.
WIRE: Rail uses bridge session/message/approve/evidence endpoints.
F-WEB-008 · Streaming + approval cards + palette
dependsOn: [F-WEB-007,S-011]
RESEARCH: SSE contracts; APPROVAL_REQUIRED card schema. IMPL: Consume bridge events; never reimplement policy.
WIRE: EventSource → card list; palette Ctrl/Cmd-K.
F-WEB-009 · Multi-level thinking / evidence drawer
dependsOn: [S-006,F-WEB-007]
RESEARCH: Side panel density; View Transitions for evidence morph. IMPL: Collapsed default; morph only when evidence exists.
WIRE: Drawer state independent of CLI verbosity channel.
F-WEB-010 · Developer Tools container
dependsOn: [F-WEB-001]
RESEARCH: SpecGraph isolation path.
IMPL: Hidden by default; SpecGraph only under /developer/specgraph. WIRE: Nav spine flag; route guard.
F-WEB-011 · Copy/download + a11y
dependsOn: [F-WEB-007]
RESEARCH: Non-color status; reduced-motion; keyboard-complete. IMPL: Release-blocking a11y checklist.
WIRE: Storybook/contract tests for contrast and focus.

F-WEB-012 · Layout theme persistence + recovery ribbon
dependsOn: [S-010,F-WEB-003]
RESEARCH: Survive refresh; report restored state.
IMPL: localStorage/session durable UI state + ribbon on recovery event. WIRE: Shell mounts ribbon from bridge recovery.

ANDROID ATOMS (Track F-AND) ~15–18k LOC · Compose
F-AND-001 · App shell Compose
dependsOn: [S-008,B-005]
RESEARCH: Claude mobile density; HIG 44dp; one-hand zones.
IMPL: Nav host: list · conversation · composer · offline. Never embed engine. WIRE: MainActivity → NavHost; all screens use bridge client.
F-AND-002 · Bridge client Android
dependsOn: [B-005]
RESEARCH: localhost/binder latency on device.
IMPL: Kotlin client for session/message/events/status/approve/evidence/files. WIRE: Single BridgeRepository injected in all ViewModels.
F-AND-003 · Conversation list + project boundary
dependsOn: [F-AND-001,S-003]
RESEARCH: First screen = last conversation or project list (default: last active if any else projects).
IMPL: Offline-capable list from local store.
WIRE: List ViewModel reads ProjectRegistry via bridge.
F-AND-004 · Primary stream full width
dependsOn: [F-AND-002]
RESEARCH: Claude stream; no permanent dual column on phone. IMPL: Message list + tool traces collapsed.
WIRE: Stream adapter maps bridge events to UI model.
F-AND-005 · Composer thumb zone
dependsOn: [F-AND-004]
RESEARCH: Bottom safe area; 44dp targets. IMPL: Text field + send + attach; IME aware. WIRE: Sends via bridge message API.
F-AND-006 · Checkpoint chip → sheet
dependsOn: [S-007]
RESEARCH: One-hand sheet; Resume primary.
IMPL: Chip under status or answer; sheet Resume/Next/Evidence. WIRE: Sheet ViewModel binds CheckpointViewModel.
F-AND-007 · Tools/timeline secondary sheet
dependsOn: [F-AND-004]

RESEARCH: Secondary as sheet not second column. IMPL: Timeline of tool calls/approvals.
WIRE: Opened from stream affordance; same event source.
F-AND-008 · Thinking levels in sheet
dependsOn: [S-006,F-AND-007]
RESEARCH: Never force deep thinking on mobile. IMPL: L1–L3 on demand.
WIRE: Independent verbosity pref on device.
F-AND-009 · Approval cards + push
dependsOn: [F-AND-004]
RESEARCH: Notification only for APPROVAL_REQUIRED. IMPL: In-stream cards + optional push.
WIRE: Bridge approval events → UI + FCM path optional.
F-AND-010 · Copy/share + offline resume
dependsOn: [F-AND-003]
RESEARCH: Share sheet; resume without laptop.
IMPL: Local durable state; share text/evidence link. WIRE: Resume path uses S-007 restore.
F-AND-011 · Sideload APK pipeline
dependsOn: [F-AND-001]
RESEARCH: Signing + install steps on target devices. IMPL: CI builds release APK; doc install steps.
WIRE: Release job uploads artifact; version matches engine compat field.
F-AND-012 · File tree sheet (VS Code IA lite)
dependsOn: [F-AND-003,B-010]
RESEARCH: Mobile file browse without four-pane. IMPL: Bottom sheet tree; open file preview.
WIRE: Uses project files API; preview is read-only v1.

CROSS-SURFACE / SUPERIORITY UI (Track F-X)
· Territory-as-material all surfaces
dependsOn: [F-CLI-009,F-WEB-007,F-AND-004]
RESEARCH: Single visual language for in/out territory. IMPL: Desaturate out; accent in; tokens only.
WIRE: Shared theme hook reads TerritoryService state.
· Attestation optical focus
dependsOn: [F-X-001]
RESEARCH: Variable weight / ANSI intensity. IMPL: Valid envelope sharpens; drift softens. WIRE: Evidence headers use attestation state.

· Independent verbosity channels
dependsOn: [S-006]
RESEARCH: Expand on one surface must not force another.
IMPL: Per-surface disclosure prefs; engine stores full depth once. WIRE: Pref keys namespaced cli/web/android.
· Evidence morph parity
dependsOn: [S-005]
RESEARCH: Web View Transition · Android sheet · CLI expand row. IMPL: Same fields; surface-specific gesture.
WIRE: Contract test same EvidenceRef shape.
· Mode retheme from status vocab only
dependsOn: [S-004]
RESEARCH: Planning/Working/Review/Blocked drive tokens. IMPL: No decorative random theme switch.
WIRE: Theme engine subscribes to status enum.
· Competitive checklist gate
dependsOn: [F-CLI-011,F-WEB-011,F-AND-010]
RESEARCH: Side-by-side Antigravity · OpenCode · Claude Android. IMPL: Checklist acceptance, not new features.
WIRE: Release job requires checklist file green.
· Uncertainty-calibrated recommendation UI
dependsOn: [S-005]
RESEARCH: Outside-box — show calibrated % + evidence ids, not confidence theater. IMPL: RecommendationCard { probability?, evidenceIds[] }.
WIRE: Only shown when engine provides calibration fields.
· Intent-conflict banner
dependsOn: [S-003]
RESEARCH: Surface conflict with prior hard prohibition before work. IMPL: Banner with exact prior clause.
WIRE: Intent layer emits conflict event; all surfaces listen.

COMPETITIVE UX HARVEST (embedded requirements)

Source
Steal
Fix
Expand in ATROPOS
Cursor
Spatial editor memory
No IDE
fork
External agent + evidence rail
Aider
Simple repo CLI,
git rollback
Multi-p rovider wall
Auto-discover providers; sticky chrome

Source Steal Fix Expand in ATROPOS

OpenCode
Sessions,
tabs,
composer
Weak
proof
Checkpoint + evidence + local-only
badge
OpenHands
Chat +
workspace
+ terminal split
Backen d
couplin g in UI
Bridge-only; parallel territories visible
Antigravity CLI
Sticky
prompt,
Esc cancel
Resize breaka ge
Dynamic breakpoints; golden cols
Claude mobile
Density, session client
Cloud
session bias
Offline resume; bridge to local engine
Codex CLI
Plan visibility, sandbox
Opaque done
C_real gates + evidence CAS
ChatGPT
Clean stream
No
project
/proof
Six answers + project boundary

LOC BUDGET MAP

Track
Approx new LOC
Notes
S shared
2–3k
contracts,
vocab, doctor
B backend
10–20k
install,
providers,
bridge, Sentry, GHA, MCP,
extension host
F-CLI
6–8k
sticky TUI, disclosure, goldens
F-WEB
12–15k
session shell + workbench
theme +
explorer/tabs/ rail
F-AND
15–18k
Compose shell
+ bridge client
+ sheets

Track Approx new LOC Notes

F-X
2–3k
parity,
calibration, checklist
Total
~47–67k
UI-heavy ~40k; backend mid band 10–20k

PARALLEL EXECUTION GUIDE
Agent F (Claude Code etc.) starts: S-001…S-012 (if free) then F-CLI-*  F-WEB-* after S-008/B-005.
Agent B (Codex etc.) starts: B-001…B-016 freely after S-008 where edged.
Merge rule: WIRE acceptance + doctor –orphans must pass before integrate.
Do not implement cloud “proprietary planner” — planner is open; host ledger/evidence only.

GATE
HOE advanced gate green when: 1. S atoms wired
CLI sticky + disclosure + checkpoint goldens pass
Web session default + VS Code theme switch works
Android sideload APK talks to bridge offline-capable
Provider discovery zero-config launch works
Sentry loop demo + GHA verify action published
doctor –orphans –ui green
Competitive checklist file signed
Non-goals: Fork VS Code · embed engine in APK · SpecGraph in primary nav · thinking expanded by default · color-only status · silent resume · 30 env vars to launch.

End of ATROPOS Advanced HOE + Surface/Backend Obligation DAG v1

VISUAL BLUEPRINT ATOMS (what you see)
F-VIS-001 · CLI open frame
dependsOn: [F-CLI-001]
RESEARCH: Top-left brand; header center ContextSight + status vocab; header right checkpoint age.
IMPL: Fixed rows 1–2 header; body scroll; last 2 rows input.
WIRE: LandingRenderer layout constants tested at 40/80/120 cols.

F-VIS-002 · CLI hero / body
dependsOn: [F-CLI-002]
RESEARCH: Transcript is hero; disclosure rows default ▸ collapsed. IMPL: No provider wall in hero; mythology answers banned.
WIRE: First paint uses S-001 answers not static copy.
F-VIS-003 · CLI footer
dependsOn: [F-CLI-001,F-CLI-006]
RESEARCH: Anchored input + one-line providers + keybind hint. IMPL: Footer never bounces on redraw.
WIRE: Input region owned by AnsiTerminalEngine only.
F-VIS-004 · Web open frame session theme
dependsOn: [F-WEB-002]
RESEARCH: Top bar project + status; main sessions or stream; composer bottom. IMPL: Six answers visible without search.
WIRE: Home page components bind StatusAnswers.
F-VIS-005 · Web open frame workbench theme
dependsOn: [F-WEB-003]
RESEARCH: Top-left activity/project; left explorer; center tabs; bottom panel; right AI. IMPL: Matches VS Code spatial habits; agent not inside editor process.
WIRE: Theme class on workbench root; screenshot tests.
F-VIS-006 · Web hero center
dependsOn: [F-WEB-005]
RESEARCH: Empty center = six-answers dashboard or last AI turn (default six-answers). IMPL: No blank dead end.
WIRE: EmptyState component registered in tab host.
F-VIS-007 · Web footer composer
dependsOn: [F-WEB-008]
RESEARCH: Always reachable composer; palette affordance. IMPL: Sticky composer in both themes.
WIRE: Composer posts to bridge message API.
F-VIS-008 · Android open frame
dependsOn: [F-AND-001]
RESEARCH: Top-left project/session title + status chip; no hamburger maze. IMPL: Material 3 top app bar.
WIRE: Scaffold topBar binds S-001/S-003.
F-VIS-009 · Android hero stream
dependsOn: [F-AND-004]
RESEARCH: Full-width stream is hero; tools in sheet. IMPL: LazyColumn messages; collapsed tool rows.
WIRE: Stream items from BridgeRepository flow.

F-VIS-010 · Android footer composer
dependsOn: [F-AND-005]
RESEARCH: Thumb zone above nav/IME.
IMPL: 48dp min touch; send always visible when focused. WIRE: Composer → bridge; offline queue if disconnected.
F-VIS-011 · Animation matrix
dependsOn: [F-CLI-007,F-WEB-009]
RESEARCH: Reduced-motion mandatory; only real progress animates.
IMPL: Table: thinking chip yes; full transcript no; evidence morph yes if evidence exists. WIRE: prefers-reduced-motion respected on Web/Android; CLI flag.
F-VIS-012 · HUD rules
dependsOn: [S-001,S-007]
RESEARCH: Status/checkpoint peripheral; never steal center from code/stream. IMPL: Chips in header/rail only.
WIRE: Lint/layout test forbids status block in hero center.

ADDITIONAL BACKEND ATOMS
· Linear issue → territory fix tool
dependsOn: [B-011]
RESEARCH: Public GraphQL; later tier after Sentry/GHA. IMPL: Tool under BoundedAgencyGate.
WIRE: Optional MCP; disabled until Tier-0 green.
· Slack/Discord slash distribution
dependsOn: [B-009]
RESEARCH: Distribution not product; after something worth distributing. IMPL: /atropos status|fix|evidence via webhooks.
WIRE: Bot tokens in keychain; posts evidence links only.
· Playwright verification tool for App Factory
dependsOn: [B-005]
RESEARCH: Browser verify generated apps.
IMPL: MCP playwright; screenshots as evidence CAS.
WIRE: Factory completion gate may require screenshot evidence id.
· doctor –orphans blocks new orphans
dependsOn: [S-012]
RESEARCH: Historical 125/869 zero-caller problem.
IMPL: AST/caller graph; exit non-zero on new orphans. WIRE: pre-commit or CI required.
· Free-space gate surfaced
dependsOn: [S-001]
RESEARCH: Storage constitution as correctness.

IMPL: StatusAnswers includes freeSpace/ceiling. WIRE: UI chips warn before write when low.
· AcceptanceVelocity metric API
dependsOn: [S-005]
RESEARCH: Progress metric not “agent did stuff”.
IMPL: Endpoint returns velocity + C_real components. WIRE: Web dashboard + CLI /metrics read same API.

ATOM COUNT & PARALLEL SCHEDULE
Approximate atom counts: S 12 · B 22 · F-CLI 11 · F-WEB 12 · F-AND 12 · F-X 8 · F-VIS 12 ≈
89 atoms (expand further in implementation without orphaning).
Week-style parallel slices 1. S-001…S-012 + B-001…B-003 + B-005
F-CLI-001…006 B-002…B-007
F-WEB-001…007 B-008…B-011
F-AND-001…006 B-012…B-016
Remaining F + F-X + GATE

FINAL PLANNING STATE (closed)
Defaults: Compose · session-first Web · VS Code theme switchable · log panel v1
Open-core: Client open · evidence/ledger hostable · planner open
First users: Auditability seekers, not Cursor migrants Integrations: Sentry → GitHub Actions → then stop Orphan ban: WIRE mandatory
End planning confirmed. Document is the implementation DAG.

SOURCE DOC 6 — FULL ATOMIZATION ADDENDUM
Rule: Anything in Source Doc 6 that is not already shipped becomes an atom. MCP servers are one atom each (or tight group only when inseparable). Every atom gets estLOC + RESEARCH + IMPL + WIRE.
Install & first-run (fine grain)
B-INST-001 · Platform detect · estLOC 80
RESEARCH: Termux aarch64 vs linux x64 vs macOS install paths used by Aider/OpenCode. IMPL: uname/arch mapping table only.
WIRE: get.atropos.dev script calls detect; writes ATROPOS_PLATFORM.

B-INST-002 · Artifact download + hash verify · estLOC 120 RESEARCH: Bit-identical accepted JAR hash publish.
IMPL: curl artifact; sha256 match or abort.
WIRE: installer exits non-zero on mismatch; doctor –version shows hash.
B-INST-003 · PATH / PREFIX install · estLOC 60 RESEARCH: Termux $PREFIX/bin vs ~/.local/bin. IMPL: wrapper script on PATH.
WIRE: command -v atropos succeeds post-install.
B-INST-004 · Config dir bootstrap · estLOC 80 RESEARCH: XDG vs Termux etc paths.
IMPL: empty valid config.yaml + providers.json skeleton. WIRE: first atropos reads config without crash.
B-INST-005 · Post-install doctor + six answers · estLOC 40 dependsOn: [S-001,B-INST-004]
IMPL: one doctor run; print ready.
WIRE: installer invokes atropos doctor --first-run.
B-INST-006 · npm fallback package · estLOC 100 IMPL: @atropos/cli publishes same wrapper.
WIRE: npm bin points at same doctor path.
B-PROV-001 · Env var table (authoritative list) · estLOC 150
IMPL: OPENAI, ANTHROPIC, GROQ, XAI/GROK, GOOGLE/GEMINI, TOGETHER, FIREWORKS, DEEPSEEK, MISTRAL, OPENROUTER, AZURE_OPENAI_, AWS_+Bedrock, OLLAMA_HOST, ATROPOS_PROVIDER_*, CLAUDE_API_KEY aliases.
WIRE: ProviderActivationService.scanEnv() only source of discovery.
B-PROV-002 · Per-key health check · estLOC 200
IMPL: format + optional cheap reachability; mark healthy/unhealthy/untested. WIRE: results → providers.json; RoutePolicy ignores unhealthy.
B-PROV-003 · Cascade print on launch · estLOC 60 IMPL: human one-screen discovery summary.
WIRE: Main.kt startup path prints once.
B-PROV-004 · providers list/refresh/test/prefer/disable · estLOC 250 WIRE: CommandRouter registers five subcommands.
B-PROV-005 · Zero healthy provider UX · estLOC 40 IMPL: clear message + one env example; no crash.
WIRE: startup branch when healthy.isEmpty().
B-PROV-006 · Parallel provider workers · estLOC 400 dependsOn: [B-PROV-002]
IMPL: Director assigns territory per provider worker; merge under

VerifiedCompletionGate.
WIRE: AutonomousOrchestrator creates ProviderWorker instances.
B-HELP-001 · /help surface · estLOC 200
RESEARCH: Aider/Codex help density without wall of text. IMPL: spine-aware help from CommandCatalog.
WIRE: /help and -h registered; Web help route uses same catalog JSON.
B-HELP-002 · One-pager docs install · estLOC 100
IMPL: “one key in env → atropos” page listing env vars.
WIRE: get.atropos.dev links to doc; README section generated from B-PROV-001 table.

MCP & integrations — one atom per integration (territory-gated)
**Architectural rule (all B-MCP-*):** tool under BoundedAgencyGate + SecretSinkMatrix; never second planner; tokens in env/keychain only.
Tier 0 / differentiator first
B-MCP-SENTRY · Sentry issues API · estLOC 350 RESEARCH: stack→file:line→patch→evidence loop. IMPL: public API/MCP; evidence CAS on result.
WIRE: IntegrationRegistry + sample goal in CI.
B-MCP-GHA · GitHub Actions verify action · estLOC 300 IMPL: action.yml runs atropos verify; posts check.
WIRE: example workflow in repo; marketplace optional later.
B-MCP-GITHUB · GitHub REST/GraphQL MCP · estLOC 400 IMPL: issues/PRs/checks/reviews via user PAT/OAuth.
WIRE: ToolExecutor; mcp config optional.
B-MCP-GITLOCAL · Local git MCP · estLOC 200 IMPL: diff/commit/rebase offline.
WIRE: always-on local tool.
B-MCP-FS · Filesystem MCP sandboxed · estLOC 150 IMPL: workspace-bounded read/write/search.
WIRE: core tool; already partial — finish WIRE to ToolExecutor.
Tier 1
B-MCP-GITLAB · estLOC 300 · MRs/pipelines
B-MCP-BITBUCKET · estLOC 250 · Atlassian REST B-MCP-LINEAR · estLOC 300 · GraphQL issues→PR B-MCP-JIRA · estLOC 300 · Atlassian REST
B-MCP-CONFLUENCE · estLOC 200
B-MCP-PLAYWRIGHT · estLOC 350 · screenshots as evidence

B-MCP-PUPPETEER · estLOC 300
B-MCP-DOCKER · estLOC 280 · Engine API
B-MCP-POSTGRES · estLOC 220
B-MCP-SQLITE · estLOC 150
B-MCP-REDIS · estLOC 150
B-MCP-Snyk · estLOC 250
B-MCP-SONAR · estLOC 250
B-MCP-DATADOG · estLOC 250 B-MCP-NEWRELIC · estLOC 250 B-MCP-SUPABASE · estLOC 200 B-MCP-FIREBASE · estLOC 200
For each Tier-1 atom above:
RESEARCH: public API/MCP only; no partnership. IMPL: adapter + allowlist + territory.
WIRE: IntegrationRegistry entry; atropos mcp list shows; disabled by default until allowlisted.
Tier 2
B-MCP-SLACK · estLOC 280 · distribution after product
B-MCP-DISCORD · estLOC 260
B-MCP-TEAMS · estLOC 180 · webhooks
B-MCP-ASANA · estLOC 200
B-MCP-CLICKUP · estLOC 200
B-MCP-NOTION · estLOC 220
B-MCP-PAGERDUTY · estLOC 180
B-MCP-OPSGENIE · estLOC 160
B-MCP-TERRAFORM · estLOC 200 · read state
B-MCP-PULUMI · estLOC 180
B-MCP-K8S · estLOC 300 · read + limited apply under territory
B-MCP-AWS-READ · estLOC 250 · read-only
B-MCP-GCP-READ · estLOC 250
B-MCP-AZURE-READ · estLOC 250
Tier 3 / registry
B-MCP-BRIDGE-SCHEMA · generic MCP JSON config · estLOC 400 RESEARCH: 100k+ registry servers — support schema not each server. IMPL: stdio/HTTP MCP client; allowlist.
WIRE: config.mcp.servers[] loaded at start.
B-MCP-MEMORY · estLOC 120 · assist only, never authority
B-MCP-SEQUENTIAL-THINKING · estLOC 100 · research plane optional
B-MCP-FETCH · estLOC 100
B-MCP-TIME · estLOC 60
B-MCP-EVERYTHING-REF · estLOC 40 · test only

B-MCP-OAUTH-UX · browser OAuth for GitHub/Linear · estLOC 300 IMPL: open browser; store token keychain.
WIRE: atropos auth github command.
B-MCP-KEYCHAIN · secret storage · estLOC 200 IMPL: env first, then OS keychain; never log token. WIRE: SecretVault/TokenIsolationVault callers.

Superiority axes as atoms (outside-the-box list)
Each is ABSENT until implemented; estLOC is implementation+tests.
B-SUP-001 · Bit-exact reproducibility certificate · estLOC 500
B-SUP-002 · Potential Φ termination on autonomous loops · estLOC 400
B-SUP-003 · Information-theoretic context budget · estLOC 350
B-SUP-004 · Adversarial self-play verifier · estLOC 600
B-SUP-005 · Causal impact graph · estLOC 450
B-SUP-006 · MDL patch preference · estLOC 200
B-SUP-007 · Joule/$ cost ledger per verified predicate · estLOC 300
B-SUP-008 · Provider arbitrage engine · estLOC 400 B-SUP-009 · Quota futures/reservation · estLOC 350 B-SUP-010 · Disk entropy budget · estLOC 250
B-SUP-011 · Logical/vector clocks on claims · estLOC 300 B-SUP-012 · Time-travel agent state replay · estLOC 500 B-SUP-013 · Deadline-aware scheduling · estLOC 280
B-SUP-014 · Circadian/human-presence autonomy · estLOC 220
B-SUP-015 · Secret non-interference (IFC) · estLOC 600
B-SUP-016 · Capability attenuation sandbox · estLOC 350
B-SUP-017 · Prompt-injection typed envelope · estLOC 300
B-SUP-018 · Supply-chain attestation binary/response · estLOC 250
B-SUP-019 · Sub-agent incentive mechanism · estLOC 300
B-SUP-020 · Coalitional stability check · estLOC 280
B-SUP-021 · Resource auction (context/quota/disk) · estLOC 350
B-SUP-022 · Hypothesis registry pre-registration · estLOC 300
B-SUP-023 · Negative-result memory · estLOC 250
B-SUP-024 · Cross-project invariant mining · estLOC 400
B-SUP-025 · Counterfactual replay · estLOC 450
F-SUP-026 · Uncertainty-calibrated UI · estLOC 200
F-SUP-027 · Intent-conflict detector UI · estLOC 180
F-SUP-028 · Explanation-as-proof tree UI · estLOC 350
F-SUP-029 · Human veto residual obligation · estLOC 200 B-SUP-030 · Bit-identical multi-env install · estLOC 150 B-SUP-031 · Air-gapped full gates mode · estLOC 200
B-SUP-032 · Agent-as-library embed API · estLOC 400
B-SUP-033 · Formal multi-surface contract tests · estLOC 300

B-SUP-034 · Public benchmark harness · estLOC 600
B-SUP-035 · Live competitive shadow mode · estLOC 500 B-SUP-036 · Regret minimization dashboard · estLOC 280 B-SUP-037 · Superiority invariant set · estLOC 250
B-SUP-038 · Auto superiority regression tests · estLOC 300
B-SUP-039 · Bit-level audit log of superiority claims · estLOC 200
For each B-SUP/F-SUP:
RESEARCH: one-line “how Cursor fails this axis.” IMPL: measurable acceptance predicate.
WIRE: named owner module + doctor or release gate checks invariant.

Open-core boundary atoms (corrected)
B-OC-001 · Document client vs ledger host boundary · estLOC 80
IMPL: CONTRIBUTING + ARCHITECTURE section: planner open; CAS/ledger hostable. WIRE: README links; CI checks no “proprietary planner” claim in marketing strings.
B-OC-002 · Local-only default path · estLOC 150 IMPL: works with zero network; badge in S-001. WIRE: integration tests offline.
B-OC-003 · AGPL §13 enterprise note · estLOC 40 IMPL: LEGAL.md note for procurement.
WIRE: linked from install docs.
B-OC-004 · Zero-retention research plane · estLOC 200 WIRE: PolicyGate mode flag audited.

Revised totals

Block
Atoms (approx)
estLOC band
Prior S/F/B core
~89
47–67k
Install/provi der/help
~15
~2k
MCP/integra tions
~45
~8–12k
Superiority axes
~39
~12–18k
Open-core boundary
4
~0.5k

Block Atoms (approx) estLOC band

Combined
~190+
UI still ~40k · backend
can land 15–30k if SUP fully pursued
Build order reminder: Tier MCP = Sentry → GHA → generic MCP schema → GitHub/git →
stop. Superiority axes after core WIRE green. Help and install before growth integrations.
Orphan rule unchanged: no atom merges without production caller named in WIRE.

MICRO-ATOM EXPANSION — cannot split further
Definition: One atom = one symbol or one acceptance predicate or one user-visible affordance. If it has two verbs, split it.
Provider discovery micro-atoms
B-PROV-001a · Read OPENAI_API_KEY · estLOC 5 · WIRE: scanEnv
B-PROV-001b · Read OPENAI_API_BASE · estLOC 5
B-PROV-001c · Read ANTHROPIC_API_KEY · estLOC 5
B-PROV-001d · Read GROQ_API_KEY · estLOC 5
B-PROV-001e · Read XAI_API_KEY · estLOC 5
B-PROV-001f · Read GROK_API_KEY alias · estLOC 5
B-PROV-001g · Read GOOGLE_API_KEY · estLOC 5
B-PROV-001h · Read GEMINI_API_KEY alias · estLOC 5
B-PROV-001i · Read TOGETHER_API_KEY · estLOC 5 B-PROV-001j · Read FIREWORKS_API_KEY · estLOC 5 B-PROV-001k · Read DEEPSEEK_API_KEY · estLOC 5 B-PROV-001l · Read MISTRAL_API_KEY · estLOC 5
B-PROV-001m · Read OPENROUTER_API_KEY · estLOC 5
B-PROV-001n · Read AZURE_OPENAI_API_KEY · estLOC 5
B-PROV-001o · Read AZURE_OPENAI_ENDPOINT · estLOC 5
B-PROV-001p · Read AWS_ACCESS_KEY_ID · estLOC 5
B-PROV-001q · Read AWS_SECRET_ACCESS_KEY · estLOC 5
B-PROV-001r · Read AWS_REGION for Bedrock · estLOC 5
B-PROV-001s · Read OLLAMA_HOST · estLOC 5
B-PROV-001t · Read ATROPOS_PROVIDER_* glob · estLOC 15
B-PROV-001u · Read CLAUDE_API_KEY alias · estLOC 5
B-PROV-002a · Validate key non-empty · estLOC 10
B-PROV-002b · Validate key format regex per provider · estLOC 40
B-PROV-002c · TCP/TLS reachability probe optional · estLOC 50
B-PROV-002d · Mark healthy · estLOC 5
B-PROV-002e · Mark unhealthy · estLOC 5
B-PROV-002f · Mark untested · estLOC 5
B-PROV-002g · Persist providers.json write · estLOC 30

B-PROV-002h · Persist providers.json read on next boot · estLOC 20
B-PROV-003a · Print discovered count line · estLOC 10
B-PROV-003b · Print one healthy row · estLOC 15
B-PROV-003c · Print one unhealthy row · estLOC 15 B-PROV-003d · Print cascade order line · estLOC 10 B-PROV-004a · Command providers list · estLOC 40
B-PROV-004b · Command providers refresh · estLOC 30
B-PROV-004c · Command providers test · estLOC 40
B-PROV-004d · Command providers prefer · estLOC 35 B-PROV-004e · Command providers disable · estLOC 30 B-PROV-005a · Branch zero healthy · estLOC 20
B-PROV-005b · Message how to set one key · estLOC 15
B-PROV-006a · Create ProviderWorker for provider P · estLOC 80
B-PROV-006b · Assign territory grant to worker · estLOC 40
B-PROV-006c · Assign acceptance predicate to worker · estLOC 40
B-PROV-006d · Collect worker proposal · estLOC 50
B-PROV-006e · Collect worker evidence refs · estLOC 40
B-PROV-006f · Merge proposals under VerifiedCompletionGate · estLOC 80
B-PROV-006g · Reject cross-territory provider write · estLOC 40
Install micro-atoms
B-INST-001a · Detect linux · estLOC 5
B-INST-001b · Detect aarch64 · estLOC 5 B-INST-001c · Detect x86_64 · estLOC 5 B-INST-001d · Detect darwin · estLOC 5
B-INST-001e · Detect Termux PREFIX · estLOC 10
B-INST-002a · Resolve latest accepted artifact URL · estLOC 20
B-INST-002b · Download bytes · estLOC 15
B-INST-002c · Compute sha256 · estLOC 10
B-INST-002d · Compare to published hash · estLOC 10
B-INST-002e · Abort on mismatch · estLOC 5
B-INST-003a · Write binary/wrapper file · estLOC 15
B-INST-003b · chmod +x · estLOC 5
B-INST-003c · Symlink or PATH append instruction · estLOC 15
B-INST-004a · mkdir config dir · estLOC 5
B-INST-004b · Write empty config.yaml skeleton · estLOC 20
B-INST-004c · Write empty providers.json skeleton · estLOC 15
B-INST-005a · Invoke doctor · estLOC 10
B-INST-005b · Print six answers block · estLOC 20
B-INST-005c · Print ready line · estLOC 5
Sentry loop micro-atoms (differentiator)
B-MCP-SENTRY-a · Load Sentry auth token from keychain/env · estLOC 15
B-MCP-SENTRY-b · List unresolved issues for project · estLOC 40
B-MCP-SENTRY-c · Fetch single issue detail · estLOC 30

B-MCP-SENTRY-d · Parse top stack frame file path · estLOC 40
B-MCP-SENTRY-e · Parse top stack frame line number · estLOC 20
B-MCP-SENTRY-f · Map frame to workspace path · estLOC 40
B-MCP-SENTRY-g · Open territory grant around file · estLOC 30
B-MCP-SENTRY-h · Build goal from issue title+culprit · estLOC 30
B-MCP-SENTRY-i · Request patch proposal from worker · estLOC 20
B-MCP-SENTRY-j · Run verification gates on patch · estLOC 20
B-MCP-SENTRY-k · Write evidence bundle CAS hash · estLOC 40
B-MCP-SENTRY-l · Attach evidence hash to issue comment optional · estLOC 40
B-MCP-SENTRY-m · Register tool in IntegrationRegistry · estLOC 15
B-MCP-SENTRY-n · BoundedAgencyGate check before call · estLOC 10
B-MCP-SENTRY-o · SecretSinkMatrix: token never to logs · estLOC 15
GitHub micro-atoms
B-MCP-GH-a · OAuth device/browser flow start · estLOC 50
B-MCP-GH-b · Persist GitHub token keychain · estLOC 20
B-MCP-GH-c · List issues · estLOC 30
B-MCP-GH-d · Get issue · estLOC 20
B-MCP-GH-e · Create issue · estLOC 25
B-MCP-GH-f · Comment on issue · estLOC 20
B-MCP-GH-g · List PRs · estLOC 30
B-MCP-GH-h · Get PR files · estLOC 25
B-MCP-GH-i · Create PR · estLOC 40
B-MCP-GH-j · Request PR review · estLOC 20 B-MCP-GH-k · Post PR comment · estLOC 20 B-MCP-GH-l · List check runs · estLOC 25
B-MCP-GH-m · Create check run · estLOC 35
B-MCP-GH-n · Update check run conclusion · estLOC 25
B-MCP-GH-o · Read branch protection · estLOC 25
B-MCP-GH-p · GraphQL file blame optional · estLOC 40
B-MCP-GH-q · Gate every call on territory · estLOC 15
B-MCP-GH-r · Gate every call on SecretSinkMatrix · estLOC 15
GitHub Actions micro-atoms
B-MCP-GHA-a · action.yml declares inputs · estLOC 30 B-MCP-GHA-b · Checkout workspace step · estLOC 10 B-MCP-GHA-c · Install atropos step · estLOC 20
B-MCP-GHA-d · Run atropos verify on diff · estLOC 30
B-MCP-GHA-e · Parse evidence hashes from output · estLOC 25
B-MCP-GHA-f · Post commit status / check · estLOC 35
B-MCP-GHA-g · Example workflow file in repo · estLOC 20
B-MCP-GHA-h · Fail job on gate red · estLOC 10

Local git micro-atoms
B-MCP-GIT-a · git status porcelain · estLOC 15
B-MCP-GIT-b · git diff · estLOC 15
B-MCP-GIT-c · git add path · estLOC 10
B-MCP-GIT-d · git commit message · estLOC 20 B-MCP-GIT-e · git rebase continue · estLOC 25 B-MCP-GIT-f · conflict file list · estLOC 20
B-MCP-GIT-g · worktree add under territory · estLOC 40
Generic MCP client micro-atoms
B-MCP-CORE-a · Parse mcp.json servers array · estLOC 30 B-MCP-CORE-b · Spawn stdio server process · estLOC 40 B-MCP-CORE-c · Initialize MCP handshake · estLOC 30
B-MCP-CORE-d · List tools from server · estLOC 20
B-MCP-CORE-e · Call tool by name · estLOC 30
B-MCP-CORE-f · Map tool result to evidence blob · estLOC 40
B-MCP-CORE-g · Kill server on session end · estLOC 15
B-MCP-CORE-h · Allowlist server name check · estLOC 15 B-MCP-CORE-i · Deny non-allowlisted server · estLOC 10 B-MCP-CORE-j · Command atropos mcp list · estLOC 25
B-MCP-CORE-k · Command atropos mcp test · estLOC 30
Remaining integrations — one verb each (pattern)
For each of GitLab, Bitbucket, Linear, Jira, Confluence, Playwright, Puppeteer, Docker,
Postgres, SQLite, Redis, Snyk, Sonar, Datadog, NewRelic, Supabase, Firebase, Slack, Discord, Teams, Asana, ClickUp, Notion, PagerDuty, Opsgenie, Terraform-read, Pulumi-read,
K8s-read, K8s-limited-apply, AWS-read, GCP-read, Azure-read:
Split at minimum into: - auth load - list/search primary objects - get one object -
mutate safe object (if any) - registry register - gate territory - gate secrets
IDs: B-MCP-<SYS>-auth|list|get|mutate|reg|terr|sec estLOC 10–40 each · WIRE IntegrationRegistry + ToolExecutor Default disabled until allowlisted (except Sentry/GHA/git/fs).
Help micro-atoms
B-HELP-001a · Build help from CommandCatalog · estLOC 40
B-HELP-001b · Render help CLI · estLOC 30
B-HELP-001c · Render help JSON for Web · estLOC 20
B-HELP-002a · Generate env-var table markdown from B-PROV-001* · estLOC 30
B-HELP-002b · Publish one-pager path · estLOC 15

Superiority axes — keep one atom per axis but add acceptance symbol
Each B-SUP-/F-SUP- gains: - acceptance predicate function name - owner module path -
release-gate hook
Example:
B-SUP-001a · Compute certificate root hash · estLOC 80
B-SUP-001b · Persist certificate object CAS · estLOC 40
B-SUP-001c · Verify certificate CLI command · estLOC 50
B-SUP-001d · Release gate: certificate exists for tagged release · estLOC 20
(Apply same a/b/c/d split pattern to SUP-002…039 when implementing; do not implement before Tier-0 WIRE green.)
Orphan-prevention micro-atoms
S-012a · Parse production Kotlin symbols · estLOC 80
S-012b · Parse production TS symbols · estLOC 60
S-012c · Build caller graph edges · estLOC 100
S-012d · Flag zero-caller production symbols · estLOC 40
S-012e · Exit non-zero in CI · estLOC 10
S-012f · Allowlist bootstrap exceptions file · estLOC 20
Count
Micro-addendum alone adds ~200+ indivisible atoms on top of v1.1. Grand total order ~400 atoms when every integration is verb-split.
If an atom still contains “and”, split again.