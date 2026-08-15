# Task: wire every production Kotlin file that has no callers

## What "no callers" means

A production file is ORPHANED when **no other file under `src/main/kotlin`
references any symbol it declares**. It exists, it compiles, it usually has a
test, and it never executes. It counts toward the LOC total and does nothing.

Reproduce the list exactly with this script (run from the repo root):

```python
# scripts/find-orphans.py
import os, re, json
ROOT = "src/main/kotlin"
files = [os.path.join(d, f) for d, _, fs in os.walk(ROOT) for f in fs if f.endswith(".kt")]
DECL = re.compile(
    r'^(?:@\w+(?:\([^)]*\))?\s*)*'
    r'(?:public\s+|internal\s+|sealed\s+|abstract\s+|open\s+|data\s+|value\s+|'
    r'annotation\s+|inline\s+|suspend\s+)*'
    r'(?:class|object|interface|enum\s+class|fun|val|const\s+val|typealias)\s+'
    r'([A-Za-z_][A-Za-z0-9_]*)', re.M)
src = {p: open(p, encoding="utf-8", errors="replace").read() for p in files}
decls = {p: {m.group(1) for line in t.splitlines() if not line.startswith((" ", "\t"))
             for m in [DECL.match(line)] if m} for p, t in src.items()}
toks = {p: set(re.findall(r'\b[A-Za-z_][A-Za-z0-9_]*\b', t)) for p, t in src.items()}
orphans = [(p, len(src[p].splitlines()), sorted(n)) for p, n in decls.items()
           if n and not any(n & toks[q] for q in files if q != p)]
for p, loc, names in sorted(orphans, key=lambda x: -x[1]):
    print(f"{loc:5d}  {p}  :: {','.join(names)}")
print(f"\n{len(orphans)} orphaned, {sum(o[1] for o in orphans)} LOC")
```

Baseline at the time of writing: **65 orphaned files, 3,475 LOC, out of 920
production files.** The number must reach as close to zero as the rules below
allow, and every file you leave must be listed with a reason.

## Rules that bind this task

These come from `AGENTS.md` §0 and are not negotiable.

- **§0.7 non-duplication.** Extend or compose the *existing* semantic owner.
  Never create a second DAG, verifier, territory system, memory root, provider
  registry, or lakehouse. Before writing any new file, grep for the owner
  first. A new aggregator class that "runs all the orphans" is the exact
  failure mode this rule exists to stop — it satisfies the caller count and
  violates the law.
- **§0.6 no fake VERIFIED.** Some of these files fabricate their results (see
  the DO-NOT-WIRE list). Calling them from production would put a false
  VERIFIED into the engine. Wiring them is worse than leaving them dead.
- **§0.4 / §7 atomic decoupling.** One file, one responsibility, and prefer
  extend-in-place over new files.
- **§0.9 evidence over narrative.** Every claim names paths, line deltas, and
  which acceptance predicate moved.
- **§0.10** append a Progress Ledger row to `AGENTS.md` §2 after each batch.
  Append only — never rewrite a prior row.

## The three legitimate outcomes per file

For each orphan, exactly one of:

1. **WIRE** — find the real call path and put it on it. Not a demo command,
   not a new "registry" that exists to hold it: the place a user or another
   subsystem actually reaches it. Say which command or code path now reaches
   it.
2. **MERGE** — it duplicates an existing owner. Fold its logic into that owner
   and delete the file. Name the owner.
3. **DELETE** — it is dead, fabricated, or superseded. Delete it and its test.
   Say why.

"Wired" means a production file under `src/main/kotlin` other than itself now
references it, **and** that reference is reachable from `Main.kt`, a
`CommandRouter` branch, a `BridgeRoutes` handler, or an engine phase. Re-run
the script to prove the count dropped.

## DO NOT WIRE — these fabricate results (§0.6)

Each of these returns a hardcoded pass. Calling them from production injects a
false VERIFIED. Decide MERGE or DELETE for each, or implement them for real —
but do not simply give them a caller.

| File | What it fakes |
| --- | --- |
| `core/phase20/Phase20Loop.kt` | `executeAmendment` returns `amendment.copy(verified = true)`; comment says "Simulates". Duplicates the real `SelfImprovementLoop`. |
| `core/verification/IntegrityProofs.kt` | Every proof returns a literal `"hash-selfhost"`, `"hash-greenfield"`, … as its evidence hash. |
| `core/acceptance/EvaluationSpecIntegration.kt` | `runSpec()` returns `passed = true, coverage 0.85` as literals. |
| `core/integration/AgplPerimeter.kt` | `PipedStreamRouter.routePipedCommand` returns the string `"ResultOf(cmd) on (input)"` instead of executing anything. |
| `core/integration/AdversarialValidator.kt` | Same shape — verify before wiring. |

## The 65 files

Grouped by the subsystem that most likely owns the call path. LOC first.

### Phase 20 governance (7)
These lost their caller when a duplicate lakehouse facade was reverted for
§0.7. `Phase20GovernanceService` already holds a shared `EvidenceStore` — pass
it to these rather than letting each construct its own, which is why they were
never usable together.

```
 40  core/phase20/AmendmentRegistry.kt
 30  core/phase20/EvidenceLedger.kt
 29  core/phase20/ManifestBuilder.kt
 27  core/phase20/ProposalStore.kt
 26  core/phase20/LakehouseRetrieve.kt
 26  core/phase20/MemoryLedger.kt
 24  core/phase20/SelfBuildValidationRule.kt
```

### Verification (13)
`DeterministicChecks` / `DeterministicVerifier` is the existing owner — extend
it, do not add a second verifier. `/verify structural` already routes there.

```
 81  core/verification/PrecedenceLattice.kt        role x token x action lattice
 74  core/verification/SourceDoc2Rules.kt          Rule127/129/137/142
 40  core/verification/TerritoryGrant.kt           path grants + drift detection
 33  core/verification/RiskyStdlibScanner.kt
 32  core/verification/BatchReporter.kt            line/addition/deletion counts
 31  core/verification/UiParityVerifier.kt
 29  core/verification/AuthorityAttestation.kt     likely duplicates AuthBootstrap — check
 27  core/verification/GoalInvariantSet.kt
 24  core/verification/AcceptanceVelocity.kt
 20  core/verification/CompletionCalculus.kt       min() of impl/integration/verify/evidence
 20  core/verification/AdmissionController.kt      refuses config that disables an invariant
 10  core/verification/AssertionNaming.kt
 29  core/acceptance/FinalSD1SD2Acceptance.kt
```

### Provider (9)
`ProviderFactory`, `RoutePolicy`, `StatusQuotaRenderer`, `ProviderChatDispatcher`
are the live paths.

```
141  core/provider/ContextEnvelopeAttestor.kt
130  core/provider/ProviderPreferenceOrder.kt
 88  core/provider/ImmutablePrompt.kt
 78  core/provider/FallbackChainRegistry.kt
 77  core/ProviderHttpClient.kt
 60  core/provider/EligibilityAlgorithm.kt
 28  core/provider/TypedFailureStates.kt
 10  core/provider/ProviderDescriptorReport.kt
  9  core/provider/adapter/AdapterModel.kt
```

### Self-host / agent (5)
`SelfHostCommand`, `SelfHostGoalService`, `SelfHostAutonomousRunner`.

```
130  cli/commands/SelfHostChangeRequestClassifier.kt
128  core/agent/SelfHostMutationVerificationGate.kt
 66  core/agent/SelfHostInstalledProofEvidence.kt
 37  core/agent/SelfHostGoalRecoveryOrchestrator.kt
 60  core/autonomous/AutonomousBacklogManager.kt
```

### Intent / NL (5)
`NlEntryPipeline` is now the CLI's NL front door (`CommandRouter`) — these
belong on that path.

```
 65  core/intent/MessyIntentParser.kt
 38  core/intent/Sd5HumanIntentInvariants.kt
 35  core/intent/MentionExtractor.kt
 20  core/intent/Sd5B0XValidator.kt
  9  core/intent/IntentEnvelope.kt
```

### DLOI / SpecGraph (3)
The residual doc flags `SourceAuthorityLaw` and `TermuxPathResolver` as
"wire into SourceAuthorityIndexer". `HandoffDagTranslator` should be reached
by the SpecGraph handoff ingest path.

```
223  core/specgraph/HandoffDagTranslator.kt
221  dloi/SourceAuthorityLaw.kt
 36  dloi/TermuxPathResolver.kt
```

### Security (2)
`RedactionFilter` / `SecretEnrollment` / `EvidenceStore` are the live path.
Note: redaction *masks*; `SecretEgressGate` *detects* — they are complementary,
not duplicates.

```
 46  core/security/SecretEgressGate.kt
 36  core/security/SecretSinkMatrix.kt
```

### UI (3)
`AnsiTerminalEngine` / `TerminalRenderingFacade` / `StatusBarRenderer`.

```
 61  cli/ui/ComposerRenderer.kt      likely duplicates ComposerViewport — check
 43  cli/ui/DagReactorRenderer.kt    takes a Doc 4 status term; wire to DAG status
 41  cli/ui/design/HoeInspectors.kt
```

### Factory (3)
`AppFactoryRouter` / `FactoryCommandHandler` / `AppProjectGenerator`.

```
 26  core/factory/AppDatabaseSecurityPlanner.kt
 26  core/factory/AppAuthPlanner.kt
 24  core/factory/AppBackendIntegrationPlanner.kt
```

### Evaluation / platform / misc (11)
```
176  core/territory/SubagentSpawnService.kt
133  core/evaluation/EvaluationDashboard.kt
 97  core/preview/PreviewEvidence.kt
 96  core/evaluation/AtroposMetrics.kt
 40  core/ast/MdpCompilerState.kt
 29  core/time/SystemClock.kt              RealClock/TestClock; many files use java.time.Clock directly
 28  core/output/OutputModeDetector.kt
 26  core/platform/PlatformWire.kt
 11  core/multimodal/LivePreviewEvidenceService.kt
  2  core/adapter/HardwareProfileAdapter.kt
```

## Build gotchas on this tree

- `gradle.properties` pins `-Xmx768m` for Termux. On a full desktop checkout
  the Kotlin frontend OOMs on `AgentQueueWorkRunner.kt`. **Do not edit
  gradle.properties** — override on the command line:

  ```
  ./gradlew compileKotlin compileTestKotlin test --offline \
    -Dorg.gradle.jvmargs="-Xmx3g -XX:MaxMetaspaceSize=768m -Dfile.encoding=UTF-8" \
    -Pkotlin.daemon.jvmargs="-Xmx3g -XX:MaxMetaspaceSize=768m"
  ```

- Tests use `kotlin.test`, **not** JUnit Jupiter. `org.junit.jupiter` is not on
  the classpath; importing it breaks `compileTestKotlin`, which takes the whole
  suite down, not just that file. `assertDoesNotThrow` and `assertArrayEquals`
  live in `src/test/kotlin/atropos/testing/JvmAssertions.kt`.
- Current green baseline: **2109 tests, 0 failures.** Do not commit below it.
- `git commit -F -` with a heredoc; quoted phrases break `-m`.

## Definition of done

1. Re-running the orphan script prints a materially lower count, and every
   remaining entry is listed in your report with WIRE / MERGE / DELETE and a
   reason.
2. `./gradlew compileKotlin compileTestKotlin test` is green, with ≥ 2109 tests
   and 0 failures.
3. No new file exists that duplicates an existing semantic owner (§0.7).
4. Nothing that fabricates a result was given a production caller (§0.6).
5. `AGENTS.md` §2 has an appended ledger row naming paths, line deltas, the
   before/after orphan count, and the verification command you actually ran.
