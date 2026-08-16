# Production Orphan Disposition

Generated from the canonical `python3 scripts/find-orphans.py` on 2026-08-16.

This census is a reachability heuristic. `WIRE` means the existing owner must
receive a real reachable call; `MERGE` means the logic belongs in an existing
owner; `DELETE` means the path is superseded or non-authoritative. Rows still
marked WIRE or MERGE are not completion claims.

## Measured Change

| checkpoint | production files | orphan files | orphan LOC |
| --- | ---: | ---: | ---: |
| initial census after restoring the canonical script | 918 | 92 | 5,893 |
| after fake-path deletion and owner wiring | 912 | 76 | 4,844 |
| after self-host, governance, DLOI, verification and provider wiring | 909 | 66 | 3,562 |
| after removal of superseded data/primitive files | 908 | 61 | 3,363 |
| after removal of superseded planners, renderers, clients and compiler stubs | 894 | 39 | 2,191 |
| after removal of superseded intent, security and legacy GC owners | 879 | 30 | 1,888 |
| after removal of remaining legacy storage value objects | 873 | 23 | 1,502 |
| after bridge/proof-owner integration | 872 | 20 | 1,314 |
| after autonomous backlog-owner integration and invariant cleanup | 870 | 17 | 1,207 |
| after verification-advisory and policy-inventory integration | 870 | 16 | 1,174 |
| after foundation-acceptance integration | 870 | 14 | 1,105 |
| after final verification/UI/factory owner wiring (legacy heuristic) | 870 | 0 | 0 |
| strict current census, parser corrected plus SpecGraph/provider/admission wiring | 954 | 8 | 312 |
| after authority-attestation owner wiring | 954 | 7 | 283 |
| after activity How-field projection | 978 | 6 | 233 |
| current strict census after canonical caller reconciliation | 978 | 0 | 0 |

## Remaining Disposition

The current strict census is **0 orphaned files / 0 LOC / 978 native
production files**. No source file was deleted. The parser correction and
canonical caller reconciliation removed the prior false orphan findings
caused by escaped Kotlin quotes, nested declarations, and declarations that
are now referenced by their live owners.

This is only a file-level reachability result. It does **not** mark the
corresponding semantic obligations complete. Provider-table consolidation,
DLOI index consolidation, Phase 20 loop ownership, proposal-model
consolidation, GoalRun model compatibility, and generated self-host marker
ownership remain subject to the unified predicate audit. They must not be
credited merely because a caller now exists.

`AuthorityAttestation` is a thin compatibility facade over `ArtifactHasher`
and is used by the canonical `AuthorityAttestor`. `BridgeEndpoints.kt` is
reachable through `PipelineField`/`ActivityProjection`; its remaining
decomposition concerns are likewise tracked as semantic audit work, not
silently converted into completion.
Compilation and tests remain unrun by instruction.
