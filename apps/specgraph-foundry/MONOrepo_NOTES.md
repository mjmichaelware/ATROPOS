# SpecGraph Foundry inside ATROPOS monorepo
This directory contains the SpecGraph Foundry source/research/compiler/web codebase imported into ATROPOS as a bounded subsystem.
SpecGraph owns source ingestion, research, Authority Graph compilation, Execution DAG generation, provenance, validation, and handoff export.
ATROPOS owns autonomous execution, agents, worktrees, verification, repair, and long-running runtime behavior.
Do not collapse these responsibilities into one tangled runtime. Use typed contracts/connectors between them.
