# ATROPOS Completion Blueprint DAG — REVISED

**Key change: ALL UI/UX atoms come FIRST, starting with the most obvious and
most used features, before any other stage work begins.**

This guarantees that from the earliest possible moment, ATROPOS looks, feels,
and behaves exactly like OpenCode's terminal CLI — same layout, fonts, specs,
HIG, connections, and interaction model.

---

## Phase 0 — UI/UX Parity (Immediate)

Every atom in this phase is about making ATROPOS's terminal interface
**indistinguishable from OpenCode's CLI** in look, feel, and behavior.

### Priority 1 — What the user sees and touches every session

```
U001_COMMAND_PROMPT
  └── The /agent ask, /agent patch, /shell, !cmd prompt line
  └── Must match OpenCode's exact prompt rendering (prefix, spacing, cursor)
  └── Current: AnsiTerminalEngine exists but rendering is not pixel-matched
  └── DONE when: prompt line is visually identical to OpenCode

U002_OUTPUT_RENDERING
  └── Every command output (ask, patch, status, etc.) renders in OpenCode format:
       header, body, footer, status line
  └── Code blocks rendered with correct indentation, line numbers, syntax coloring
  └── Current: formatBlock() exists but uses ad-hoc formatting
  └── DONE when: /agent ask output matches OpenCode exactly

U003_STATUS_BAR
  └── Persistent status line at bottom of terminal showing:
       active provider, queue depth, quota state, last patch ID, DAG status
  └── Updates in real-time as state changes
  └── Current: no persistent status bar
  └── DONE when: status bar matches OpenCode's exactly (content + format)

U004_ERROR_RENDERING
  └── Errors rendered in OpenCode's exact error format (icon, color, stack context)
  └── Warnings similarly differentiated
  └── DONE when: errors look identical to OpenCode
```

### Priority 2 — Navigation and interaction

```
U005_RAW_KEY_READER
  └── Arrow key navigation (up/down through history, left/right within line)
  └── Tab completion with visual popup matching OpenCode
  └── Emacs keybindings (C-a, C-e, C-k, C-y, etc.) + Vim mode
  └── Current: RawKeyReader exists as STUB
  └── DONE when: key handling is indistinguishable from OpenCode

U006_COMMAND_HISTORY
  └── Persistent, searchable, secret-redacted command history
  └── Up/down arrow recalls history with OpenCode's exact visual behavior
  └── C-r reverse search with matching OpenCode highlighting
  └── Current: CommandHistory exists as PARTIAL
  └── DONE when: history navigation matches OpenCode exactly
```

### Priority 3 — Visual polish and HIG

```
U007_TERMINAL_THEME
  └── Light/dark mode toggle matching OpenCode's exact color palette
  └── Custom theme support
  └── ANSI 256-color + truecolor rendering
  └── Current: TerminalTheme exists with basic colors
  └── DONE when: color output matches OpenCode's palette pixel-for-pixel

U008_TYPOGRAPHY_AND_SPACING
  └── Font rendering (monospace glyphs, line height, character spacing)
  └── Padding, margins, borders matching OpenCode's exact measurements
  └── List/table formatting (alignment, truncation, wrapping)
  └── DONE when: any formatted output matches OpenCode's spacing exactly

U009_RESPONSIVE_LAYOUT
  └── Terminal width detection (40/80/120+ columns)
  └── Content reflow on resize without artifacts
  └── Dashboard layout adapts to available space
  └── Current: responsive exists as PARTIAL
  └── DONE when: resizing behaves identically to OpenCode
```

### Priority 4 — Dashboard and tabs

```
U010_DASHBOARD_TABS
  └── Tabs: Dashboard, Chat, Providers, Factory, Logs — matching OpenCode layout
  └── Tab state survives terminal resize and command execution
  └── Tab content is live/refreshable
  └── Current: DashboardTabsScreens exists as PARTIAL
  └── DONE when: tabs match OpenCode's exact layout and behavior

U011_DASHBOARD_WIDGETS
  └── Provider health widget (list of configured/active/quota state)
  └── Queue widget (depth, oldest job, retry count)
  └── DAG status widget (completed/failed/blocked by phase)
  └── Recent patches widget (last 5 with status)
  └── DONE when: dashboard matches OpenCode's information density and layout
```

### Priority 5 — Shell and connections

```
U012_SHELL_BRIDGE
  └── !cmd, /shell, /pwd, /cd, /ls, /git status with OpenCode's exact output
  └── Shell allowlist, timeout, cwd-aware rendering
  └── Current: ShellBridge exists as PARTIAL
  └── DONE when: shell output renders exactly like OpenCode

U013_CONNECTION_PANEL
  └── Show connected services: Google, GitHub, Supabase, Vercel, MCPs
  └── Status indicators (connected/disconnected/error)
  └── Quick-reconnect from panel
  └── DONE when: connection panel matches OpenCode's provider list exactly
```

### Priority 6 — Clipboard, mouse, and remaining interaction

```
U014_CLIPBOARD_INTEGRATION
  └── Copy selection to clipboard (C-c, C-Insert, mouse selection)
  └── Paste from clipboard (C-v, Shift-Insert)
  └── DONE when: copy/paste behaves identically to OpenCode

U015_MOUSE_SUPPORT
  └── Clickable links in output
  └── Scroll with mouse wheel
  └── Click to focus/select
  └── DONE when: mouse interaction matches OpenCode

U016_SPINNERS_AND_PROGRESS
  └── Spinner animation during provider calls (exact OpenCode characters and cadence)
  └── Progress bars for long operations
  └── DONE when: progress indicators match OpenCode exactly
```

### UI/UX Acceptance Gate
```
ALL UI atoms U001–U016 must be DONE.
Comparison: side-by-side with OpenCode in same terminal, same commands.
Any visual or behavioral difference is a blocker.

PHASE 0 COMPLETE MARKER: ATROPOS_UI_UX_PARITY: VERIFIED
```

---

## Stage 1 — Self-Buildable

After UI/UX parity is achieved, build the inside-out self-hosting loop so
ATROPOS can build itself autonomously.

### Phase 1.1 — Foundation Lock

```
F001_dloi_address_router
  └── A001_source_document_registry (register SD1, SD2, section IDs, hashes)
  └── A002_source_section_address (typed section IDs)
  └── A003_dloi_router (route intent to domain/category/leaf)
```

### Phase 1.2 — Provider Identity & Context (already built)

```
C001_context_envelope
  └── C002_context_envelope_factory
  └── C003_context_envelope_serializer
  └── C004_provider_context_injector
  └── C005_context_attestation
  └── C006_provider_response_parser
  └── C007_context_attestation_service
  └── C008_context_drift_detector
  └── C009_typed_context_failure
```

### Phase 1.3 — Local Runtime & Verification

```
C001_kotlin_jvm_runtime ─── C002_local_toolchain_provider
  ├── C003_kotlin_compile_probe
  ├── C004_git_state_probe
  └── C005_termux_path_resolver

D001_deterministic_verifier ─── D004_no_fake_success_gate
  ├── D002_constraint_solver_evaluator (real checks, not stub)
  ├── D003_probabilistic_immunity_engine (parse stderr)
  └── D005_safe_jar_swap_gate (preserve old jar on failure)
```

### Phase 1.4 — Memory, Queue, Daemon

```
F003_local_memory_store ─── F004_sqlite_vec_integration
  ├── F005_chunking_1024_overlap
  ├── E002_reward_penalty_store
  └── E001_self_improving_compile_loop

L004_redaction_filter ─── L002_secret_source
  ├── L001_token_isolation_vault (REAL vault, not stub)
  └── L003_key_doctor

J005_agent_queue ─── J006_agent_daemon
```

### Phase 1.5 — Agent Core (parallel group)

```
J001_agent_ask  J002_agent_patch  J003_agent_apply_check  J004_agent_apply_latest
H001_quota_ledger  H002_provider_usage_event  H003_route_policy
H004_free_mode_guard  H005_emergency_paid_gate  H006_fallback_chains
H007_queue_when_free_unavailable
G001_api_capability  G002_provider_descriptor  G003_provider_result
G004_provider_error  G005_provider_registry_30
```

### Phase 1.6 — DAG Core Execution

```
J010_director_orchestrator (expand from STUB)
  └── J011_worker_code_synthesizer (expand from STUB)
  └── DagExecutionService fully wired with attested provider dispatch
  └── executeProviderCall() uses AgentRunService + context envelope
  └── IsolatedWorktreeService enforces territory on file mutations
  └── VerifiedCompletionGate blocks false completions
  └── CrashRecoveryService handles stale claims and restarts
```

### Phase 1.7 — Self-Hosting Loop (complete end-to-end)

```
SelfHostGoalService full loop:
  startGoal → setDag → selectNextDagNode →
  evaluateReadyDagNode (attested provider) →
  verify → record experience → continue

GoalContinuationService full loop:
  completeRun → condition check → next node selection →
  automatic continuation → recovery → experience persistence

E003_error_gradient_extractor (localized repair from stderr)
  └── feeds into SelfHostGoalService repair cycle
```

### Stage 1 Acceptance Gate
```
  ✓ UI/UX parity with OpenCode (Phase 0 — all U atoms DONE)
  ✓ compileKotlin passes
  ✓ Provider context envelope injected into every provider call
  ✓ /agent self-host start → setDag → selectNext → evaluate → loop works
  ✓ Provider attestation verified on response
  ✓ TypedContextFailure persisted on attestation failure
  ✓ Short-input "ATROPOS" reports state (not mythology)
  ✓ Failure falls back to local with retry
  ✓ Experience recorded after each DAG node
  ✓ VerifiedCompletionGate blocks false completions
  ✓ SafeJarSwapGate preserves old jar on failure

STAGE 1 COMPLETE MARKER: ATROPOS_SELF_BUILDABLE: VERIFIED
```
---

## Stage 2 — Source Docs 1-2 Complete

All 94 SpecGraph atoms + ~10 new provider-context atoms. Every atom DONE:
implemented, wired, reachable, tested, verified, documented, source-aligned.

### Phase 2.1 — Source Authority (A)

```
A001_source_document_registry ─┐
A002_source_section_address ───┤
A003_dloi_router ──────────────┤── A004_HIGZeroGuard ── A005_source_doc_to_code_trace
A005_source_doc_to_code_trace ─┘
  └── Every implemented feature atom traces to source section
```

### Phase 2.2 — AST & Symbol Graph (B)

```
B001_ast_symbol_graph
  └── B002_tree_sitter_grammar_bridge (real AST extraction, not name stub)
  └── B003_ast_namespace_reconciler (deterministic import resolution)
  └── B004_symbol_census_command (flat symbol report)
```

### Phase 2.3 — Remaining Local Runtime (C)

```
C001–C005 (complete from Stage 1, harden and test)
```

### Phase 2.4 — Remaining Verification (D)

```
D001–D005 (complete from Stage 1, add edge case tests)
```

### Phase 2.5 — Dopamine Loop (E)

```
E001_self_improving_compile_loop ─── E002_reward_penalty_store
  └── E003_error_gradient_extractor (complete)
  └── success +1, failure -1, survives restart
```

### Phase 2.6 — Lakehouse / Memory (F)

```
F001_ontological_address_router ─── F003_local_memory_store
  ├── F002_cloud_lakehouse_sync_engine (lazy delta sync)
  ├── F004_sqlite_vec_integration (complete)
  ├── F005_chunking_1024_overlap (complete)
  └── F006_drive_export_upload (OAuth, secrets excluded)
```

### Phase 2.7 — Provider Grid (G)

```
G001–G005 (complete from Stage 1)
  └── G006_provider_fixture_matrix (every provider: success/error/malformed/
       empty/timeout/redaction fixture)
  └── G007_provider_live_opt_in (ATROPOS_LIVE_PROVIDER_TESTS=1)
```

### Phase 2.8 — Quota / Route / Policy (H)

```
H001–H007 (complete from Stage 1)
  └── H006_fallback_chains: CHAT, CODE, REPAIR, PLANNING, DOCS chains fully wired
  └── H007_queue_when_free_unavailable: degrades gracefully
```

### Phase 2.9 — Status & Observability (I)

```
I001_status_command ─── I005_terminal_width_snapshots (40/80/120 — test)
  ├── I002_status_quota
  ├── I003_status_route (explains selected + skipped with reasons)
  └── I004_status_failures (redacted only)
```

### Phase 2.10 — Agent / App Factory (J)

```
J001–J011 (complete from Stage 1)
  └── J007_app_factory_router: prompt→plan→code→validate→repair→package pipeline
  └── J008_endpoint_registry + J009_endpoint_manifest
  └── J010_director_orchestrator + J011_worker_code_synthesizer
```

### Phase 2.11 — Shell / CLI (K)

```
K001_command_registry ─── K006_raw_key_reader (complete from Phase 0)
K002_help_from_registry ─── K007_command_history (complete from Phase 0)
K003_slash_command_integrity ─── K008_dashboard_tabs_screens (complete from Phase 0)
K004_shell_bridge ─── K009_responsive_termux_dashboard (complete from Phase 0)
K005_shell_allowlist_timeout_cwd_redaction
```

### Phase 2.12 — Security / Redaction (L)

```
L001–L005 (complete from Stage 1, add L005_secret_diff_check)
```

### Phase 2.13 — Build / CI / Packaging (M)

```
M001_gradle_wrapper_pinned ─── M004_github_actions_clean_runner
M002_gradle_properties_control_plane
M003_kotlin_compat_scan
M005_safe_jar_packaging (jar build/swap only after gates)
M006_docker_native_desktop_android_web_plan
```

### Phase 2.14 — Test / Acceptance (N)

```
N001_provider_tests ─── N003_source_authority_tests
N002_terminal_tests (40/80/120, NO_COLOR, TERM=dumb, headless)
N004_endpoint_parity_tests
  └── N005_final_acceptance_command (one report for SD1-2)
```

### Phase 2.15 — Boilerplate / Stub Debt (O)

```
O001–O010: all MUST_AUDIT items resolved:
  └── Empty files: implement, remove, or mark future
  └── Disconnected frontend: wire or remove
  └── Swarm stubs: implement or hide
  └── Router concern mixing: split oversized files
  └── Provider transport normalization: split from normalizer
  └── Probabilistic immunity: split stderr parsing/scoring/verification
  └── Manual JSON: replace or test
  └── Fake commands: implement or hide
  └── Placeholder green: no TODO/NotImplemented in production
  └── Missing tests: every feature atom has behavioral tests
```

### Phase 2.16 — Final Acceptance (P)

```
P001_final_sd1_sd2_acceptance
  └── All 94 atoms DONE or intentionally deferred
  └── compileKotlin clean
  └── All tests pass
  └── No secrets in diff
  └── Status commands report correctly
  └── UI/UX parity verified by side-by-side comparison
  └── Output: ATROPOS_SD1_SD2_FINAL_ACCEPTANCE_REPORT.md
```

### Stage 2 Acceptance Gate
```
STAGE 2 COMPLETE MARKER: ATROPOS_SD1_SD2_COMPLETE: VERIFIED
```
---

## Stage 3 — Phase 20+ (All Source Documents Complete)

### Phase 3.1 — Source Document 3 (~50-80 atoms)

```
SD3_ATROPOS_SPEC
  ├── Extended provider specifications (pricing, rate limits, model tables)
  ├── Advanced routing policies (cost-aware, latency-aware, capability-aware)
  ├── Comprehensive test framework (fixture-driven, property-based)
  ├── Multi-model orchestration (ensemble, cascade, fallback with scoring)
  ├── Provider health monitoring (probes, circuit breakers, cooldown tuning)
  └── Usage analytics and cost tracking (per-provider, per-session, per-goal)
```

### Phase 3.2 — Phase 12: Director Advisory Mode

```
P12_DIRECTOR_ADVISORY
  ├── Director visibility layer (see all DAG nodes, providers, queues)
  ├── Advisory drift detection (compare expected vs actual behavior)
  ├── Deviation scoring (numeric drift score per node)
  ├── Policy-rule mismatch reporting
  └── Human-readable advisory output
```

### Phase 3.3 — Phase 13: Territory Enforcement

```
P13_TERRITORY_ENFORCEMENT
  ├── Worktree scope determinism (absolute path checks)
  ├── File mutation boundary checks (pre-apply verification)
  ├── Cross-territory violation detection (alerts on scope breach)
  ├── Territory policy DSL (expressive path rules)
  └── Staging area per territory (isolated worktrees)
```

### Phase 3.4 — Phase 14: HR Router

```
P14_HR_ROUTER
  ├── Cross-boundary information flow control (need-to-know basis)
  ├── Role-based context filtering (manager sees more than worker)
  ├── Information classification levels (public/internal/secret/restricted)
  ├── Need-to-know attestation (provider must justify information request)
  └── Audit trail for every information access
```

### Phase 3.5 — Phase 15: Auditor & Custodian

```
P15_AUDITOR_CUSTODIAN
  ├── Independent verification service (separate process/runtime)
  ├── Deterministic cleanup operations (garbage collection, compaction)
  ├── Tamper-evident logs (chained hashes)
  ├── Evidence preservation (immutable audit records)
  ├── Retention policy engine (TTL per record type)
  └── Forensic reconstruction (rebuild state from audit trail)
```

### Phase 3.6 — Phase 16: Manager/Specialist/Worker Hierarchy

```
P16_HIERARCHY
  ├── Manager role: delegation, oversight, result aggregation
  ├── Specialist role: expertise-based task routing
  ├── Worker role: bounded execution within territory
  ├── Escalation paths: worker→specialist→manager with timeouts
  ├── Task breakdown: manager splits goals into sub-tasks
  └── Result aggregation: manager collects and verifies sub-results
```

### Phase 3.7 — Phase 17: Multimodal Runtime

```
P17_MULTIMODAL
  ├── Screenshot capture and inspection (Compose Desktop)
  ├── Snapshot comparison (diff two terminal states)
  ├── Visual diff detection (pixel-level comparison)
  ├── Image attachment handling (upload to provider)
  ├── Terminal recording playback (asciinema-style replay)
  └── UI state capture and restore (persist visual state)
```

### Phase 3.8 — Phase 18: Multiplatform Expansion

```
P18_MULTIPLATFORM
  ├── Portable core extraction (separate platform-agnostic kernel)
  ├── Compose Desktop frontend (Kotlin + Compose for Desktop)
  ├── Android runtime shell (Termux-native Android app)
  ├── Web browser target (Kotlin/JS or Kotlin/Wasm)
  ├── JVM server deployment (headless server mode)
  ├── Containerized runner (Docker image)
  └── CI/CD matrix across all targets (GitHub Actions matrix build)
```

### Phase 3.9 — Phase 19: App Factory

```
P19_APP_FACTORY
  ├── Request→plan→code→verify→artifact pipeline
  ├── Screenshot or run proof before acceptance
  ├── Artifact generation (jar, apk, binary, container)
  ├── Install proofs (artifact installs and runs correctly)
  ├── Commit-ready output (staged, message, ready for review)
  ├── Full lifecycle: prompt → acceptance with evidence
  └── Factory dashboard: live view of pipeline stages
```

### Phase 3.10 — Phase 20: Full Autonomy

```
P20_FULL_AUTONOMY
  ├── Self-improving policy refinement (learn from experience)
  ├── Goal self-generation from source docs (read→atomize→DAG)
  ├── Automatic DAG construction from requirements (dependency resolution)
  ├── Self-healing on failure (detect→diagnose→repair automatically)
  ├── Resource-aware scheduling (CPU/memory/network-aware)
  ├── Priority-based task selection (urgency/importance scoring)
  ├── Experience consolidation across sessions (cross-session learning)
  ├── Automatic provider selection optimization (cost+latency+quality)
  └── Full inside-out loop:
        Read source doc → atomize requirements →
        build DAG → assign hierarchy → attest providers →
        execute → verify → promote → record experience → repeat
```

### Phase 3.11 — Provider Integrations (~20-30 additional providers)

```
PROVIDER_INTEGRATIONS
  ├── GitHub API (issues, PRs, repos, Actions, releases)
  ├── GitLab API (repos, MRs, pipelines)
  ├── Supabase (db, auth, storage, realtime, edge functions)
  ├── Vercel (deployments, env vars, logs, domains)
  ├── Google AI (Gemini Pro, Gemini Ultra, embeddings)
  ├── Google Cloud (Vertex AI, Cloud Storage, Cloud Run)
  ├── Google Drive (export/import artifacts, OAuth)
  ├── HuggingFace Inference API (thousands of models)
  ├── Replicate (model hosting and inference)
  ├── Cohere (generation, embedding, classification, rerank)
  ├── Together AI (open-source models, fast inference)
  ├── DeepInfra (serverless inference, many model families)
  ├── Fireworks AI (optimized open models)
  ├── Anthropic (Claude Opus, Sonnet, Haiku)
  ├── OpenAI (GPT-4o, GPT-4, o1, o3, embeddings, DALL-E)
  ├── xAI (Grok models)
  ├── MCP client adapter (generic Model Context Protocol, thousands of MCP servers)
  ├── Ollama local models (complete from Stage 1)
  ├── DeepSeek (deepseek-reasoner, deepseek-chat)
  └── Custom user API slot (arbitrary OpenAI-compatible endpoint)
```

### Phase 3.12 — MCP Server Implementation

```
MCP_SERVER
  ├── Model Context Protocol server (ATROPOS as MCP host)
  ├── Tool registration and discovery (expose ATROPOS tools to MCP clients)
  ├── Resource templates (expose ATROPOS state as resources)
  ├── Prompt templates (expose ATROPOS prompt templates)
  ├── Sampling support (let MCP clients request LLM sampling)
  ├── Transports: stdio, SSE, WebSocket
  ├── Authentication extensions (OAuth, API key, token)
  └── Dual mode: ATROPOS is both MCP host AND MCP server simultaneously
```

### Phase 3.13 — UI/UX Final Polish

```
UI_UX_FINAL
  └── All U001–U016 atoms from Phase 0 are already DONE
  └── Additional polish for new Stage 3 features:
  ├── Provider connection panel shows all 30 providers
  ├── DAG visualization (tree view in dashboard)
  ├── Goal timeline (history of all goal runs)
  ├── Experience browser (search/browse past experiences)
  ├── Policy editor (interactive policy configuration)
  └── Theme gallery (preview and switch themes)
```

### Stage 3 Acceptance Gate
```
  ✓ SD3 all atoms DONE
  ✓ Phase 12-16 complete (Director, Territory, HR, Auditor, Hierarchy)
  ✓ Phase 17 complete (multimodal — screenshot, vision, recording)
  ✓ Phase 18 complete (multiplatform — desktop, android, web, server)
  ✓ Phase 19 complete (app factory — prompt→artifact with proof)
  ✓ Phase 20 complete (full autonomy — self-building without human)
  ✓ 30 provider slots filled and tested
  ✓ MCP server running and accepting connections
  ✓ UI/UX parity with OpenCode verified by side-by-side comparison
  ✓ ATROPOS can build itself from source docs without human intervention
  ✓ ATROPOS can read any new source doc, atomize it, build a DAG, execute it

STAGE 3 COMPLETE MARKER: ATROPOS_PHASE_20_COMPLETE: VERIFIED
```

## Summary

| Stage | Atoms | Key Characteristic |
|-------|-------|-------------------|
| Phase 0 — UI/UX Parity | 16 atoms (U001–U016) | Immediate: ATROPOS looks and behaves EXACTLY like OpenCode CLI |
| Stage 1 — Self-Buildable | ~40 atoms | ATROPOS can build itself autonomously with full UI/UX parity |
| Stage 2 — SD1-2 Complete | ~104 atoms | All 94 SpecGraph atoms + provider context atoms DONE |
| Stage 3 — Phase 20+ | ~300-500 atoms | All source docs, integrations, MCP, autonomy |

**Critical path insight: Phase 0 (UI/UX) gates everything.** The system is
immediately usable and recognizable from the first commit. Every subsequent
stage benefits from having a terminal interface that behaves exactly like
OpenCode — the same key bindings, the same visual feedback, the same
interaction patterns that OpenCode users already know.
