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
  <img src="https://img.shields.io/badge/Kotlin-7F52FF?logo=kotlin&logoColor=white" alt="Kotlin"/>
  <img src="https://img.shields.io/badge/Python-3776AB?logo=python&logoColor=white" alt="Python"/>
  <img src="https://img.shields.io/badge/TypeScript-3178C6?logo=typescript&logoColor=white" alt="TypeScript"/>
  <img src="https://img.shields.io/badge/Java-ED8B00?logo=openjdk&logoColor=white" alt="Java"/>
  <img src="https://img.shields.io/badge/CSS-1572B6?logo=css3&logoColor=white" alt="CSS"/>
</p>

---

## Install

```sh
curl -fsSL https://raw.githubusercontent.com/mjmichaelware/ATROPOS/main/install.sh | sh
# or: npm install -g @mjmichaelware/atropos

cd your-project && atropos
```

**Java 17+.** Termux: `pkg install openjdk-21` · Debian: `sudo apt install openjdk-21-jre-headless` · macOS: `brew install openjdk@21`

Put provider keys in the environment. ATROPOS discovers what you have on startup. In-app: `/help` · command palette `/` · `@path` to attach files.

---

## The flat-context fantasy is dead

Shoving an entire product into one giant context window and hoping a single model “understands everything” is how you buy confident nonsense. Flat prompts do not create structure. They hide the lack of it until the agent rewrites the wrong half of the tree.

ATROPOS does not pretend a bigger window is a plan. Work is atomized into a **directed acyclic graph (DAG)** of obligations — ordered, dependency-aware, and runnable without circular busywork. **SpecGraph** is the planning and verification substrate: it turns requirements into research-enriched, checkable blueprints that the engine can execute node by node. The DAG is the spine. SpecGraph is how serious plans enter that spine. Hierarchy and territory keep each node from becoming a free-for-all.

That is the opposite of chatty sub-agents renegotiating scope in prose while your quota evaporates.

---

## Why the field is broken

Most “autonomous” stacks still do this: isolate workers in worktrees, then spend the budget on **LLM group chat** — mailboxes, status ping-pong, soft scope, drift found after the damage. The cloud sees your repo on a loop. Providers stay locked. “Done” means the model stopped typing.

### Cursor Agent
Real autonomy with soft control. Agents that keep moving after stop, recreate deleted files, and commit without clear intent show up in the wild. Cloud paths have served **stale default-branch reads** while local git was correct — silent drift. Closed product; gravity pulls loops into the cloud.

**ATROPOS:** territory at dispatch. Illegal paths blocked before write. Local-first engine. Cloud is a model endpoint, not the brain.

### Claude Code
Excellent single-agent depth — and a **quota furnace**. Subagent fan-out is reported near **~7×** a normal session; MCP tool defs can eat a large slice of the window before real work starts. Weekly and rolling caps die on long teams. Anthropic-first.

**ATROPOS:** multi-provider from env keys; free-first routing. Coordination is hierarchy and state, not another paid meeting between agents.

### Codex CLI
Strong sandbox. OpenAI-shaped defaults and metering. Opaque quota drain and wait/re-sample loops have burned weekly allowance on long jobs. Vendor core under the open edges.

**ATROPOS:** open client (AGPL-3.0). Durable projects instead of living inside a rolling window as architecture.

### OpenHands
Ambitious issue→PR autonomy with **message-heavy** orchestration. Drift is post-hoc. You pay for the chat between agents as much as for the patch.

**ATROPOS:** preventive scope. Promote only after independent checks — not when the swarm goes quiet.

### OpenCode
Best-in-class OSS TUI and real BYOK. Still session-shaped; measured harness work has shown large **token waste per solved task** when loops idle and re-sample. Keys are free; chatter is not.

**ATROPOS:** DAG-ordered long runs, durable projects, restart continuity — multi-provider without treating “open” as “unbounded session spend.”

### Aider
Git discipline is real. Autonomy is not. It is a turn-by-turn pair programmer with a **dated terminal UX**, now on a maintenance trajectory. Right tool for small diffs. Wrong tool for multi-day hierarchical delivery.

**ATROPOS:** multi-surface control plane (terminal, web, Android) built for operators who run real work, not a 2023 REPL aesthetic.

---

## What ATROPOS optimizes

| Field failure | ATROPOS |
|---------------|---------|
| Flat mega-context “understanding” | **DAG + SpecGraph** — ordered atoms, not one blob |
| Unscoped autonomy / drift | Territory at dispatch; block before write |
| Chatty sub-agents | Hierarchy + policy state, not agent group chat |
| Provider lock | Multi-provider discovery; free-first cascade |
| Session death | Durable projects, checkpoints, restart continuity |
| Cloud-as-brain | Local-first engine; providers are backends |
| “Done” when the model stops | Independent checks before promotion |
| One surface only | Terminal · web · Android, one engine |
| Closed client | AGPL-3.0 |

Autonomy without the drift is the architecture: **scoped hierarchical agents, DAG-ordered work, SpecGraph-backed plans, multi-provider routing, multi-surface control.**

Providers need network unless you configure a local model (e.g. Ollama).

---

## License

[AGPL-3.0](LICENSE)
