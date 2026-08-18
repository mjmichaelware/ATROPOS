<p align="center">
  <img src="docs/assets/atropos-wordmark.svg" alt="ATROPOS" width="480"/>
</p>

<p align="center">
  <strong>Moteur de codage multi-agents IA open source</strong>
</p>

<p align="center">
  Terminal · Web · Android<br/>
  Planifie, édite, vérifie et livre du logiciel sur chaque surface.
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

## Installation

```sh
# Installateur en une ligne
curl -fsSL https://raw.githubusercontent.com/mjmichaelware/ATROPOS/main/install.sh | sh

# npm
npm install -g @mjmichaelware/atropos

# JAR direct
curl -fL -o ~/ATROPOS.jar \
  https://github.com/mjmichaelware/ATROPOS/releases/download/latest/ATROPOS.jar
java -jar ~/ATROPOS.jar
```

**Nécessite Java 17+**

| Plateforme | Installer le JDK |
|------------|------------------|
| Android (Termux) | `pkg install openjdk-21` |
| Debian/Ubuntu | `sudo apt install openjdk-21-jre-headless` |
| macOS | `brew install openjdk@21` |
| Windows | Installer un JDK 17+ et utiliser Windows Terminal |

Python 3.11+ est optionnel (recommandé pour l'atomizer). Pas de `pip install` — l'atomizer est dans le JAR.

Puis, depuis n'importe quel projet, dans **n'importe quel terminal** (mobile ou bureau) :

```sh
cd votre-projet
atropos
```

Termux · iTerm · Windows Terminal · GNOME Terminal · Alacritty · Warp — le même binaire JVM + ANSI.

### Variables d'environnement utiles

| Variable | Effet |
|----------|--------|
| `ATROPOS_MODEL_<PROVIDER>` | Modèle par fournisseur (ex. `ATROPOS_MODEL_GROQ`) |
| `ATROPOS_INGEST_ROOTS` | Racines de lecture supplémentaires (`:` séparées) |
| `ATROPOS_NO_ANIMATION` | Ignore la séquence d'ouverture |
| `ATROPOS_ASCII` | UI ASCII au lieu des caractères de boîte |
| `ATROPOS_JAVA_OPTS` | Options JVM |
| `ATROPOS_JAR` | Utiliser un jar déjà présent |
| `ATROPOS_VERSION` | Épingler la version d'install |

```sh
ATROPOS_MODEL_GROQ=llama-3.1-8b-instant atropos
ATROPOS_INGEST_ROOTS=/storage/emulated/0/Download atropos
```

```sh
atropos auth accept AGENTS.md
```

---

## Surfaces

| Surface | Ce que vous obtenez |
|---------|---------------------|
| **Terminal / CLI TUI** | Plan de contrôle complet du moteur — mobile et bureau |
| **Web** | Client navigateur sur le moteur local |
| **Android** | APK native haute densité, une main |

Un moteur. Plusieurs surfaces. Mêmes projets, preuves et fournisseurs.

---

## Commandes utiles (terminal)

| Zone | Commandes |
|------|-----------|
| Orientation | `/help` · `/status` · `/dashboard` · `/providers` · `/keys status` · `/verify` |
| Travail | `/factory run <prompt>` · `/factory plan` · `/agent run` · `/self-host run` |
| Fournisseurs | `/use <provider>` · `/use auto` · `/keys setup` · `/providers live-test` |
| DAG | `/dag status` · `/dag nodes` · `/dag runnable` |
| Récupération | `/resume` · `/interrupt soft` · `/snapshot capture` |

`/` ouvre la palette · flèches naviguent · Entrée exécute · Tab complète · Échap ferme  
Joindre des fichiers avec `@chemin`

La surface complète des commandes est dans l'app (`/help`).

---

## Pourquoi ATROPOS

| | |
|--|--|
| **Open source** | Inspecter et modifier le client — AGPL-3.0 |
| **Multi-agents** | Dispatch hiérarchique avec contrôle de portée — pas un seul fil de chat |
| **Multi-fournisseurs** | Clés dans l'env → auto-découverte ; priorité au gratuit |
| **Longue durée** | Projets et checkpoints durables — pas un agent réactif piloté par prompts |
| **Multi-surfaces** | Terminal, web et Android sur un seul moteur |
| **Tout terminal** | Mobile ou bureau — le même binaire |

Les fournisseurs ont besoin du réseau. Les modèles locaux (ex. Ollama) fonctionnent une fois configurés.

---

## Licence

[AGPL-3.0](LICENSE)
