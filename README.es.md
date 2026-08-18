<p align="center">
  <img src="docs/assets/atropos-wordmark.svg" alt="ATROPOS" width="480"/>
</p>

<p align="center">
  <strong>Autonomía sin la deriva.</strong>
</p>

<p align="center">
  Motor de codificación multi-agente con IA de código abierto para terminal, web y Android.<br/>
  Agentes jerárquicos de larga duración · proyectos duraderos · <strong>26 proveedores</strong>.<br/>
  Agente de codificación agentic · BYOK multi-proveedor · listo para MCP · plano de control self-hosted.
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

## Instalación

```sh
curl -fsSL https://raw.githubusercontent.com/mjmichaelware/ATROPOS/main/install.sh | sh

npm install -g @mjmichaelware/atropos

cd tu-proyecto && atropos
```

**Java 17+.** Termux: `pkg install openjdk-21` · Debian: `sudo apt install openjdk-21-jre-headless` · macOS: `brew install openjdk@21`

Añade las claves API que ya uses. ATROPOS descubre proveedores al arrancar — no configuras los 26. En la app: `/help` · paleta `/` · `@ruta` para adjuntar archivos.

---

## Qué optimiza ATROPOS

| Dolor que siguen causando la mayoría de agentes de codificación | Qué hace ATROPOS |
|----------------------------------------------------------------|------------------|
| Ediciones autónomas sin control y **deriva del agente** | **Jerarquía multi-agente con alcance** — territorio al despacho; rutas ilegales bloqueadas antes de escribir |
| **Desperdicio de tokens**, quema de **cuota** y subagentes charlatanes | **Orquestación multi-agente** por política y estado — no chat grupal entre LLMs |
| **Cierre a un proveedor** y lanzamiento de un solo vendor | **BYOK** · **26 proveedores** · enrutado multi-proveedor free-first con las claves que ya tienes |
| La sesión muere y se pierde el trabajo largo | Proyectos **autónomos de larga duración** con **checkpoint**, **resume** y continuidad tras reinicio |
| Meterlo todo en una **ventana de contexto plana** | Unidades de trabajo **ordenadas por DAG** — átomos con dependencias, no un mega-prompt |
| Agentes en paralelo que se pisan entre sí | **Agentes en paralelo** y despacho de swarm controlado, sin trabajo circular |
| Adivinar con un mega-prompt en lugar de un plan real | **Planificación y verificación** con SpecGraph antes de ejecutar |
| La nube como cerebro y clientes cerrados | Motor **local-first** · **self-hosted** · **código abierto** (AGPL-3.0); la nube es backend LLM opcional |
| “Listo” cuando el modelo deja de hablar | **Finalización verificada** — comprobaciones independientes antes de promover |
| Solo IDE o solo terminal | **Multi-superficie**: CLI / **TUI** de terminal, app web, Android / móvil — un motor |

---

## SpecGraph y el DAG

**SpecGraph** es el analizador de documentos y el constructor de **DAG** dentro de ATROPOS — una app completa de planificación y verificación embebida en el motor. Convierte documentos fuente en un **grafo dirigido acíclico** (task graph / dependency graph) respaldado por investigación, para que agentes de codificación autónomos de **largo horizonte** y el swarm multi-agente ejecuten ingeniería de software ordenada en lugar de volcar un producto entero en una sola **ventana de contexto** plana. Checkpoint y resume mantienen ese grafo vivo tras reinicios. SpecGraph es cómo entran los blueprints serios al swarm sin thrash circular ni estructura alucinada.

Los proveedores necesitan red salvo que configures un modelo local (p. ej. Ollama).

---

## Licencia

[AGPL-3.0](LICENSE)
