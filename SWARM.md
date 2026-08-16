# ATROPOS Swarm Topology

maxDepth: 1
escalationPath: auditor, director
coordinationCostBound: 500

## Nodes

- director | director | src/main/kotlin/atropos
- auditor | auditor | src/test/kotlin/atropos
- factory | worker | src/main/kotlin/atropos/core/factory
- surface | worker | src/main/kotlin/atropos/cli/ui

This document declares topology only. Repository authority and safety policy
remain owned by `AGENTS.md` and the canonical authority cascade.

Loaded and attested by the canonical `SwarmMdLoader` through `AuthBootstrap`.
