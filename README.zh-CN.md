<p align="center">
  <img src="docs/assets/atropos-wordmark.svg" alt="ATROPOS" width="480"/>
</p>

<p align="center">
  <strong>有自主，无漂移。</strong>
</p>

<p align="center">
  面向终端、Web 与 Android 的开源多智能体 AI 编码引擎。<br/>
  长时间运行的分层智能体 · 持久项目 · <strong>26 个提供商</strong>。<br/>
  Agentic 编码代理 · BYOK 多提供商 · MCP 就绪 · 自托管控制面。
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

## 安装

```sh
curl -fsSL https://raw.githubusercontent.com/mjmichaelware/ATROPOS/main/install.sh | sh

npm install -g @mjmichaelware/atropos

cd 你的项目 && atropos
```

**需要 Java 17+。** Termux：`pkg install openjdk-21` · Debian：`sudo apt install openjdk-21-jre-headless` · macOS：`brew install openjdk@21`

放入你已有的 API 密钥即可。ATROPOS 启动时自动发现提供商——不必配置全部 26 个。应用内：`/help` · 命令面板 `/` · `@路径` 附加文件。

---

## ATROPOS 优化什么

| 多数编码代理仍带来的痛点 | ATROPOS 的做法 |
|--------------------------|---------------|
| 失控的自主编辑与 **agent drift（智能体漂移）** | **有范围的多智能体层级** — 调度时分配领地，非法路径写入前拦截 |
| **Token 浪费**、**配额**烧尽与话痨子智能体 | 通过策略与状态做 **多智能体编排** — 不是 LLM 群聊 |
| **提供商锁定** 与单一厂商启动地狱 | **BYOK** · **26 个提供商** · 优先免费的多提供商路由 |
| 会话结束，长任务丢失 | **长时间自主** 项目，支持 **checkpoint**、**resume** 与重启延续 |
| 把一切塞进 **平坦上下文窗口** | **DAG 有序** 工作单元 — 有依赖的原子，不是一个巨型提示 |
| 并行智能体互相踩踏 | 受控的 **并行智能体** 与 swarm 调度，无循环空转 |
| 用巨型提示猜，而不是真正的计划 | 执行前用 SpecGraph 做 **规划与验证** |
| 云当大脑、闭源客户端 | **本地优先** · **自托管** · **开源** 引擎（AGPL-3.0）；云只是可选 LLM 后端 |
| 模型停嘴就算“完成” | **经验证的完成** — 独立检查后再提升 |
| 只做 IDE 或只做终端 | **多界面**：终端 **CLI** / **TUI**、Web、Android / 移动端 — 同一引擎 |

---

## SpecGraph 与 DAG

**SpecGraph** 是 ATROPOS 内的文档分析器与 **DAG** 构建器 — 嵌在引擎里的完整规划与验证应用。它把源文档变成有研究支撑的 **有向无环图**（任务图 / 依赖图），让 **长周期** 自主编码代理与多智能体 swarm 按序执行软件工程工作，而不是把整个产品倒进一个平坦的 **上下文窗口**。Checkpoint 与 resume 让该图在重启后仍然存活。SpecGraph 是严肃软件蓝图进入 swarm、避免循环 thrash 与幻觉结构的方式。

除非配置本地模型（如 Ollama），提供商需要网络。

---

## 许可证

[AGPL-3.0](LICENSE)
