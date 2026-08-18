<p align="center">
  <img src="docs/assets/atropos-wordmark.svg" alt="ATROPOS" width="480" onerror="this.style.display='none'"/>
</p>

<h1 align="center">ATROPOS</h1>

<p align="center">
  <strong>Open-source AI coding agent</strong>
</p>

<p align="center">
  Multi-provider CLI that plans, edits, verifies, and ships code.<br/>
  Local-first · Termux-native · verification gates · durable projects
</p>

<p align="center">
  <a href="README.md">English</a> ·
  <a href="README.es.md">Español</a> ·
  <a href="README.zh-CN.md">简体中文</a> ·
  <a href="README.fr.md">Français</a>
</p>

<p align="center">
  <a href="https://github.com/mjmichaelware/ATROPOS/stargazers"><img src="https://img.shields.io/github/stars/mjmichaelware/ATROPOS?style=flat" alt="Stars"/></a>
  <a href="LICENSE"><img src="https://img.shields.io/github/license/mjmichaelware/ATROPOS?style=flat" alt="License"/></a>
  <a href="https://github.com/mjmichaelware/ATROPOS/releases"><img src="https://img.shields.io/github/v/release/mjmichaelware/ATROPOS?style=flat" alt="Release"/></a>
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
| Termux | `pkg install openjdk-21` |
| Debian/Ubuntu | `sudo apt install openjdk-21-jre-headless` |
| macOS | `brew install openjdk@21` |

Python 3.11+ is optional (recommended for the atomizer). No `pip install` required — the atomizer ships inside the JAR.

Then:

```sh
cd your-project
atropos
```

Works in Termux, iTerm, Windows Terminal, GNOME Terminal, Alacritty — plain JVM + ANSI.

### Useful environment variables

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
```

```sh
atropos auth accept AGENTS.md
```

---

## Commands worth knowing

| Area | Commands |
|------|----------|
| Orient | `/help` · `/status` · `/dashboard` · `/providers` · `/keys status` · `/verify` |
| Work | `/factory run <prompt>` · `/factory plan` · `/agent run` · `/self-host run` |
| Providers | `/use <provider>` · `/use auto` · `/keys setup` · `/providers live-test` |
| DAG | `/dag status` · `/dag nodes` · `/dag runnable` |
| Recovery | `/resume` · `/interrupt soft` · `/snapshot capture` |

`/` opens the command palette · arrows navigate · Enter runs · Tab completes · Esc closes  
Attach files with `@path` (txt, md, docx, pdf; images described)

Full command surface lives in-app (`/help`). This README stays short on purpose.

---

## Why ATROPOS

| | |
|--|--|
| **Open source** | Inspect and modify the client — AGPL-3.0 |
| **Multi-provider** | Keys in env → auto-discover; free-first routing |
| **Verification before done** | Gates block fake success |
| **Scope safety** | Agents stay inside assigned paths by default |
| **Termux-native** | Built to run on phone terminals, not only desktops |
| **Durable work** | Projects and checkpoints survive restart |

Not offline by default — providers need network. Local models (e.g. Ollama) work when you configure them.

---

## License

[AGPL-3.0](LICENSE)
