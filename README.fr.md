<p align="center">
  <img src="docs/assets/atropos-wordmark.svg" alt="ATROPOS" width="480"/>
</p>

<p align="center">
  <strong>De l'autonomie sans la dérive.</strong>
</p>

<p align="center">
  Moteur de codage multi-agents IA open source pour terminal, web et Android.<br/>
  Agents hiérarchiques longue durée · projets durables · <strong>26 fournisseurs</strong>.<br/>
  Agent de codage agentic · BYOK multi-fournisseurs · prêt MCP · plan de contrôle self-hosted.
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

## Installation

```sh
curl -fsSL https://raw.githubusercontent.com/mjmichaelware/ATROPOS/main/install.sh | sh

npm install -g @mjmichaelware/atropos

cd votre-projet && atropos
```

**Java 17+.** Termux : `pkg install openjdk-21` · Debian : `sudo apt install openjdk-21-jre-headless` · macOS : `brew install openjdk@21`

Ajoutez les clés API que vous avez déjà. ATROPOS découvre les fournisseurs au démarrage — vous ne configurez pas les 26. Dans l'app : `/help` · palette `/` · `@chemin` pour joindre des fichiers.

---

## Ce qu'ATROPOS optimise

| Douleur que causent encore la plupart des agents de codage | Ce que fait ATROPOS |
|------------------------------------------------------------|---------------------|
| Éditions autonomes sans contrôle et **dérive d'agent** | **Hiérarchie multi-agents à portée** — territoire à l'affectation ; chemins illégaux bloqués avant écriture |
| **Gaspillage de tokens**, brûlure de **quota** et sous-agents bavards | **Orchestration multi-agents** par politique et état — pas de chat de groupe entre LLM |
| **Verrouillage fournisseur** et enfer d'un seul vendor | **BYOK** · **26 fournisseurs** · routage multi-fournisseurs free-first avec vos clés |
| La session meurt et le long travail est perdu | Projets **autonomes longue durée** avec **checkpoint**, **resume** et continuité au redémarrage |
| Tout fourrer dans une **fenêtre de contexte plate** | Unités de travail **ordonnées en DAG** — atomes à dépendances, pas un méga-prompt |
| Agents parallèles qui se marchent dessus | **Agents parallèles** et dispatch de swarm contrôlé, sans travail circulaire |
| Deviner avec un méga-prompt au lieu d'un vrai plan | **Planification et vérification** via SpecGraph avant exécution |
| Le cloud comme cerveau et clients fermés | Moteur **local-first** · **self-hosted** · **open source** (AGPL-3.0) ; le cloud est un backend LLM optionnel |
| « Terminé » quand le modèle s'arrête de parler | **Achèvement vérifié** — contrôles indépendants avant promotion |
| IDE seul ou terminal seul | **Multi-surfaces** : **CLI** / **TUI** terminal, app web, Android / mobile — un moteur |

---

## SpecGraph et le DAG

**SpecGraph** est l'analyseur de documents et le constructeur de **DAG** dans ATROPOS — une app complète de planification et de vérification embarquée dans le moteur. Il transforme les documents sources en un **graphe orienté acyclique** (task graph / dependency graph) enrichi par la recherche, pour que des agents de codage autonomes à **long horizon** et le swarm multi-agents exécutent un travail d'ingénierie logicielle ordonné au lieu de déverser un produit entier dans une seule **fenêtre de contexte** plate. Checkpoint et resume gardent ce graphe vivant après redémarrage. SpecGraph est la façon dont les vrais blueprints logiciels entrent dans le swarm sans thrash circulaire ni structure hallucinée.

Les fournisseurs ont besoin du réseau sauf si vous configurez un modèle local (p. ex. Ollama).

---

## Licence

[AGPL-3.0](LICENSE)
