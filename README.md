# ATROPOS

**The Sovereign Local-First Deterministic Hierarchical Multi-Swarm Software Development Engine**

![Hero](docs/assets/imported/20260629_190733/image-14.jpg)

**Independent • Runs for days without stopping • No quota collapse • Preventive verification • E(Δ)=0**

ATROPOS is a complete autonomous software-development engine and control plane. It uses a strict hierarchical territory model so it can work for days on end with no quota problems, no uncontrolled information leaks, and real preventive verification.

Current systems rely on chatty LLM coordination. This causes exploding token costs, reactive drift detection, and makes long-running autonomous work unsustainable. ATROPOS replaces that model with explicit territory assignment, Director-level preventive diff monitoring, a controlled HR Router, and deterministic verification before any LLM escalation.

---

![E(Δ)=0 Principle](docs/assets/imported/20260629_190733/image-20.jpg)

---

## Core Hierarchy

![Hierarchy](docs/assets/imported/20260629_190733/image-30.jpg)

**Human Owner/CEO** — Strategic direction and final authority on irreversible actions.

**Director** — Task decomposition, territory assignment at dispatch time, global diff monitoring across all worktrees, preventive drift detection, and escalation handling.

**VP / Manager** — Domain and team coordination. They receive queues from the Director and assign explicit territories to Specialists and Workers.

**Specialist** — Deep domain expertise with higher verification requirements and narrower territory defaults.

**Worker** — Executes strictly inside the granted territory in its dedicated worktree. No visibility into other agents’ work is required.

**Custodian** — Deterministic hygiene tasks (temp file removal, dead branch pruning, artifact cleanup). Runs on schedules or Director triggers with minimal LLM involvement.

**Auditor** — Independent read-only verification layer with a separate reporting line to prevent execution-layer capture.

**HR / Information Router** — The single controlled chokepoint for any cross-territory information request. Applies redaction, scope validation, and risk classification. Most intra-territory work bypasses it entirely.

This structure keeps coordination cost linear with active territories instead of exploding with agent count or session length.

---

## Provider Grid (30+ Agents & Capabilities)

ATROPOS includes a complete registry of 30+ providers used as agents across different tasks and capabilities:

**Free / Cooldown Eligible**
- groq (Chat, Code, Repair, Plan)
- openrouter (Chat, Code, Repair, Plan)
- gemini (Chat, Plan, Large Context, Vision)
- github_models (Chat, Code, Plan, CI)
- cloudflare_ai (Chat, Embed, Edge)
- jina (Reader, Web, Embed)
- huggingface (Chat, Embed, Vision, Asset)
- deepinfra (Chat, Code, Repair, Embed)
- siliconflow (Chat, Code, Asset)
- nvidia (Chat, Code, Repair)
- sambanova (Chat, Code, Repair)
- cerebras (Chat, Code, Repair)

**Credit Pool**
- fal (Asset, Vision)
- replicate (Asset, Vision)

**Paid Locked (Explicit Unlock Required)**
- anthropic (Chat, Code, Repair, Plan, Large Context)
- openai (Chat, Code, Repair, Plan, Vision, Embed)
- xai (Chat, Plan)
- deepseek_direct (Chat, Code, Repair)
- cohere (Chat, Plan, Embed)
- mistral (Chat, Code, Repair)

**Infrastructure & Specialized**
- serpapi (Web)
- supabase (Database, Vector DB, Edge, Storage)
- pinecone (Vector DB)
- github_actions (CI, Edge)
- google_cloud_free (Secret, Storage, Edge)
- ollama (Local fallback when available)

Every provider has success, auth-failure, rate-limit, malformed, timeout, and dry-run fixtures. RoutePolicy and QuotaLedger ensure the system never collapses even during long-running work. Free and cooldown providers are strongly preferred.

---

## Nano-Style Coherent Batch Discipline

ATROPOS development (and future autonomous operation) follows strict nano-style rules:

- One batch = one architectural promise
- Ideal size: 500–2,000 LOC, topologically narrow, internally complete
- Compile slices, not the universe
- No success echo unless the whole gate passes
- Every batch ends with compile success + smoke success + safe jar + git commit + context export

This discipline enables safe, high-throughput construction of a complex sovereign system while maintaining E(Δ)=0 rollback safety.

---

## Current State

- Reactive Termux TUI with persistent tabs, command palette, and responsive layout that reflows on pinch-zoom
- Complete provider descriptor registry, RoutePolicy, QuotaLedger, and redaction system
- Deterministic verification foundation with DLOI addressing
- All development follows the full 20-phase blueprint with gated passes and context exports after every success

**Pass 5 (In Progress)**: Provider activation doctor, `/keys doctor`, truthful provider state rendering, and interactive route inspection.

---

ATROPOS is released under AGPL-3.0-only. It is fully independent and not affiliated with any provider company. Providers are interchangeable reasoning backends. The system owns routing, memory, verification, policy, secrets, and execution.

**ATROPOS is independent. It is sovereign. It is deterministic. It is built to last.**