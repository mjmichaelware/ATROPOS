# ATROPOS

**The Sovereign Local-First Deterministic Hierarchical Multi-Swarm Software Development Engine**

![E(Δ)=0 Principle](docs/assets/imported/20260629_190733/image-20.jpg)

**Independent • Runs for days without stopping • No quota collapse • Preventive verification • E(Δ)=0**

ATROPOS is a complete autonomous software-development engine and control plane. It is built on a strict hierarchical territory model with explicit scope boundaries, preventive drift detection, and controlled information flow. This architecture allows it to operate for days on end with no quota collapse, no uncontrolled context growth, and real deterministic verification before any LLM escalation.

Current systems (Codex CLI, Claude Code Agent Teams, Google Antigravity, OpenSwarm, AgentsMesh, and similar orchestrators) all rely on LLM-mediated coordination through task lists, mailboxes, and reviewer agents. This creates structural problems that become severe at scale: token cost grows super-linearly with agent count and duration, drift detection remains reactive and expensive, global visibility stays fragmented, and long-running autonomous work becomes unsustainable.

ATROPOS replaces that model entirely with territory assignment at dispatch time, continuous low-cost Director-level diff monitoring for preventive drift detection, a single auditable HR Router for cross-boundary information, and deterministic verification (Tree-sitter + DLOI) before any model call. Most work stays inside its assigned territory and never touches heavy coordination layers.

---

## Core Hierarchy

![Hierarchy Model](docs/assets/imported/20260629_190733/image-30.jpg)

**Human Owner/CEO**  
Strategic direction, policy definition, and final authority on irreversible actions and risk tolerance. This level is not agentified.

**Director**  
Owns task decomposition, explicit territory assignment at dispatch time, global diff monitoring across all active worktrees, preventive drift detection, escalation handling, and maintenance of the single source of truth for scope and state. Only this layer requires broad visibility.

**VP / Manager**  
VP layers own major capability domains (Code Synthesis, Verification, Research/Ingestion, Assets, CI/Deployment). Managers handle team-level coordination within a domain, assign explicit territories to Specialists and Workers, track progress, and escalate scope or policy issues.

**Specialist**  
Deep domain expertise execution with higher verification requirements and narrower territory defaults. Registered with capability tags for intelligent routing.

**Worker**  
Executes assigned tasks strictly inside the granted territory in its dedicated worktree. Does not require visibility into other agents’ work.

**Custodian**  
Deterministic hygiene layer. Handles temp file removal, dead branch pruning, artifact cleanup, and basic state compaction on fixed schedules or Director triggers with minimal LLM involvement.

**Auditor**  
Independent read-only verification layer with a separate reporting line to prevent execution-layer capture. Performs syntax, structural consistency, and policy compliance checks.

**HR / Information Router**  
The single controlled chokepoint for any cross-territory information request. Applies redaction patterns, scope validation against territory metadata, and risk classification. Approved narrow responses are returned. Denied or high-risk requests are logged and escalated. Most intra-territory work bypasses the router entirely.

This hierarchy keeps coordination cost linear with the number of active territories rather than exploding with agent count or session length.

---

## Quantifiable Advantages Over Current Systems

| Dimension                    | Typical Market Pattern (Codex / Claude Code / Open-Source Orchestrators) | ATROPOS |
|-----------------------------|--------------------------------------------------------------------------|---------|
| Coordination Mechanism      | LLM-mediated chatty (task lists, mailboxes, dynamic spawning)           | Strict hierarchy + territory metadata + Director diff inspection |
| Drift Detection             | Reactive (reviewer agents or test failures after damage)                | Preventive (at dispatch + continuous low-cost monitoring) |
| Token Cost Scaling          | Super-linear with agent count and session duration                      | Linear with number of active territories |
| Global Visibility           | Fragmented across many LLM contexts                                     | Concentrated in narrow, auditable Director component |
| Information Flow            | Uncontrolled mailboxes                                                  | HR Router with policy, redaction, and full audit trail |
| Verification Philosophy     | Post-hoc LLM review or external harness                                 | Deterministic structural checks first + independent Auditor |
| Long-Running Capability     | Quota/context collapse within hours                                     | Designed for days of continuous autonomous operation |
| Self-Improvement Signal     | Diffuse multi-agent chat logs                                           | Clean attribution to specific roles, territories, and decisions |

---

## What ATROPOS Actually Is Right Now

ATROPOS is a local-first, deterministic, multi-provider, multi-agent software-development engine and sovereign CLI/TUI control plane. It is not a chat wrapper. It is not a thin orchestration layer. It owns routing, memory, verification, tools, files, policy, secrets, execution, and acceptance.

The system is built around a strict hierarchical territory model. Work is dispatched with explicit allowed paths (territory). A Director layer continuously monitors diffs from active worktrees against those territories for preventive drift detection. Most agents operate strictly inside their assigned territory and do not need visibility into other agents’ work. Cross-boundary information requests are forced through a single auditable HR Router that applies redaction and scope validation.

ATROPOS already includes:
- A complete provider descriptor registry with 30+ providers
- RoutePolicy and SQLite-backed QuotaLedger for free-first routing that does not collapse
- Central redaction that runs before UI rendering, logs, prompts, diffs, and status output
- Deterministic verification foundation using Tree-sitter and DLOI addressing
- Persistent cognitive memory (batch records, failure signatures, DLOI-linked notes)
- Reactive Termux-native TUI with persistent tabs, command palette, history navigation, and responsive layout that reflows on pinch-zoom

All development follows a strict nano-style coherent batch discipline with E(Δ)=0 gates. Every batch must pass narrow compile + smoke before widening. Context is exported after every successful gated pass.

---

## Full Provider Registry (30+ Agents & Capabilities)

ATROPOS maintains a validated registry of over 30 providers used across different task types:

**Free / Cooldown Eligible (Strongly Preferred)**
- groq — Chat, Code, Repair, Plan
- openrouter — Chat, Code, Repair, Plan
- gemini — Chat, Plan, Large Context, Vision
- github_models — Chat, Code, Plan, CI
- cloudflare_ai — Chat, Embed, Edge
- jina — Reader, Web, Embed
- huggingface — Chat, Embed, Vision, Asset
- deepinfra — Chat, Code, Repair, Embed
- siliconflow — Chat, Code, Asset
- nvidia — Chat, Code, Repair
- sambanova — Chat, Code, Repair
- cerebras — Chat, Code, Repair

**Credit Pool**
- fal — Asset, Vision
- replicate — Asset, Vision

**Paid Locked (Require Explicit Unlock)**
- anthropic — Chat, Code, Repair, Plan, Large Context
- openai — Chat, Code, Repair, Plan, Vision, Embed
- xai — Chat, Plan
- deepseek_direct — Chat, Code, Repair
- cohere — Chat, Plan, Embed
- mistral — Chat, Code, Repair

**Infrastructure & Specialized**
- serpapi — Web search
- supabase — Database, Vector DB, Edge, Storage
- pinecone — Vector DB
- github_actions — CI, Edge
- google_cloud_free — Secret, Storage, Edge
- ollama — Local fallback (when available)

Every provider has success, auth-failure, rate-limit, malformed, timeout, and dry-run fixtures. RoutePolicy explains exactly why a provider was chosen or skipped. QuotaLedger + cooldown tracking prevents collapse during long-running work.

---

## Deterministic Verification & DLOI Addressing

ATROPOS uses DLOI (Dewey-style Ontological Addressing) so source documents and codebase symbols share one address space. The system can route tasks to exact source sections and exact AST symbols without cosine-RAG guesswork.

Structural verification (Tree-sitter + DLOI symbol graph) runs before any LLM escalation. Import consistency, type/structure invariants, and known pattern violations are checked deterministically. Only inconclusive cases escalate to an LLM reviewer. This keeps verification reproducible and reduces token spend on review.

Persistent cognitive memory stores not only successful patches but also normalized failure signatures linked to specific DLOI addresses. This creates clean training signals for future self-improvement components.

---

## Why the Hierarchical Territory Model Is Structurally Superior

Most current coding agent systems (Codex CLI, Claude Code Agent Teams, Google Antigravity, OpenSwarm, AgentsMesh, and similar projects) have converged on the same coordination pattern: worktree isolation combined with LLM-mediated messaging (task lists, mailboxes, dynamic subagent spawning, and reviewer agents).

This pattern creates several predictable problems at scale:

- **Coordination cost grows super-linearly** with the number of agents and the length of the session. Every task claim, status update, conflict notification, and synthesis step consumes model context and tokens.
- **Drift detection is reactive**. Problems are usually discovered after changes have already been made, either through reviewer agents or test failures.
- **Global visibility is fragmented**. No single component holds a clean, up-to-date picture of all active work. Visibility is scattered across many LLM contexts.
- **Information flow is uncontrolled**. Agents can request or receive information outside their intended scope, increasing hallucination surface and audit difficulty.
- **Verification is post-hoc**. Most systems rely on LLM review or external harness code after work is already done.

These are not minor implementation issues. They are direct consequences of choosing probabilistic generation for coordination, scope control, drift detection, and information routing — functions that should be narrow, deterministic, and inspectable state operations.

ATROPOS takes the opposite approach. It moves coordination out of the LLM layer and into explicit, inspectable architecture:

- Territory is assigned at dispatch time and stored with the task record.
- A Director layer continuously inspects diffs from active worktrees against assigned territories at low computational cost.
- Drift becomes visible the moment it appears, not after a reviewer agent or test failure.
- The HR Router forces any cross-territory information request through policy checks, redaction, and logging.
- Most agents never need to know what other agents are doing.

This produces lower token cost per unit of engineering work, earlier and cheaper drift detection, concentrated global visibility, controlled information flow, and cleaner signals for any future self-improving components.

---

## Nano-Style Coherent Batch Discipline (Current Implementation)

ATROPOS development follows a strict nano-style coherent batch discipline that will also govern autonomous operation in later phases.

Core rules:
- One batch equals one architectural promise with a narrow purpose and clear rollback boundary.
- Ideal batch size is 500–2,000 LOC that is topologically narrow and internally complete.
- Compile slices, not the entire project after every small edit.
- No success signal unless the full gate (narrow compile + smoke) passes.
- Every successful batch ends with compile success, smoke success, safe jar installation, git commit, and context export.

This discipline is what makes it possible to safely construct a complex sovereign system at high throughput while maintaining E(Δ)=0 safety at every step. It turns implicit workflow hygiene into an enforceable architectural primitive.

---

## Current Termux-Native Reactive TUI

ATROPOS already includes a fully reactive terminal interface built specifically for Termux constraints:

- Persistent tabs and screens (Dashboard, Chat, Providers, Factory, Logs, Keys, Shell)
- Command palette with fuzzy search and arrow navigation
- Separate history lanes for prompts, slash commands, and shell commands
- Responsive layout that reflows cleanly when terminal size changes (including Termux pinch-zoom)
- First-class shell bridge with timeout, cwd state, redaction, and allowlisting
- Provider and key doctor commands that report truthful state without leaking secrets

The interface treats terminal size as live input and recomputes card layout, truncation rules, and lower-panel allocation on every redraw. This makes it genuinely usable on mobile Termux environments where font scale and screen size frequently change.

