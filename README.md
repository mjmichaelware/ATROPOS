<p align="center">
  <img src="docs/assets/atropos-wordmark.svg" alt="ATROPOS" width="480"/>
</p>

<p align="center">
  <strong>Autonomy without the drift.</strong>
</p>

<p align="center">
  Scoped hierarchical agents. Durable projects.<br/>
  Terminal · Web · Android — same engine.
</p>

<p align="center">
  <a href="README.md">English</a> ·
  <a href="README.es.md">Español</a> ·
  <a href="README.zh-CN.md">简体中文</a> ·
  <a href="README.fr.md">Français</a>
</p>

<p align="center">
  <img src="https://img.shields.io/badge/license-AGPL--3.0-blue" alt="License AGPL-3.0"/>
  <img src="https://img.shields.io/badge/kotlin-primary-7F52FF" alt="Kotlin"/>
  <img src="https://img.shields.io/badge/open--source-multi--agent-a855f7" alt="Open source multi-agent"/>
</p>

---

## Install

```sh
curl -fsSL https://raw.githubusercontent.com/mjmichaelware/ATROPOS/main/install.sh | sh
# or: npm install -g @mjmichaelware/atropos

cd your-project && atropos
```

**Java 17+.** Termux: `pkg install openjdk-21` · Debian: `sudo apt install openjdk-21-jre-headless` · macOS: `brew install openjdk@21`

Keys in the environment are enough — ATROPOS discovers providers on startup. Optional: `ATROPOS_MODEL_<PROVIDER>`, `ATROPOS_INGEST_ROOTS`, `ATROPOS_JAVA_OPTS`. In-app: `/help` · palette `/` · `@path` to attach files.

---

## Why the field is broken

Most “autonomous” stacks share one failure mode: **worktree isolation + LLM group chat**. Agents get separate trees, then burn your quota *talking to each other* about scope, status, and handoffs. Drift is found after the damage. Context fills with meta-chat. The cloud sees your repo again and again. That is not intelligence. That is an expensive meeting with extra steps.

### Cursor Agent
Cloud agents and soft scope. Users report agents that keep editing **after stop**, recreate deleted files, and commit without clear intent. Cloud sessions have served **stale default-branch file reads** while git on the same machine was correct — silent prompt drift. Autonomy is real; **control is not**. Product gravity pulls code and loops into the cloud. Closed client.

**ATROPOS:** territory is assigned at dispatch. Out-of-territory writes are blocked *before* they land. Local-first engine. Cloud is an optional model provider, not the control plane.

### Claude Code
Best-in-class single-agent quality — and a **quota furnace**. Subagent fan-out is reported around **~7×** token cost vs a normal session; MCP tool defs can eat a third of the window before you type. Weekly and 5-hour caps die on long agent teams. Anthropic-first lock-in. Coordination is still LLM-mediated handoffs, not hard scope law.

**ATROPOS:** multi-provider from env keys; free-first routing. Coordination is hierarchy + policy state, not another subagent meeting on your bill.

### Codex CLI
Strong sandbox. OpenAI-shaped economics and defaults. Users hit **opaque quota drain** (single prompts eating large chunks of rolling windows). Built-in wait/re-sample loops have burned weekly quota on long jobs. Harness/sandbox mismatch when supervising other CLIs. Vendor core.

**ATROPOS:** open client (AGPL-3.0). Provider is a backend, not the product identity. Durable projects instead of rolling-window anxiety as architecture.

### OpenHands
Ambitious multi-agent / issue→PR autonomy. Orchestration is **message-heavy**. Drift and failure modes are post-hoc (job ended, PR opened, tests maybe). Remote runner gravity is common. You pay for the chat between agents as much as for the patch.

**ATROPOS:** preventive territory, not post-hoc regret. Promotion after independent checks — not when the swarm stops typing.

### OpenCode
Excellent OSS TUI and real BYOK. Still **session-shaped**. Research measurements have shown order-of-magnitude **token waste per solved task** vs tighter harnesses (idle/no-action turns compounding context). You bring the keys; you still pay for chatter if the loop is chatty.

**ATROPOS:** long-running durable projects + restart continuity. Scoped hierarchy so “open source multi-provider” is not the same as “unbounded session spend.”

### Aider
Git discipline is real. Autonomy is not — it is a **pair-programmer**, turn-by-turn. Maintenance-mode trajectory in 2026. Terminal UX is **dated and sparse** next to modern TUIs. Fine for small diffs; wrong tool for multi-day hierarchical work.

**ATROPOS:** reactive multi-surface control plane (terminal, web, Android) built for operators, not a 2023 REPL aesthetic.

---

## What ATROPOS actually optimizes

| Failure mode in the field | ATROPOS response |
|---------------------------|------------------|
| Unscoped autonomy / drift | Territory at dispatch; block illegal paths before write |
| Chatty sub-agents eating quota | Hierarchy + state/policy coordination — not agent group chat |
| Provider lock | Multi-provider discovery from env; free-first cascade |
| Session death | Durable projects, checkpoints, restart continuity |
| Cloud-as-brain | Local-first engine; providers are interchangeable backends |
| “Done” when the model stops | Independent checks before promotion |
| IDE-only or terminal-only | One engine: terminal · web · Android |
| Closed client | AGPL-3.0 — inspect and change it |

Autonomy without the drift is not a slogan. It is the architecture: **scoped hierarchical agents, durable projects, multi-provider routing, multi-surface control.**

Providers need network unless you configure a local model (e.g. Ollama).

---

## License

[AGPL-3.0](LICENSE)
