<p align="center">
  <img src="docs/assets/atropos-wordmark.svg" alt="ATROPOS" width="480"/>
</p>

<p align="center">
  <strong>Stop babysitting chatty coding agents.</strong>
</p>

<p align="center">
  Long-running hierarchical work. Durable projects. Any terminal—phone or desktop.
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
# One-line installer
curl -fsSL https://raw.githubusercontent.com/mjmichaelware/ATROPOS/main/install.sh | sh

# npm
npm install -g @mjmichaelware/atropos

# Direct JAR
curl -fL -o ~/ATROPOS.jar \
  https://github.com/mjmichaelware/ATROPOS/releases/download/latest/ATROPOS.jar
java -jar ~/ATROPOS.jar
```

**Requires Java 17+**

| Platform | Install JDK |
|----------|-------------|
| Android (Termux) | `pkg install openjdk-21` |
| Debian/Ubuntu | `sudo apt install openjdk-21-jre-headless` |
| macOS | `brew install openjdk@21` |
| Windows | Install a JDK 17+ and use Windows Terminal |

Python 3.11+ is optional (recommended for the atomizer). No `pip install` required — the atomizer ships inside the JAR.

```sh
cd your-project
atropos
```

Termux · iTerm · Windows Terminal · GNOME Terminal · Alacritty · Warp — same JVM + ANSI binary.

### Environment variables

| Variable | Effect |
|----------|--------|
| `ATROPOS_MODEL_<PROVIDER>` | Override model for one provider (e.g. `ATROPOS_MODEL_GROQ`) |
| `ATROPOS_INGEST_ROOTS` | Extra read roots (`:`-separated) |
| `ATROPOS_NO_ANIMATION` | Skip opening sequence |
| `ATROPOS_ASCII` | ASCII UI instead of box-drawing |
| `ATROPOS_JAVA_OPTS` | JVM flags |
| `ATROPOS_JAR` | Use a jar you already have |
| `ATROPOS_VERSION` | Pin installer release |

```sh
ATROPOS_MODEL_GROQ=llama-3.1-8b-instant atropos
ATROPOS_INGEST_ROOTS=/storage/emulated/0/Download atropos
atropos auth accept AGENTS.md
```

In-app: `/help` · command palette `/` · attach files with `@path`.

---

## Why not another coding agent

Most tools are **prompt-driven sessions**: one chat thread, reactive steps, work dies when the session ends, and the agent is happy to spray the whole tree.

ATROPOS is built the other way:

| Pain with typical CLIs / agents | Here |
|--------------------------------|------|
| Chatty, turn-by-turn babysitting | Long-running autonomous hierarchical agents |
| Session dies → start over | Durable projects and checkpoints |
| Vendor lock or one-provider launch hell | Multi-provider from env keys |
| Desktop-only or IDE-only | Same engine in any terminal, web, and Android |
| Closed client | Open source (AGPL-3.0) — inspect and change it |

Local-first by design. Providers need network unless you wire a local model (e.g. Ollama).

---

## License

[AGPL-3.0](LICENSE)
