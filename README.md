<p align="center">
  <img src="docs/assets/atropos-wordmark.svg" alt="ATROPOS" width="480"/>
</p>

<p align="center">
  <strong>Open-source multi-agent AI coding engine</strong>
</p>

<p align="center">
  Terminal · Web · Android<br/>
  Plans, edits, verifies, and ships software across every surface.
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
  <img src="https://img.shields.io/badge/surfaces-terminal%20%7C%20web%20%7C%20android-a855f7" alt="Surfaces"/>
  <img src="https://img.shields.io/badge/languages-EN%20%7C%20ES%20%7C%20ZH%20%7C%20FR-0ea5e9" alt="Languages"/>
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

Then, from any project directory, in **any terminal** (phone or desktop):

```sh
cd your-project
atropos
```

Termux · iTerm · Windows Terminal · GNOME Terminal · Alacritty · Warp — same JVM + ANSI binary.

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

## Surfaces

| Surface | What you get |
|---------|----------------|
| **Terminal / CLI TUI** | Full engine control plane — works on mobile and desktop terminals |
| **Web** | Browser client over the local engine (session, files, evidence, agents) |
| **Android** | Native APK client aimed at dense, one-hand operator UX |

One engine. Multiple surfaces. Same projects, evidence, and providers.

---

## Commands worth knowing (terminal)

| Area | Commands |
|------|----------|
| Orient | `/help` · `/status` · `/dashboard` · `/providers` · `/keys status` · `/verify` |
| Work | `/factory run <prompt>` · `/factory plan` · `/agent run` · `/self-host run` |
| Providers | `/use <provider>` · `/use auto` · `/keys setup` · `/providers live-test` |
| DAG | `/dag status` · `/dag nodes` · `/dag runnable` |
| Recovery | `/resume` · `/interrupt soft` · `/snapshot capture` |

`/` opens the command palette · arrows navigate · Enter runs · Tab completes · Esc closes  
Attach files with `@path` (txt, md, docx, pdf; images described)

Full command surface lives in-app (`/help`).

---

## Why ATROPOS

| | |
|--|--|
| **Open source** | Inspect and modify the client — AGPL-3.0 |
| **Multi-agent** | Hierarchical dispatch with scope control — not a single chat thread |
| **Multi-provider** | Keys in env → auto-discover; free-first routing |
| **Long-running** | Durable projects and checkpoints — not a chatty prompt-driven session |
| **Multi-surface** | Terminal, web, and Android over one engine |
| **Any terminal** | Phone or desktop — same binary |

Providers need network. Local models (e.g. Ollama) work when configured.

---

## License

[AGPL-3.0](LICENSE)
