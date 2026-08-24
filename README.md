<p align="center">
  <img src="docs/assets/atropos-wordmark.svg" alt="ATROPOS" width="480"/>
</p>

<p align="center">
  <img src="docs/assets/atropos-demo.gif" alt="ATROPOS demo" width="360"/>
</p>

<p align="center">
  <strong>Autonomy without the drift.</strong>
</p>

<p align="center">
  Open-source multi-agent AI coding engine for terminal, web, and Android.<br/>
  Long-running hierarchical agents · durable projects · <strong>26 providers</strong>.<br/>
  Agentic coding agent · BYOK multi-provider · MCP-ready · self-hosted control plane.
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
```

```sh
npm install -g @mjmichaelware/atropos
```

```sh
cd your-project && atropos
```

**Requires Java 17+** (any terminal on phone or desktop — Termux, iSH, a-Shell, iTerm, Windows Terminal, GNOME, Alacritty, Warp, and others).

```sh
pkg install openjdk-21
```

```sh
sudo apt install openjdk-21-jre-headless
```

```sh
brew install openjdk@21
```

```sh
sudo dnf install java-21-openjdk-headless
```

Drop in the API keys you already use. ATROPOS auto-discovers providers on startup — you do not configure all 26. In-app: `/help` · command palette `/` · `@path` to attach files.

See [provider onboarding](docs/PROVIDER_ENVIRONMENT.md) for the supported environment aliases, local connect path, and free-first paid approval policy.

---

## What ATROPOS optimizes

| Pain most coding agents still cause | What ATROPOS does |
|-------------------------------------|-------------------|
| Uncontrolled autonomous edits and **agent drift** | **Scoped multi-agent hierarchy** — territory assigned at dispatch, illegal paths blocked before write |
| **Token waste**, **quota** burn, and chatty sub-agents | **Multi-agent orchestration** via policy and state — not LLM group chat |
| **Provider lock-in** and one-vendor launch hell | **BYOK** · **26 providers** · free-first multi-provider routing from keys you already have |
| Session dies and long work is lost | **Long-running autonomous** projects with **checkpoint**, **resume**, and restart continuity |
| Stuffing everything into a **flat context window** | **DAG-ordered** work units — dependency-aware atoms, not one mega-prompt |
| Parallel agents that thrash each other | Controlled **parallel agents** and swarm dispatch without circular busywork |
| Mega-prompt guessing instead of a real plan | **Planning and verification** via SpecGraph before execution |
| Cloud-as-brain and closed clients | **Local-first** · **self-hosted** · **open-source** engine (AGPL-3.0); cloud is optional LLM backend |
| “Done” when the model stops talking | **Verified completion** — independent checks before promotion |
| IDE-only or terminal-only tools | **Multi-surface**: terminal **CLI** / **TUI**, web app, Android / mobile — one engine |

---

## SpecGraph and the DAG

**SpecGraph** is the document analyzer and **DAG** builder inside ATROPOS — a full planning and verification app embedded in the engine. It turns source documents into a research-backed **directed acyclic graph** (task graph / dependency graph) of obligations so **long-horizon** autonomous coding agents and the multi-agent swarm execute ordered software-engineering work instead of dumping an entire product into one flat **context window**. Checkpoint and resume keep that graph alive across restarts. SpecGraph is how serious software blueprints enter the swarm without circular thrash or hallucinated structure.

Providers need network unless you configure a local model (e.g. Ollama).

---

## License

[AGPL-3.0](LICENSE)
