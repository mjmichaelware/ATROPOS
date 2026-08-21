# Factory Long-Horizon Status

This is an implementation projection of the existing factory path, not a new
authority document or a completion percentage claim.

## Language completion

`FactoryLanguageContract` is shared by `AppGeneratedBehaviorGuard` and
`FactoryCompletionVerifier`. It selects native production/test paths for the
declared language, excludes `static/` from source credit, and reports missing
artifacts using the detected language. Python uses production `.py` files plus
`tests/*.py`; Kotlin retains `src/main/**/*.kt` and `src/test/**/*.kt`.

The Python web outcome was not run in this batch. The code-level outcome is
that a valid Python source/test map can satisfy the language predicates, while
missing either side fails closed; the runtime directory-preservation proof
remains an operator or focused-test execution item.

## B-item disposition

| Item | Status | Existing/new path |
| --- | --- | --- |
| B1 bounded open-DAG loop | WIRED | `FactoryObligationLoop`, `DagStore`, `FactoryRunOrchestrator` |
| B2 acceptance freeze | WIRED | `FactoryAcceptanceFreeze`, `FactoryLineage`, `EvidenceManifest`, completion gate |
| B3 writer/checker separation | EXISTING + WIRED | `AppProjectGenerator`, `AuditorService`, `DirectorService`, `VerifiedCompletionGate` |
| B4 anti-false-finish | WIRED | freeze predicates, `FactoryObligationLoop`, evidence gate before completion status |
| B5 stuck/thrash detection | WIRED | `FactoryProgressGuard` and DAG `BLOCKED` transition |
| B6 cross-session handoff | WIRED | `FactoryRunHandoff` projects DAG/checkpoint state and last good commit |
| B7 territory/branch isolation | EXISTING + PRESERVED | `FactoryHierarchyGate`, `FactoryRunRootGuard`, derived factory branch |
| B8 UI verification | WIRED | `LivePreviewService`; static HTML is `STATIC_CAPTURED_SOFT` and unsupported browser is `SKIPPED_SOFT_BROWSER_UNAVAILABLE` |
| B9 canary ordering | WIRED | `DagStore` readiness and `FactoryObligationLoop.beforeMutation` record runnable roots |
| B10 run economics | WIRED | `FactoryRunEconomics`, journal recorder, `FactoryPlan` |
| B11 phase contract packet | WIRED | `ContextEnvelopeFactory.createForFactory` freeze/open-work/non-goal packet |
| B12 same-oracle repair | EXISTING GATE + BOUND | `FactoryAcceptanceFreeze` is passed into completion; no alternate success marker |
| B13 language-complete completion | WIRED | `FactoryLanguageContract` and completion gate |

## Verification boundary

Focused tests were added for language predicates, acceptance-freeze
determinism, DAG finalization, handoff serialization, and thrash detection.
`git diff --check` is the only verification run for this batch. Gradle,
compile, full tests, JAR, install, browser, and runtime proofs were not run.
SpecGraph/provider dimension fill remains hard-required only when a usable
SpecGraph path is present; a missing `SPECGRAPH_ROOT` records degraded internal
DAG fallback. Lakehouse remains context attachment only and never fills the 16
dimensions.

## Principal fingerprints

```text
FactoryAcceptanceFreeze.kt 39d40144c48c3ae515f82426b4059d8acfcf02c14ad93baa283acc61a3afd9b4
FactoryLanguageContract.kt 2e17141a6aeeeeb244da96608d95f2f8eba3bb4f536c7ece1f7fcd0729a8e875
FactoryObligationLoop.kt 8a3c2290f8e0b2b5cff599592a7836904ff8fc47efe2e4ca7a35240becda7880
FactoryProgressGuard.kt 06665cb2e9d475d390023889375a84155b13b13d2c34f7926888b65b77328aa2
FactoryRunEconomics.kt 46c8e227d6a8e7d916ae2307df0ecffb46479aa212a6129640e2937ea55b681d
FactoryRunHandoff.kt d951e13eb8ae7f36ad8ab06b5faeb3bc1cb11e976fd2cb44e16ea9fa0faaf07a
FactoryRunOrchestrator.kt 21b56e81ba44318badef5686e3675ef96eb35701fd04a24bf15983ab04eb5168
```
