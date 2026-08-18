<p align="center">
  <img src="docs/assets/atropos-wordmark.svg" alt="ATROPOS" width="480"/>
</p>

<p align="center">
  <strong>Deja de cuidar agentes de codificación charlatanes.</strong>
</p>

<p align="center">
  Trabajo jerárquico de larga duración. Proyectos duraderos. Cualquier terminal—móvil o escritorio.
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

## Instalación

```sh
curl -fsSL https://raw.githubusercontent.com/mjmichaelware/ATROPOS/main/install.sh | sh
npm install -g @mjmichaelware/atropos

curl -fL -o ~/ATROPOS.jar \
  https://github.com/mjmichaelware/ATROPOS/releases/download/latest/ATROPOS.jar
java -jar ~/ATROPOS.jar
```

**Requiere Java 17+**

| Plataforma | JDK |
|------------|-----|
| Android (Termux) | `pkg install openjdk-21` |
| Debian/Ubuntu | `sudo apt install openjdk-21-jre-headless` |
| macOS | `brew install openjdk@21` |
| Windows | JDK 17+ + Windows Terminal |

```sh
cd tu-proyecto
atropos
```

### Variables de entorno

| Variable | Efecto |
|----------|--------|
| `ATROPOS_MODEL_<PROVIDER>` | Modelo por proveedor |
| `ATROPOS_INGEST_ROOTS` | Raíces de lectura extra |
| `ATROPOS_NO_ANIMATION` | Sin secuencia de apertura |
| `ATROPOS_ASCII` | UI ASCII |
| `ATROPOS_JAVA_OPTS` | Flags JVM |
| `ATROPOS_JAR` | Jar existente |
| `ATROPOS_VERSION` | Versión del instalador |

En la app: `/help` · paleta `/` · archivos con `@ruta`.

---

## Por qué no otro agente de codificación

La mayoría son **sesiones impulsadas por prompts**: un hilo de chat, pasos reactivos, el trabajo muere al cerrar la sesión, y el agente no duda en tocar todo el árbol.

ATROPOS está construido al revés: agentes jerárquicos de larga duración, proyectos duraderos, multi-proveedor desde el entorno, el mismo motor en terminal / web / Android, cliente open source (AGPL-3.0).

---

## Licencia

[AGPL-3.0](LICENSE)
