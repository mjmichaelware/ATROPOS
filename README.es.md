<p align="center">
  <img src="docs/assets/atropos-wordmark.svg" alt="ATROPOS" width="480"/>
</p>

<p align="center">
  <strong>Motor de codificación multi-agente con IA de código abierto</strong>
</p>

<p align="center">
  Terminal · Web · Android<br/>
  Planifica, edita, verifica y entrega software en cada superficie.
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

## Instalación

```sh
# Instalador de una línea
curl -fsSL https://raw.githubusercontent.com/mjmichaelware/ATROPOS/main/install.sh | sh

# npm
npm install -g @mjmichaelware/atropos

# JAR directo
curl -fL -o ~/ATROPOS.jar \
  https://github.com/mjmichaelware/ATROPOS/releases/download/latest/ATROPOS.jar
java -jar ~/ATROPOS.jar
```

**Requiere Java 17+**

| Plataforma | Instalar JDK |
|------------|--------------|
| Android (Termux) | `pkg install openjdk-21` |
| Debian/Ubuntu | `sudo apt install openjdk-21-jre-headless` |
| macOS | `brew install openjdk@21` |
| Windows | Instala un JDK 17+ y usa Windows Terminal |

Python 3.11+ es opcional (recomendado para el atomizer). No hace falta `pip install` — el atomizer va dentro del JAR.

Luego, desde cualquier proyecto, en **cualquier terminal** (móvil o escritorio):

```sh
cd tu-proyecto
atropos
```

Termux · iTerm · Windows Terminal · GNOME Terminal · Alacritty · Warp — el mismo binario JVM + ANSI.

### Variables de entorno útiles

| Variable | Efecto |
|----------|--------|
| `ATROPOS_MODEL_<PROVIDER>` | Modelo por proveedor (ej. `ATROPOS_MODEL_GROQ`) |
| `ATROPOS_INGEST_ROOTS` | Raíces de lectura extra (`:` separadas) |
| `ATROPOS_NO_ANIMATION` | Omite la secuencia de apertura |
| `ATROPOS_ASCII` | UI ASCII en lugar de caracteres de caja |
| `ATROPOS_JAVA_OPTS` | Flags de la JVM |
| `ATROPOS_JAR` | Usa un jar que ya tengas |
| `ATROPOS_VERSION` | Fija la versión del instalador |

```sh
ATROPOS_MODEL_GROQ=llama-3.1-8b-instant atropos
ATROPOS_INGEST_ROOTS=/storage/emulated/0/Download atropos
```

```sh
atropos auth accept AGENTS.md
```

---

## Superficies

| Superficie | Qué obtienes |
|------------|----------------|
| **Terminal / CLI TUI** | Plano de control completo del motor — móvil y escritorio |
| **Web** | Cliente en el navegador sobre el motor local |
| **Android** | APK nativa de alta densidad, una mano |

Un motor. Varias superficies. Mismos proyectos, evidencia y proveedores.

---

## Comandos útiles (terminal)

| Área | Comandos |
|------|----------|
| Orientar | `/help` · `/status` · `/dashboard` · `/providers` · `/keys status` · `/verify` |
| Trabajo | `/factory run <prompt>` · `/factory plan` · `/agent run` · `/self-host run` |
| Proveedores | `/use <provider>` · `/use auto` · `/keys setup` · `/providers live-test` |
| DAG | `/dag status` · `/dag nodes` · `/dag runnable` |
| Recuperación | `/resume` · `/interrupt soft` · `/snapshot capture` |

`/` abre la paleta · flechas navegan · Enter ejecuta · Tab completa · Esc cierra  
Adjunta archivos con `@ruta`

La superficie completa de comandos está en la app (`/help`).

---

## Por qué ATROPOS

| | |
|--|--|
| **Código abierto** | Inspecciona y modifica el cliente — AGPL-3.0 |
| **Multi-agente** | Despacho jerárquico con control de alcance — no un solo hilo de chat |
| **Multi-proveedor** | Claves en el entorno → auto-descubrimiento; prioriza gratis |
| **Larga duración** | Proyectos y checkpoints duraderos — no un agente reactivo de prompts |
| **Multi-superficie** | Terminal, web y Android sobre un motor |
| **Cualquier terminal** | Móvil o escritorio — el mismo binario |

Los proveedores necesitan red. Modelos locales (p. ej. Ollama) funcionan si los configuras.

---

## Licencia

[AGPL-3.0](LICENSE)
