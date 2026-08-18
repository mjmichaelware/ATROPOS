<p align="center">
  <img src="docs/assets/atropos-wordmark.svg" alt="ATROPOS" width="480"/>
</p>

<p align="center">
  <strong>Arrêtez de babysitter des agents de codage bavards.</strong>
</p>

<p align="center">
  Travail hiérarchique de longue durée. Projets durables. N'importe quel terminal—mobile ou bureau.
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

## Installation

```sh
curl -fsSL https://raw.githubusercontent.com/mjmichaelware/ATROPOS/main/install.sh | sh
npm install -g @mjmichaelware/atropos

curl -fL -o ~/ATROPOS.jar \
  https://github.com/mjmichaelware/ATROPOS/releases/download/latest/ATROPOS.jar
java -jar ~/ATROPOS.jar
```

**Nécessite Java 17+**

| Plateforme | JDK |
|------------|-----|
| Android (Termux) | `pkg install openjdk-21` |
| Debian/Ubuntu | `sudo apt install openjdk-21-jre-headless` |
| macOS | `brew install openjdk@21` |
| Windows | JDK 17+ + Windows Terminal |

```sh
cd votre-projet
atropos
```

### Variables d'environnement

| Variable | Effet |
|----------|--------|
| `ATROPOS_MODEL_<PROVIDER>` | Modèle par fournisseur |
| `ATROPOS_INGEST_ROOTS` | Racines de lecture extra |
| `ATROPOS_NO_ANIMATION` | Sans séquence d'ouverture |
| `ATROPOS_ASCII` | UI ASCII |
| `ATROPOS_JAVA_OPTS` | Options JVM |
| `ATROPOS_JAR` | Jar existant |
| `ATROPOS_VERSION` | Version d'install |

Dans l'app : `/help` · palette `/` · fichiers avec `@chemin`.

---

## Pourquoi pas un agent de codage de plus

La plupart sont des **sessions pilotées par prompts** : un fil de chat, des étapes réactives, le travail meurt avec la session, et l'agent touche volontiers tout l'arbre.

ATROPOS est construit à l'envers : agents hiérarchiques de longue durée, projets durables, multi-fournisseurs depuis l'env, le même moteur en terminal / web / Android, client open source (AGPL-3.0).

---

## Licence

[AGPL-3.0](LICENSE)
