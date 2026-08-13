# ATROPOS — Unimplemented Inventory (numbered, supersession-applied)

**Audited at:** commit `877230d` · 2026-08-13
**Supersedes:** `docs/GAP_AUDIT_2026-08-13.md` (kept for its evidence and file paths)
**Tree measured:** 771 main Kotlin files · 306 test files · 202 registered commands

## Status marks

| Mark | Meaning |
|---|---|
| `DEFECT` | Claimed working; observed failing on the installed runtime |
| `ABSENT` | No code |
| `ORPHAN` | Code + tests exist, zero production callers |
| `PARTIAL` | Exists, contract not met |
| `UNENFORCED` | Exists and wired, but default-off or advisory |
| `DONE` | Verified present and reachable |

## Supersession rule applied in this pass

The previous list audited Source Doc 1's lakehouse and addressing design as
missing. It is not missing — it was **replaced**, and the replacement is locked
authority. The Phase 20 gap map §0 states it directly:

> Lakehouse substrate (locked): Cloudflare R2 primary store · CAS key = SHA-256
> full digest · structural manifest … local DLOI → CAS → location index

Two pieces of evidence in the tree confirm the boundary:

1. There are **two** `DloiCoordinate` types. `atropos.dloi.DloiCoordinate` is
   `documentId / sectionId / lineStart / lineEnd` and is used by the whole
   engine. `atropos.data.lakehouse.DloiCoordinate` is `domain / category / leaf`
   integers — Source Doc 1's 9-digit scheme — and has **zero callers**.
2. `DeterministicChecks.checkDloiAddress` remediates with *"use a provable
   `document#section@Lstart-end` address"*. That is the live address form.

Everything downstream of the numeric band scheme is therefore superseded, and is
listed in §0 as **do not implement** rather than counted as a gap. Items marked
`CONFIRM` are boundary cases where I could not resolve supersession from the tree
alone.

---

# 0. SUPERSEDED — do not implement

These appeared as gaps in the previous list and should be struck. Each is
replaced by a later locked design.

| # | Superseded requirement | Source | Replaced by |
|---|---|---|---|
| S1 | 3-digit numeric DLOI band scheme (000–019, 400–439, 510–529, 620–629) | SD1 §.001 | `document#section@Lstart-end` + human taxonomy path tags (`E/networking/http`) |
| S2 | CORPUS→DLOI table (`ATROPOS_200_git_part01`, `ATROPOS_610_roadmap`, …) | SD1 §.001 | R2 CAS objects addressed by `index/paths.txt` |
| S3 | Bundle resolution (`B1_devtools`, `B2_ai_stack` → multiple domains) | SD1 §.001 | Exact-path retrieval, one path one object |
| S4 | Routing protocol via DOMAIN codes | SD1 §.001 | Keyword → path-tag match against `paths.txt`, exact-path fetch |
| S5 | Reserved coordinates `420`, `621` | SD1 §.001 | — |
| S6 | `UnixDomainSocketBridge` (`420.011`) | SD1 §.001 | An illustration of S1, not a component |
| S7 | `ATROPOS_MAP_2_DLOI_COORDINATES` | SD1 §.001 | `index/paths.txt` |
| S8 | `ATROPOS_HASH_INDEX.txt` | SD1 §.001 | `index/objects.tsv` (hash ↔ path) |
| S9 | `ATROPOS_AGENT_PLAYBOOK` | SD1 §.001 | `AGENTS.md` |
| S10 | `dloi_address` as a 9-digit taxonomic coordinate in `ast_symbol_graph` | SD1 §1.0.1 | The live coordinate form — see item 12 |
| S11 | Memory-mapped I/O (mmap) via Android NDK | SD1 §1.0.1 | rclone → local CAS at `~/.atropos/lakehouse` |
| S12 | Shadow-log replication of quantized embedding shards | SD1 §1.0.1 | CAS delta sync by hash |
| S13 | 5 TB global knowledge matrix | SD1 §1.0.1 | 5 GB target ≈ 900 M high-signal words (P20 §0) |
| S14 | Google Drive / GCS / Firebase / Supabase as the lakehouse store | SD1 §.100, §.500, §1.0.2 §4 | Cloudflare R2 |
| S15 | `gdrive_upload.py` OAuth runbook | SD1 §.500 | rclone to R2 |
| S16 | `DirectorOrchestrator` / `WorkerCodeSynthesizer` as named swarm nodes | SD1 §1.0.1 | Blueprint §4.5 explicitly retires them; Phase 16 hierarchy replaces |
| S17 | `OntologicalAddressRouter` itself (29 L, zero callers) | SD1 §.001 | Dead code — **should be deleted**, not completed |

**Struck from the count: 17 items.**

---

# 1. LIVE DEFECTS

| # | Status | Item |
|---|---|---|
| 1 | `DONE` | ~~`LocalMemoryStore.search` StackOverflowError~~ — fixed `877230d`; nine sites replaced by `JsonStringField` |
| 2 | `DEFECT` | Lakehouse per-atom retrieval returns `MISS` on every path |
| 3 | `DEFECT` | DLOI returns `no_exact_match` on every real prompt |
| 4 | `DEFECT` | `provider_suggestions` skipped whenever local confidence is high |
| 5 | `DEFECT` | `confidence=100` printed while 5 of 6 research channels soft-failed — confidence does not read channel health |

# 2. SOURCE DOC 1 — surviving requirements only

**Code intelligence (§1.0.1)**

| # | Status | Item |
|---|---|---|
| 6 | `ABSENT` | `ast_symbol_graph` persistent table: `node_id`, address, `symbol_type`, `file_path`, `byte_offset_start`, `byte_offset_end`, `dependency_refs` |
| 7 | `ABSENT` | Index on the address column |
| 8 | `ABSENT` | AST namespace reconciler — inject absolute import paths from the index instead of guessing |
| 9 | `DONE` | HIG = 0 / no cosine-RAG fallback — `HigZeroGuard` implements it |

**Mathematical execution constraints (§1.0.1)**

| # | Status | Item |
|---|---|---|
| 10 | `ABSENT` | State-Aligned MDP against a non-differentiable compiler |
| 11 | `ABSENT` | Topological mutation vectors `Δt` — modifications as graph deltas, not strings |
| 12 | `PARTIAL` | TED context stripping (~94.2% prompt-weight saving) — `CodebaseDeltaTreeTracker` 274 L, not on the prompt path, no measured saving |
| 13 | `PARTIAL` | `E(Δ) = HIG + HUD = 0` as a commit precondition — `BatchGate` covers territory only |
| 14 | `ABSENT` | Error-gradient extraction — slice the failing sub-graph, route only the broken signature + exact stderr coordinate |
| 15 | `ABSENT` | Sequential Monte Carlo program sampling / branch pruning on compiler-log interception |
| 16 | `ABSENT` | Decomposed attention — "Viewer Node" vs "Editor Node" separation |

**Delegation (§1.0.2)** — `CONFIRM`: the named engine bindings (Grok=Director, Groq=Worker, Ollama=Validator) may be superseded by Phase 16 hierarchy along with S16. The *capabilities* below are listed because nothing currently provides them.

| # | Status | Item |
|---|---|---|
| 17 | `ABSENT` | On-device adversarial validator returning `Boolean(Syntax_Valid) + Missing_Imports[]` **before cloud tokens are spent** |
| 18 | `ABSENT` | Async fan-out/fan-in decoupling to bypass context dilution |
| 19 | `ABSENT` | Orchestrator emitting a JSON manifest of execution nodes ordered by topological prerequisites |

**Dopamine circuit (§1.0.1–1.0.3, restated SD3 Part C §7)**

| # | Status | Item |
|---|---|---|
| 20 | `PARTIAL` | Reward signal — `RewardPenaltyStore` (81 L) writes a TSV of `agentId/action/value/reason`; the specified `RewardVector(score, trace)` carrying the **stderr trace** does not exist |
| 21 | `ABSENT` | `Reward = SuccessRate / (Latency × Cost)` |
| 22 | `ABSENT` | Non-blocking write path for the reward log |
| 23 | `ABSENT` | Closed-loop alignment — auto-tune prompt prefix, temperature, top_p, few-shot from rolling success rate |

**CAS (§1.0.1)**

| # | Status | Item |
|---|---|---|
| 24 | `ABSENT` | Global dedupe of identical dependencies across projects |

**AGPL perimeter (§1.0.1)**

| # | Status | Item |
|---|---|---|
| 25 | `ABSENT` | Cloud-loophole closure — architecture evaluates its own deployment for UI-stripped remote hosting |
| 26 | `ABSENT` | Native shell intercept — bare `git`, `cd`, `gh` (only `/git`, `/cd` slash forms exist) |
| 27 | `ABSENT` | Piped input (`\|`) and async process streams in the router |

**Multi-platform (§.005, restated Blueprint Phase 18)**

| # | Status | Item |
|---|---|---|
| 28 | `ABSENT` | KMP `core` module — `kotlin("multiplatform")` absent from the build |
| 29 | `PARTIAL` | Module split `core/ cli/ desktop/ androidApp/ server/ shared-ui/` — only `app/` (Android) exists |
| 30 | `ABSENT` | Compose Desktop |
| 31 | `ABSENT` | GraalVM Native Image |
| 32 | `ABSENT` | Dockerfile (JVM + native, health checks, graceful shutdown) |
| 33 | `ABSENT` | Ktor backend |
| 34 | `ABSENT` | iOS / Compose for iOS |
| 35 | `ABSENT` | `LocalToolchain` / `Renderer` / `InputSystem` platform interfaces |
| 36 | `ABSENT` | Installers (.deb, Homebrew, Scoop) |

# 3. SOURCE DOC 2

| # | Status | Item |
|---|---|---|
| 37 | `ABSENT` | `LOCAL_TOOLCHAIN` as provider id 0 — **roots 9 of 11 fallback chains and is not a registered provider** |
| 38 | `ABSENT` | `CUSTOM_USER_API` (id 29) adapter slot |
| 39 | `ABSENT` | OpenRouter free-model rotation when rate-limited |
| 40 | `ABSENT` | `quota_weight ASC` as primary sort key |
| 41 | `ABSENT` | Task routing matrix as data (14 tasks × 6 columns) |
| 42 | `ABSENT` | `CHAT_CHAIN` as a named, inspectable chain |
| 43 | `ABSENT` | `CODE_CHAIN` |
| 44 | `ABSENT` | `REPAIR_CHAIN` |
| 45 | `ABSENT` | `PLANNING_CHAIN` |
| 46 | `ABSENT` | `DOCS_CHAIN` |
| 47 | `ABSENT` | `SEARCH_CHAIN` |
| 48 | `ABSENT` | `EMBED_CHAIN` |
| 49 | `ABSENT` | `MEMORY_CHAIN` |
| 50 | `ABSENT` | `SECRET_CHAIN` |
| 51 | `ABSENT` | `EDGE_CHAIN` |
| 52 | `ABSENT` | `ASSET_CHAIN` |
| 53 | `PARTIAL` | Eligibility algorithm — missing `remaining_estimate`, `recent_success_score`, `latency_estimate`, `cooldown_risk`, `model_missing` |
| 54 | `PARTIAL` | Typed failure states — `exhausted_until_reset` + `reset_at`, `billing_required`, `auth_failed`, model-missing → alternate model |
| 55 | `PARTIAL` | Rule 23 — `Clock` interface (one usage, not systemic) |
| 56 | `ABSENT` | Rule 121 — every assertion names invariant + observed value; bare `check()` prohibited |
| 57 | `ABSENT` | Rule 124 — output passes `{interactive-color, NO_COLOR, TERM=dumb, headless}` |
| 58 | `PARTIAL` | Rule 127 — snapshots as raw bytes **and** ANSI-stripped at 40/80/120 (stripped only) |
| 59 | `PARTIAL` | Rule 129 — compile to temp dir, move into place only after every gate |
| 60 | `ABSENT` | Rule 131 — batches report physical lines, code-bearing lines, additions, deletions |
| 61 | `ABSENT` | Rule 134 — pre-batch scan for risky stdlib calls (`Sequence.takeLast`, `kotlin.io.path`, kotlinx, Java above target) |
| 62 | `PARTIAL` | Rule 137 — `SUCCESS=1` only after kotlinc 0 + smoke 0 + grep truth + `git diff --check` 0 + jar swap |
| 63 | `ABSENT` | Rules 142–147 — auto-emit next minimal context-export command, export to phone Downloads, `termux-media-scan` |

# 4. SOURCE DOC 3 — evaluation (§4)

| # | Status | Item |
|---|---|---|
| 64 | `ABSENT` | `AtroposMetrics` (one file per metric family) |
| 65 | `ABSENT` | `MetricCalculator` |
| 66 | `ABSENT` | `BenchmarkRunner` |
| 67 | `ABSENT` | `EvidenceStore` |
| 68 | `ABSENT` | `ClassificationCalculator` |
| 69 | `ABSENT` | Restart recovery success **rate** |
| 70 | `ABSENT` | Verifier-first catches before LLM escalation |
| 71 | `ABSENT` | Coordination efficiency (tokens per verified change) |
| 72 | `ABSENT` | Territory safety **percentage** (only a boolean exists) |
| 73 | `ABSENT` | Identity recognition accuracy |
| 74 | `ABSENT` | Context attestation success rate |
| 75 | `ABSENT` | Drift detection latency |
| 76 | `ABSENT` | Trace completeness |
| 77 | `ABSENT` | Copy fidelity |
| 78 | `ABSENT` | Preview success rate |
| 79 | `ABSENT` | Event determinism |
| 80 | `ABSENT` | Repair quality (permanent vs recurring) |
| 81 | `ABSENT` | Batch completion rate / rollback frequency |
| 82 | `ABSENT` | Provider route effectiveness over time |
| 83 | `ABSENT` | Five-way release classification (score reduction / minimum / competitive / frontier / safety hard) |
| 84 | `ABSENT` | Zero-target division fix in lower-is-better metrics |
| 85 | `ABSENT` | SWE-bench Verified |
| 86 | `ABSENT` | Terminal-Bench |
| 87 | `ABSENT` | Aider Polyglot |
| 88 | `ABSENT` | PR acceptance / time-to-accepted-PR |
| 89 | `ABSENT` | Evaluation CLI + dashboard |

# 5. SOURCE DOC 3 — observability (§5)

| # | Status | Item |
|---|---|---|
| 90 | `ABSENT` | `ExecutionEvent` |
| 91 | `ABSENT` | `ProvenanceStream` |
| 92 | `ABSENT` | `EventPublisher` |
| 93 | `ABSENT` | `EventSubscriber` |
| 94 | `ABSENT` | `OutputCard` |
| 95 | `ABSENT` | `CardRenderer` |
| 96 | `ABSENT` | `MarkdownExporter` (full-run export) |
| 97 | `ABSENT` | `JsonExporter` |
| 98 | `ABSENT` | `ExecutionHistoryStore` |
| 99 | `ABSENT` | `HistoryQuery` |
| 100 | `ABSENT` | `HistoryIndex` |

# 6. SOURCE DOC 3 — intent layer (§6)

| # | Status | Item |
|---|---|---|
| 101 | `ABSENT` | `ActionRegistry` |
| 102 | `ABSENT` | `AliasResolver` |
| 103 | `ABSENT` | `CommandConsolidator` |
| 104 | `ABSENT` | `CommandMetadata` (central structured metadata) |
| 105 | `ABSENT` | 13 canonical verbs as the contract surface |
| 106 | `ABSENT` | Natural-language phrase mappings (item 51) |
| 107 | `ABSENT` | Inline argument guidance (item 50) |

# 7. SOURCE DOC 3 — 74-item list, remainder

| # | Status | Item |
|---|---|---|
| 108 | `ABSENT` | Tab restoration across restart (items 24–26) |
| 109 | `ABSENT` | Responsive branding — logo never clipped incl. the cut-off "POS" (item 28) |
| 110 | `ABSENT` | No-fake-progress guard — animation bound to persisted state (items 32–33) |
| 111 | `ABSENT` | Background-process panel (item 34) |
| 112 | `ABSENT` | `stalled` state (item 35) |
| 113 | `ABSENT` | Touch autocomplete (item 44) |
| 114 | `ABSENT` | Safe fuzzy execution — confirmation gate (item 46) |
| 115 | `ABSENT` | Context-sensitive suggestions (item 47) |
| 116 | `ABSENT` | Screen-reader labels (item 68) |
| 117 | `ABSENT` | Reduced-motion support (item 68) |
| 118 | `ABSENT` | High-contrast theme (item 68) |
| 119 | `ABSENT` | Focus visibility (item 68) |
| 120 | `ABSENT` | Virtualized long logs (item 69) |
| 121 | `ABSENT` | Bounded rendering / controlled background updates (item 69) |
| 122 | `ABSENT` | Canonical acceptance test suite (item 71) |

# 8. SOURCE DOC 3 — Sections B/D/E

| # | Status | Item |
|---|---|---|
| 123 | `ABSENT` | `contract/` module — `ui-` depends only on `contract/` |
| 124 | `ABSENT` | MVI per screen (Intent → Reducer → State, `StateFlow`) |
| 125 | `ABSENT` | Local HTTP+SSE server on `127.0.0.1` with optional password, one route per operation generated from `OperationRegistry` |
| 126 | `ABSENT` | GOVERNANCE view (6 tabs + cross-tab alert rail) |
| 127 | `ABSENT` | PIPELINE/ARTIFACTS view (Guided/Manual, 6 stage cards) |
| 128 | `ABSENT` | DAG view (force-directed canvas, cycle outline) |
| 129 | `ABSENT` | SNAPSHOTS view (gallery + compare) |
| 130 | `ABSENT` | SECURITY view (Keys/Redaction/Vault, paste-in redaction preview) |
| 131 | `ABSENT` | MEMORY view |
| 132 | `ABSENT` | PAID/EMERGENCY view (lift-cover metaphor, app-wide danger banner) |
| 133 | `ABSENT` | VERIFY view (Narrow/Wide toggle, pass/fail matrix) |
| 134 | `ABSENT` | SOURCE LOOKUP view (auto-typed results) |
| 135 | `ABSENT` | AUTONOMOUS view ("who's driving" indicator) |
| 136 | `ABSENT` | SWARM view (honest not-yet-available card) |
| 137 | `ABSENT` | JOBS/QUEUE view (unified CI + agent queue) |
| 138 | `ABSENT` | PLATFORM view |
| 139 | `ABSENT` | Corner-radius concentricity token (parent-radius-minus-inset) |
| 140 | `ABSENT` | 44×44pt tap targets |
| 141 | `ABSENT` | Tinted theme variant |
| 142 | `ABSENT` | Acceptance test — view buttons match contract-layer valid verbs exactly |
| 143 | `ABSENT` | Acceptance test — no view shows more than 13 verbs (build-time static scan) |
| 144 | `ABSENT` | Acceptance test — every stream element collapsed on first render; expansion does not persist |
| 145 | `ABSENT` | Acceptance test — status vocabulary identical across all 16 views + CLI |
| 146 | `ABSENT` | Acceptance test — new `OperationEndpoint` with `configured=true` produces a Home card + reachable view with **zero UI code changes** |

# 9. SOURCE DOC 4 / HOE

| # | Status | Item |
|---|---|---|
| 147 | `ABSENT` | `HOE-0003` durable project store |
| 148 | `ABSENT` | `HOE-0011` terminals first-class + evidence-linkable; no PTY over the bridge |
| 149 | `PARTIAL` | `HOE-0016` Android HOE chrome |
| 150 | `ABSENT` | `HOE-0004` colour-independence test — no non-colour channel verification |
| 151 | `ABSENT` | `HOE-0008` L4 Internal / Developer Tools disclosure level |
| 152 | `ABSENT` | Runtime Inspector |
| 153 | `ABSENT` | Agent Inspector |
| 154 | `ABSENT` | Provider Inspector |
| 155 | `ABSENT` | Policy Inspector |
| 156 | `ABSENT` | Source Authority Inspector |
| 157 | `ABSENT` | Recovery Inspector |
| 158 | `ABSENT` | `HOE-B08` copy/download from within the CLI |
| 159 | `ABSENT` | `HOE-B09` / `HOE-E01` territory-as-material |
| 160 | `ABSENT` | `HOE-E02` attestation-as-optical-focus |
| 161 | `ABSENT` | `HOE-E06` recovery tectonic ribbon |
| 162 | `ABSENT` | `HOE-E07` mode retheme from real status |
| 163 | `ABSENT` | `UI-DELTA-WEB-027` bridge endpoints: `POST /session`, `GET /session`, `GET /session/:id`, `POST /message`, `GET /events` (SSE), `POST /approve`, `POST /reject`, `GET /evidence/:id`, `GET /files` |
| 164 | `ABSENT` | `UI-DELTA-WEB-024` multi-view project (Conversation / Timeline / Execution Monitor) |
| 165 | `ABSENT` | `UI-DELTA-WEB-020` `How?` pipeline field |
| 166 | `ABSENT` | `UI-DELTA-WEB-015` approvals never erase history — BLOCKED |
| 167 | `DEFECT` | `UI-DELTA-WEB-003` unrestricted argv passthrough on `POST /cli` — RCE surface |
| 168 | `PARTIAL` | `UI-DELTA-WEB-016` / `-022` project ownership + creation affordance |
| 169 | `ABSENT` | `UI-DELTA-CLI-009` corrupt queue entry surfaces as a fault |
| 170 | `ABSENT` | `UI-DELTA-AND-001` Android HOE shell |
| 171 | `ABSENT` | **`apps/atropos-web/` does not exist** — every `HOE-C` atom points at a directory not in the tree |
| 172 | `ABSENT` | Desktop surface |

# 10. PHASE 20

| # | Status | Item |
|---|---|---|
| 173 | `ABSENT` | `SelfImprovementLoop` |
| 174 | `ABSENT` | `AutonomousBacklogManager` (restart-safe) |
| 175 | `ABSENT` | `PolicyGate` |
| 176–195 | `ABSENT` | Laws 20.1 – 20.20 as enforced predicates (20 items) |
| 196–210 | `ABSENT` | Loop transitions `P20-L01 … L15` — **the chain is never assembled** (15 items) |
| 211–217 | `ABSENT` | Lakehouse ledgers `P20-LH01 … LH07` — evidence / memory / proposal / amendment as CAS objects with structural manifests (7 items) |
| 218–226 | `ABSENT` | Governance candidates `P20-G01 … G09` (9 items) |
| 227–232 | `ABSENT` | Hard boundaries `P20-H01 … H06` — rate/depth/budget, cooldown, observation period, quarantine, termination ranking (6 items) |
| 233–239 | `ABSENT` | Superiority primitives `P20-NS01 … NS07` — proof-carrying amendments, formal `R(d)`, metric-space `I(p)`, termination ranking, object/meta separation, proposal lattice, unified CAS substrate (7 items) |
| 240–243 | `ABSENT` | Continuous superiority `P20-S01 … S04` (4 items) |
| 244 | `ABSENT` | First canonical amendment — *nonzero compile/test exit forbids VERIFIED* |

# 11. PHASE 19 — APP FACTORY

| # | Status | Item |
|---|---|---|
| 245 | `ABSENT` | `DeploymentService` |
| 246 | `ABSENT` | Hosting / preview / live environments |
| 247 | `ABSENT` | Domains + HTTPS |
| 248 | `ABSENT` | Release rollback |
| 249 | `ABSENT` | `RepositoryBinding` as its own owner |
| 250 | `ABSENT` | Scheduled / background tasks, realtime behaviour |
| 251 | `ABSENT` | Activity monitor unifying plan / provider / tool / diff / test / verifier / artifact / deploy |

# 12. ORPHANED — code and tests exist, zero production callers

| # | Status | Item |
|---|---|---|
| 252 | `ORPHAN` | `LivePreviewService` — `hotReload()` defined, never invoked |
| 253 | `ORPHAN` | `VisualComparison` — no caller, no test |
| 254 | `ORPHAN` | `EvidenceCollector` — the Blueprint's evidence unifier; nothing constructs it |
| 255 | `ORPHAN` | `McpTerritoryBridge` — no server registers it |
| 256 | `ORPHAN` | `CapabilityEnforcer` — one caller, **zero tests** |
| 257 | — | `OntologicalAddressRouter` — dead; delete per S17 |

# 13. UNENFORCED

| # | Status | Item |
|---|---|---|
| 258 | `UNENFORCED` | `ArchitectureComplianceChecker` — `enforcing = false` by default |
| 259 | `UNENFORCED` | 55 files over 250 lines, 4 over 400 (`CommandRouter` 413) |
| 260 | `UNENFORCED` | Phase 20 governance reachable only via bridge projection, no CLI |
| 261 | `ABSENT` | `SourceDocumentRegistry` — named in Blueprint Phase 6 |

# 14. SUPERIORITY ADDENDUM

| # | Status | Item |
|---|---|---|
| 262 | `ABSENT` | `AnsiScheme.kt` + "raw escapes forbidden outside scheme" compliance assertion |
| 263 | `ABSENT` | `GlobalByteCeiling` |
| 264 | `ABSENT` | `PathResolver` as named owner + compliance assertion |
| 265 | `ABSENT` | `ComputerUseBridge` |
| 266 | `ABSENT` | `SessionManager` (session/tab density) |
| 267 | `ABSENT` | `RecoveryRibbon` |
| 268 | `ABSENT` | `@mention` file ingestion |
| 269 | `ABSENT` | Territory monitor cost counters; O(N) vs O(N²) documented in evidence bundles |
| 270 | `PARTIAL` | `SurfaceContract` runs on one surface — parity asserted, not tested |

# 15. AGENTS.md

| # | Status | Item |
|---|---|---|
| 271 | `ABSENT` | **`Swarm.md` does not exist** — `SwarmMdLoader` (114 L) can only ever take the refusal path |
| 272 | `ABSENT` | No recomputed current aggregate — "what percent is ATROPOS now" has no answer in the repo |

# 16. ACCEPTANCE PROOFS NEVER RUN

| # | Status | Item |
|---|---|---|
| 273 | `ABSENT` | Self-host proof — interactive JAR mutation + git push |
| 274 | `ABSENT` | Greenfield factory proof — deploy half absent |
| 275 | `ABSENT` | Long-horizon proof |
| 276 | `ABSENT` | Recovery proof |
| 277 | `ABSENT` | Safety proof — no adversarial suite |
| 278 | `ABSENT` | Fallback proof |
| 279 | `ABSENT` | Learning proof |
| 280 | `ABSENT` | Phase 0 — fresh Termux clone and clean CI producing the same JAR hash |

---

# Count

| | |
|---|---:|
| Open items | **279** |
| Closed this session | 1 |
| Struck as superseded | 17 |
| Previous list | 298 |

# Items needing your confirmation

| Ref | Question |
|---|---|
| 17–19 | Does the SD1 delegation topology survive Phase 16's Manager/Specialist/Worker hierarchy, or is it superseded with S16? The **local validator before cloud spend** (17) looks worth keeping regardless. |
| 6–7 | `ast_symbol_graph` keeps its schema but its address column should hold `document#section@Lstart-end`, not a 9-digit coordinate. Confirm. |
| 24 | CAS dedupe — is cross-project dependency dedupe still wanted, or was that tied to the 5 TB design? |

# Suggested order

1. **2, 3** — lakehouse MISS and DLOI no-match. Both are last-hop failures on chains that are otherwise built.
2. **252–256** — wire the five orphans. Already written; converts five line items from false-green to true.
3. **90–100** — observability events. Everything in evaluation reads from this; building evaluation first means building it twice.
4. **64–89** — evaluation metrics, now with a data source.
5. **173–244** — the Phase 20 loop, now with metrics to compute `I(p)`.
6. **163** — web bridge endpoints; unblocks Web and Android together.
7. **258** — flip compliance to blocking *after* 259 is resolved, or the build stops immediately.
