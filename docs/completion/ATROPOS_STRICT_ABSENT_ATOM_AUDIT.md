# Strict Absent-Atom Audit

Date: 2026-08-12
Scope: current tracked production source and tests

This is a bounded operator audit input for the code-base accounting generator.
It records exact owner names reported absent from the current production-source
dump. It does not authorize creating duplicate owners where an authority
review later proves a semantic equivalent is the canonical owner.

## Classification

- `MISSING_ATOMIC_OWNER`: count as an open binary implementation obligation.
- `RETIRED_NAME`: intentionally absent; do not count as missing code.
- `VERIFICATION_ONLY`: track outside code completion.

## MISSING_ATOMIC_OWNER

### Phase 19 / App Factory

1. `BoundedWorkExecutor`
2. `BatchGate`
3. `GitHubBinding`
4. `GraphClaimService`
5. `GraphTransitionService`
6. `IntentParser`
7. `FuzzyMatcher`
8. `SuggestionEngine`
9. `HelpRegistry`
10. `CommandHistoryStore`

### Phase 20 / Evaluation and Governance

11. `AntiGamingAuditor`
12. `ReleaseGateEvaluator`
13. `ReproducibilityGate`
14. `ProposalGenerator`

### Phase 8 / Deterministic Verification

15. `OutputValidator`

### Phase 17 / CLI and Cross-Surface Presentation

16. `StickyHeader`
17. `AnimatedThinkingBuffer`
18. `PartialCommandEnterToSelect`
19. `ProviderOneLineSummary`
20. `CopyDownloadResponse`
21. `ResponsiveNativeGrammar`
22. `BaselineSnapshots`
23. `TerritoryAsMaterial`
24. `AttestationOpticalFocus`
25. `RecoveryTectonicRibbon`
26. `ModeRetheme`
27. `EvidenceMorph`

### Phase 18 / Android and Web Surfaces

28. `AndroidBridge`
29. `AndroidEngineBridge`
30. `LocalEngineBridge`
31. `SideloadApk`
32. `ApkSigner`
33. `ComposeAppShell`
34. `ChatListScreen`
35. `ConversationScreen`
36. `ComposerScreen`
37. `CheckpointChip`
38. `ThinkingSheet`
39. `OneHandDensity`
40. `SessionTabModel`
41. `StreamingApprovalCards`
42. `DeveloperToolsContainer`
43. `ViewTransitionEvidence`
44. `WebMergeArchitecture`

## Excluded from code completion

45. `DirectorOrchestrator` — retired/replaced name.
46. `WorkerCodeSynthesizer` — retired/replaced name.
47. `SelfHostInsideOutSandboxProofTest` — verification-only test name.

The strict audit is intentionally conservative. A later authority-coordinate
review may replace an atomic name with an existing canonical owner only when
the owner has direct reachability and independent edge evidence; that review
must amend the registry rather than silently restoring a broad crosswalk.
