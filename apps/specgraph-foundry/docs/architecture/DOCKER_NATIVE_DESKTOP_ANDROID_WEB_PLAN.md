# ATROPOS Portable Surface Plan

Authority: Source Doc 1 `.005`, atom `M006`.

This document records the migration boundary for Docker, native desktop, Android,
and Web without creating a second orchestration engine.

## Canonical ownership

- Domain, policy, DAG, territory, provider, evidence, and durable state remain in
  `src/main/kotlin/atropos/core`.
- CLI remains the sovereign local surface under `src/main/kotlin/atropos/cli`.
- Web, Android, and desktop are presentation/platform adapters over the shared
  contracts in `atropos.core.platform`.
- Docker is an operator packaging surface; it does not own runtime policy or state.

## Migration obligations

1. Platform adapters call shared application contracts rather than duplicating
   orchestration.
2. Repository roots and durable stores resolve through `AtroposRepoRootLocator`.
3. Provider, secret, territory, verification, and evidence owners remain shared.
4. A platform-specific failure is typed at the adapter boundary and cannot bypass
   the shared gates.
5. Packaging and installation proof remain separate from code completion.

## Current implementation boundary

`PlatformAbstraction`, `PlatformAdapter`, `PlatformModels`, and the CLI engine are
the existing owners. Future platform work extends these contracts or adds a thin
adapter; it must not create a second DAG, policy engine, memory root, or provider
registry.
