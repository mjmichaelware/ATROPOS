<p align="center">
  <img src="docs/assets/atropos-wordmark.svg" alt="ATROPOS" width="480"/>
</p>

<p align="center">
  <strong>开源多智能体 AI 编码引擎</strong>
</p>

<p align="center">
  终端 · Web · Android<br/>
  在每个界面上规划、编辑、验证并交付软件。
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

## 安装

```sh
# 一行安装
curl -fsSL https://raw.githubusercontent.com/mjmichaelware/ATROPOS/main/install.sh | sh

# npm
npm install -g @mjmichaelware/atropos

# 直接下载 JAR
curl -fL -o ~/ATROPOS.jar \
  https://github.com/mjmichaelware/ATROPOS/releases/download/latest/ATROPOS.jar
java -jar ~/ATROPOS.jar
```

**需要 Java 17+**

| 平台 | 安装 JDK |
|------|----------|
| Android (Termux) | `pkg install openjdk-21` |
| Debian/Ubuntu | `sudo apt install openjdk-21-jre-headless` |
| macOS | `brew install openjdk@21` |
| Windows | 安装 JDK 17+ 并使用 Windows Terminal |

Python 3.11+ 可选（推荐用于 atomizer）。无需 `pip install` — atomizer 已打包进 JAR。

然后在任意项目目录、**任意终端**（手机或桌面）：

```sh
cd 你的项目
atropos
```

Termux · iTerm · Windows Terminal · GNOME Terminal · Alacritty · Warp — 同一套 JVM + ANSI 二进制。

### 常用环境变量

| 变量 | 作用 |
|------|------|
| `ATROPOS_MODEL_<PROVIDER>` | 覆盖某一提供商的模型（如 `ATROPOS_MODEL_GROQ`） |
| `ATROPOS_INGEST_ROOTS` | 额外可读根目录（`:` 分隔） |
| `ATROPOS_NO_ANIMATION` | 跳过开场动画 |
| `ATROPOS_ASCII` | 使用 ASCII UI |
| `ATROPOS_JAVA_OPTS` | JVM 参数 |
| `ATROPOS_JAR` | 使用已有 jar |
| `ATROPOS_VERSION` | 固定安装版本 |

```sh
ATROPOS_MODEL_GROQ=llama-3.1-8b-instant atropos
ATROPOS_INGEST_ROOTS=/storage/emulated/0/Download atropos
```

```sh
atropos auth accept AGENTS.md
```

---

## 界面

| 界面 | 能力 |
|------|------|
| **终端 / CLI TUI** | 完整引擎控制面 — 手机与桌面终端 |
| **Web** | 浏览器客户端连接本地引擎 |
| **Android** | 原生高密度 APK，单手操作 |

一个引擎，多种界面。相同项目、证据与提供商。

---

## 常用命令（终端）

| 类别 | 命令 |
|------|------|
| 了解状态 | `/help` · `/status` · `/dashboard` · `/providers` · `/keys status` · `/verify` |
| 工作 | `/factory run <prompt>` · `/factory plan` · `/agent run` · `/self-host run` |
| 提供商 | `/use <provider>` · `/use auto` · `/keys setup` · `/providers live-test` |
| DAG | `/dag status` · `/dag nodes` · `/dag runnable` |
| 恢复 | `/resume` · `/interrupt soft` · `/snapshot capture` |

`/` 打开命令面板 · 方向键导航 · Enter 执行 · Tab 补全 · Esc 关闭  
用 `@路径` 附加文件

完整命令表在应用内（`/help`）。

---

## 为什么选 ATROPOS

| | |
|--|--|
| **开源** | 可检查与修改客户端 — AGPL-3.0 |
| **多智能体** | 分层调度与范围控制 — 不是单一聊天线程 |
| **多提供商** | 环境变量中的密钥 → 自动发现；优先免费 |
| **长时间运行** | 持久项目与检查点 — 不是被动的提示驱动会话 |
| **多界面** | 终端、Web 与 Android 共用一个引擎 |
| **任意终端** | 手机或桌面 — 同一二进制 |

提供商需要网络。本地模型（如 Ollama）在配置后可用。

---

## 许可证

[AGPL-3.0](LICENSE)
