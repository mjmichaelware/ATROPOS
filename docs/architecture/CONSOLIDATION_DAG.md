# Consolidation DAG

Dependency-ordered implementation atoms for the repository-wide campaign.

```text
CENSUS-01 repository fingerprint and owner registry
  -> WEB-01 canonical web comparison and unique-file inventory
  -> WEB-02 migrate root/deployment/docs references to apps/web
  -> WEB-03 remove tracked .next/build residue and add gate
  -> WEB-04 delete nested copied runtime after web tests
  -> DB-01 compare migration identifiers and applied-history evidence
  -> DB-02 canonicalize future migration directory and write gate
  -> CORE-01 verify protected-domain callers against owner registry
  -> CORE-02 consolidate duplicate policy/registry/evidence paths
  -> DECOUPLE-01 split highest-risk mixed files by responsibility
  -> GATE-01 expand existing ArchitectureComplianceChecker
  -> FINAL-01 full cross-language verification and final metrics
```

`WEB-01` through `WEB-04` are implemented; web runtime verification remains open. `DB-01` remains blocked from deletion until remote applied-history evidence is available. `CORE-01` is the next ready implementation atom and must precede semantic-owner deletion. `GATE-01` cannot approve its own implementation; its independent tests are written by a separate verification lane.

## Safe Deletion Conditions

Every deletion atom must prove caller/configuration/reflection/serialization compatibility, preserve history with `git mv` where applicable, and record the path in `DELETION_MANIFEST.md` before removal.
