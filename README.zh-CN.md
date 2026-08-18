<p align="center">
  <img src="docs/assets/atropos-wordmark.svg" alt="ATROPOS" width="480"/>
</p>

<p align="center">
  <strong>别再照看话痨式编码代理了。</strong>
</p>

<p align="center">
  长时间分层工作。持久项目。任意终端——手机或桌面。
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

## 安装

```sh
curl -fsSL https://raw.githubusercontent.com/mjmichaelware/ATROPOS/main/install.sh | sh
npm install -g @mjmichaelware/atropos

curl -fL -o ~/ATROPOS.jar \
  https://github.com/mjmichaelware/ATROPOS/releases/download/latest/ATROPOS.jar
java -jar ~/ATROPOS.jar
```

**需要 Java 17+**

| 平台 | JDK |
|------|-----|
| Android (Termux) | `pkg install openjdk-21` |
| Debian/Ubuntu | `sudo apt install openjdk-21-jre-headless` |
| macOS | `brew install openjdk@21` |
| Windows | JDK 17+ + Windows Terminal |

```sh
cd 你的项目
atropos
```

### 环境变量

| 变量 | 作用 |
|------|------|
| `ATROPOS_MODEL_<PROVIDER>` | 覆盖提供商模型 |
| `ATROPOS_INGEST_ROOTS` | 额外可读根目录 |
| `ATROPOS_NO_ANIMATION` | 跳过开场 |
| `ATROPOS_ASCII` | ASCII UI |
| `ATROPOS_JAVA_OPTS` | JVM 参数 |
| `ATROPOS_JAR` | 使用已有 jar |
| `ATROPOS_VERSION` | 固定安装版本 |

应用内：`/help` · 命令面板 `/` · `@路径` 附加文件。

---

## 为什么不是又一个编码代理

多数工具是**提示驱动的会话**：单聊天线程、被动步进、会话结束工作即丢，还容易改动整个目录树。

ATROPOS 相反：长时间运行的分层多智能体、持久项目、环境变量多提供商、同一引擎覆盖终端 / Web / Android、开源客户端（AGPL-3.0）。

---

## 许可证

[AGPL-3.0](LICENSE)
