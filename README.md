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

## Why the field is broken

Most “autonomous” coding stacks share the same structural failure mode: **isolated worktrees + LLM-mediated chat between agents**.

They give each worker its own worktree (good), then immediately nullify that isolation with mailboxes, shared task lists, and coordinator agents that spend your quota **talking about work instead of doing it**. Drift is detected after damage. Scope is negotiated in prose. Context fills with meta-chat. Providers stay locked. The cloud sees your tree over and over.

That pattern is not a feature. It is an expensive way to pretend coordination is intelligence.

| | Cursor Agent | Claude Code | Codex CLI | OpenHands | OpenCode | Aider | **ATROPOS** |
|--|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| **Coordination** | Chat / IDE agent loop | Strong single-agent + subagents; still LLM-mediated handoffs | Sandbox-heavy single harness | Multi-agent, message-heavy | Flexible BYOK TUI | Pair-programmer turns | **Hierarchy + territory at dispatch — not agent group chat** |
| **Drift control** | Aggressive autonomy; scope is soft | Reactive review / tests after edits | Sandbox limits blast radius; not preventive scope law | Issue→PR autonomy; drift is post-hoc | Session-bound | Git commits help *after* the mess | **Preventive scope: out-of-territory mutation blocked before write** |
| **Quota / context** | Cloud session economics | Burns hard on long agent teams | Better token story on plan; still session-shaped | Heavy orchestration chat | BYOK — you pay for chatter | Cheap turns; not long-horizon | **Coordination is state + policy, not another LLM meeting** |
| **Provider lock** | Product-tied models + BYOK edges | Anthropic-first | OpenAI-first | BYOK possible; product gravity elsewhere | Strong BYOK | Strong BYOK | **Multi-provider from env keys; free-first routing** |
| **Repo in the cloud** | Cloud agents / remote loops common | Cloud-backed product path | Local + cloud handoff | Often remote runner / sandbox | Local-first TUI | Local | **Local-first engine; cloud is optional provider, not the brain** |
| **Surfaces** | IDE-first (+ agent CLI) | Terminal-first | Terminal-first | Web / remote agent UX | Terminal TUI | Terminal; UX is dated and sparse | **Terminal + web + Android, one engine** |
| **UX honesty** | Polished IDE | Strong TUI | Solid CLI | Heavy product surface | Best-in-class OSS TUI feel | **Weak, aging terminal UX** | Reactive TUI built for real terminals (including phone) |
| **“Done” means** | Agent stopped / PR opened | Model finished the loop | Sandbox run finished | Issue closed / PR opened | Session outcome | Commit landed | **Promotion only after independent checks — not when the model shuts up** |
| **Long runs** | Session / product limits | Quota pressure on long teams | Plan windows | Job-shaped | Session | Turn-shaped | **Durable projects + restart continuity** |
| **Client** | Closed | Closed / restricted | Open parts, vendor core | Open core | Open | Open | **Open source (AGPL-3.0)** |

**Read the failure mode once:** worktree isolation is worthless if agents spend the budget in a group chat, renegotiate scope in natural language, and only notice drift when tests or humans scream. That is how you buy hallucination with extra steps — and how context dies before the feature ships.

ATROPOS does not win by “more autonomy.” It wins by **scoped autonomy**: hierarchical dispatch, territory at assignment time, controlled cross-boundary information, multi-provider routing, and durable project state across terminal, web, and Android.

Providers need network unless you configure a local model (e.g. Ollama).

---

## License

[AGPL-3.0](LICENSE)
