<p align="center">
  <img src="docs/assets/atropos-wordmark.svg" alt="ATROPOS" width="480"/>
</p>

<p align="center">
  <strong>Autonomy without the drift.</strong>
</p>

<p align="center">
  Open-source multi-agent AI coding engine for terminal, web, and Android.<br/>
  Long-running hierarchical agents · durable projects · <strong>26 providers</strong>.
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

npm install -g @mjmichaelware/atropos

cd your-project && atropos
```

**Java 17+.** Termux: `pkg install openjdk-21` · Debian: `sudo apt install openjdk-21-jre-headless` · macOS: `brew install openjdk@21`

Drop in the API keys you already use. ATROPOS auto-discovers providers on startup — you do not configure all 26. In-app: `/help` · command palette `/` · `@path` to attach files.

---

## What ATROPOS optimizes

| Pain most coding agents still cause | What ATROPOS does |
|-------------------------------------|-------------------|
| Uncontrolled autonomous edits and **agent drift** | **Scoped multi-agent hierarchy** — territory assigned at dispatch, illegal paths blocked before write |
| **Token waste** and quota burn from chatty sub-agents | Coordination via **policy and state**, not LLM group chat |
| **Provider lock-in** and one-vendor launch hell | **26 providers** integrated — free-first multi-provider routing from the keys you already have |
| Session dies and long work is lost | **Long-running autonomous** projects with checkpoints and restart continuity |
| Stuffing everything into a **flat context window** | **DAG-ordered** work units — dependency-aware atoms, not one mega-prompt |
| Cloud-as-brain and closed clients | **Local-first open-source** engine (AGPL-3.0); cloud is optional model backend |
| “Done” when the model stops talking | **Verified completion** — independent checks before promotion |
| IDE-only or terminal-only tools | **Multi-surface**: terminal CLI, web app, and Android — one engine |

---

## SpecGraph and the DAG

**SpecGraph** is the document analyzer and **DAG** builder inside ATROPOS — a full planning and verification app embedded in the engine. It turns source documents into a research-backed **directed acyclic graph** of obligations so long-horizon autonomous coding agents execute ordered, dependency-aware work instead of dumping an entire product into one flat context window. SpecGraph is how serious software blueprints enter the multi-agent swarm without circular thrash or hallucinated structure.

Providers need network unless you configure a local model (e.g. Ollama).

---

## License

[AGPL-3.0](LICENSE)
