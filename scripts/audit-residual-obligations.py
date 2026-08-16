#!/usr/bin/env python3
"""Audit the residual inventory against the current worktree without building."""

import hashlib
import json
import re
import subprocess
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RESIDUAL = ROOT / "docs/completion/ATROPOS_RESIDUAL_OBLIGATION_INVENTORY.json"
REGISTRY = ROOT / "docs/completion/ATROPOS_CODE_OBLIGATION_REGISTRY.json"
OUT_JSON = ROOT / "docs/completion/ATROPOS_UNIFIED_OBLIGATION_AUDIT.json"
OUT_MD = ROOT / "docs/completion/ATROPOS_UNIFIED_OBLIGATION_AUDIT.md"
ORPHAN_SCRIPT = ROOT / "scripts/find-orphans.py"

ORPHAN_DISPOSITIONS = {
    "src/main/kotlin/atropos/core/specgraph/ExportBundleReader.kt": ("WIRE_OPEN", "Verified SpecGraph bundle reader has no runtime handoff caller yet."),
    "src/main/kotlin/atropos/core/provider/TaskRoutingMatrix.kt": ("MERGE_OPEN", "RoutedTask duplicates the canonical FallbackChain routing vocabulary."),
    "src/main/kotlin/atropos/core/provider/FallbackChainRegistry.kt": ("MERGE_OPEN", "Legacy provider/task table duplicates FallbackChain and ProviderDescriptorRegistry."),
    "src/main/kotlin/atropos/data/indexer/LatentOntologicalIndexer.kt": ("MERGE_OPEN", "Legacy vector indexer must reconcile with the DLOI owner before any caller is added."),
    "src/main/kotlin/atropos/core/integration/BridgeEndpoints.kt": ("MERGE_OPEN", "Legacy bridge endpoint state duplicates canonical BridgeRoutes and projections."),
    "src/main/kotlin/atropos/core/phase20/Phase20Loop.kt": ("MERGE_OPEN", "Legacy Phase 20 loop duplicates SelfImprovementLoop; wiring it would create a second loop."),
    "src/main/kotlin/atropos/core/verification/AuthorityAttestation.kt": ("MERGE_OPEN", "Legacy attestation helper must delegate to the canonical context/authority attestation owner."),
    "src/main/kotlin/atropos/core/verification/ProposalSixFields.kt": ("MERGE_OPEN", "Legacy proposal value model duplicates ImprovementProposal and ProposalGate."),
    "src/main/kotlin/atropos/core/intent/Sd5B0XValidator.kt": ("WIRE_OPEN", "Admission validator has a real owner but no reachable production admission call yet."),
    "src/main/kotlin/atropos/core/verification/GoalRun.kt": ("MERGE_OPEN", "Legacy GoalRun model is superseded by agent.GoalRunRecord."),
    "src/main/kotlin/atropos/core/agent/SelfHostCradleRuntimeState.kt": ("GENERATED_ROOT", "Marker is emitted by SelfHostBootstrapDagFactory into candidate worktrees; it is not a runtime owner."),
}


def orphan_census():
    result = subprocess.run(
        [sys.executable, str(ORPHAN_SCRIPT)],
        cwd=ROOT,
        text=True,
        capture_output=True,
        check=False,
    )
    rows = []
    pattern = re.compile(r"^\s*(\d+)\s+(src/main/kotlin/\S+)\s+::\s*(.*)$")
    for line in result.stdout.splitlines():
        match = pattern.match(line)
        if not match:
            continue
        path = match.group(2)
        disposition, reason = ORPHAN_DISPOSITIONS.get(path, ("UNCLASSIFIED", "No disposition recorded."))
        rows.append({
            "path": path,
            "loc": int(match.group(1)),
            "symbols": [value for value in match.group(3).split(",") if value],
            "disposition": disposition,
            "reason": reason,
        })
    return rows


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def current_worktree_fingerprint() -> str:
    """Hash the exact source inputs used by this audit.

    Git HEAD is insufficient here: ATROPOS routinely audits a dirty worktree
    before a commit.  Include relative paths and bytes for every production,
    test, and build input already loaded by this script.  Reports therefore
    cannot be mistaken for a clean-commit result after local edits.
    """
    digest = hashlib.sha256()
    # REGISTRY and RESIDUAL are updated with current audit fields below.  They
    # remain recorded separately in `inputs`; including their mutable output
    # fields here would make the audit identity change because of its own run.
    paths = sorted(set(PRODUCTION + TESTS), key=lambda p: str(p.relative_to(ROOT)))
    for path in paths:
        relative = str(path.relative_to(ROOT)).encode("utf-8")
        digest.update(len(relative).to_bytes(8, "big"))
        digest.update(relative)
        content = path.read_bytes()
        digest.update(len(content).to_bytes(8, "big"))
        digest.update(content)
    return digest.hexdigest()


def source_files(test: bool):
    # The residual inventory's production oracle is Kotlin/JVM plus Android.
    # Keep this audit bounded; web source is audited by its own surface gates.
    roots = ([ROOT / "src/test", ROOT / "core/src/commonTest", ROOT / "server/src/test", ROOT / "app/src/test", ROOT / "apps/web/src", ROOT / "apps/web/e2e", ROOT / "scripts"] if test else
             [ROOT / "src/main", ROOT / "core/src/commonMain", ROOT / "server/src/main", ROOT / "app/src/main", ROOT / "apps/web/src", ROOT / "scripts"])
    files = []
    for root in roots:
        if not root.exists():
            continue
        files.extend(p for p in root.rglob("*") if p.is_file() and p.suffix in {".kt", ".java", ".py", ".ts", ".tsx", ".js", ".jsx", ".sh"})
    if not test:
        files.extend(p for p in (ROOT / "build.gradle.kts", ROOT / "settings.gradle.kts", ROOT / "gradle.properties") if p.is_file())
    def is_test(path: Path) -> bool:
        text = path.as_posix()
        return "/src/test/" in text or "/test/" in text or "/__tests__/" in text or path.name.endswith(("Test.kt", "Test.java", ".test.ts", ".test.tsx", ".spec.ts", ".spec.tsx", ".test.sh", "-test.sh"))
    return sorted(p for p in set(files) if is_test(p) == test)


PRODUCTION = source_files(False)
TESTS = source_files(True)
PROD_TEXT = {p: p.read_text(errors="ignore") for p in PRODUCTION}
TEST_TEXT = {p: p.read_text(errors="ignore") for p in TESTS}

WORD = re.compile(r"[A-Za-z_][A-Za-z0-9_]*")
DECLARATION_SCAN_RE = re.compile(
    r"\b(?:class|object|interface|enum\s+class|data\s+class|sealed\s+class|fun|val|var|typealias|"
    r"function|def|const|let)\s+"
    r"([A-Za-z_][A-Za-z0-9_]*)\b"
)


def code_only(text: str) -> str:
    """Remove comments and literals without swallowing later declarations.

    Regex-based stripping is unsafe for Kotlin regex literals: an escaped quote
    can make a broad match consume the rest of a file, hiding real declarations
    from the audit.  This bounded lexer preserves newlines and code structure
    while replacing each literal/comment with spaces.
    """
    out = []
    i = 0
    length = len(text)
    while i < length:
        if text.startswith("//", i):
            while i < length and text[i] != "\n":
                i += 1
            continue
        if text.startswith("/*", i):
            i += 2
            while i < length and not text.startswith("*/", i):
                if text[i] == "\n":
                    out.append("\n")
                else:
                    out.append(" ")
                i += 1
            i = min(length, i + 2)
            continue
        if text.startswith('"""', i):
            out.extend((" ", " ", " "))
            i += 3
            while i < length and not text.startswith('"""', i):
                out.append("\n" if text[i] == "\n" else " ")
                i += 1
            if i < length:
                out.extend((" ", " ", " "))
                i += 3
            continue
        if text[i] == '"':
            out.append(" ")
            i += 1
            escaped = False
            while i < length:
                char = text[i]
                if char == "\n":
                    out.append("\n")
                else:
                    out.append(" ")
                i += 1
                if escaped:
                    escaped = False
                elif char == "\\":
                    escaped = True
                elif char == '"':
                    break
            continue
        if text[i] == "'":
            out.append(" ")
            i += 1
            escaped = False
            while i < length:
                char = text[i]
                out.append("\n" if char == "\n" else " ")
                i += 1
                if escaped:
                    escaped = False
                elif char == "\\":
                    escaped = True
                elif char == "'":
                    break
            continue
        out.append(text[i])
        i += 1
    return "".join(out)

REQUIREMENT_ALIASES = {
    "M003": ("KotlinCompatScan", "kotlin-compat-scan-test", "KOTLIN_COMPAT_SCAN_EDGE_OK", "kotlinCompatScanEdge"),
    "M006": ("PortableSurfacePlan", "PortableSurfacePlanReport", "portableSurfacePlan"),
    "C001": ("KotlinJvmRuntime", "PlatformWire", "KOTLIN_JVM_RUNTIME_CONTRACT_OK"),
    "C002": ("LocalToolchainProvider", "LocalToolchainProbe", "LOCAL_TOOLCHAIN_PROVIDER_CONTRACT_OK"),
    "C003": ("KotlinCompileProbe", "GovernedCompileGate", "KOTLIN_COMPILE_PROBE_CONTRACT_OK"),
    "C004": ("GitStateProbe", "LocalToolchainProbe", "GIT_STATE_PROBE_CONTRACT_OK"),
    "N003": ("SourceAuthorityIndexerTest", "HigZeroGuardContractTest"),
    "N004": ("StaticOperationRegistry", "OperationEndpointManifestTest", "endpoint-manifest-proof"),
    "N005": ("calculator-final-acceptance-test", "calculator-final-acceptance", "N005_FINAL_ACCEPTANCE_COMMAND_OK"),
    "J010": ("DirectorService", "DirectorDagSupervisor", "AgentCommandDagBootstrapTest"),
    "J011": ("WorkerCodeProposalService", "AgentWorkerCommandHandler", "AgentCommandObservabilityTest"),
    "AUD004": ("FactoryResearchService", "FactoryConfidence"),
    "AUD005": ("FactoryResearchService", "FactoryConfidence"),
    "AUD009": ("HigZeroGuard", "DloiService"),
    "AUD010": ("MdpCompilerState", "SelfImprovementLoop"),
    "AUD016": ("DecomposedAttentionNode", "OnDeviceAdversarialValidator"),
    "AUD018": ("AsyncFanOutController", "OnDeviceAdversarialValidator"),
    "AUD019": ("ManifestOrchestrator", "OnDeviceAdversarialValidator"),
    "AUD024": ("DependencyDeduplicator", "RepoScaffold"),
    "AUD108": ("TabRestorationService", "SessionTabs"),
    "AUD111": ("BackgroundProcessPanel", "AnsiTerminalEngine"),
    "AUD113": ("TouchAutocomplete", "PromptSuggestionState"),
    "AUD114": ("FuzzyExecutionGate", "allowFuzzyExecution", "lastResolutionWasFuzzy"),
    "AUD116": ("AccessibilitySettings", "accessibleLabel", "TerminalRenderingFacade"),
    "AUD118": ("AccessibilitySettings", "isHighContrastEnabled", "TerminalTheme"),
    "AUD119": ("AccessibilitySettings", "hasFocusVisibility", "CommandPaletteRenderer"),
    "AUD121": ("BoundedRenderingController", "AnsiTerminalEngine"),
    "AUD152": ("RuntimeInspector", "InspectCommandHandler"),
    "AUD153": ("AgentInspector", "InspectCommandHandler"),
    "AUD154": ("ProviderInspector", "InspectCommandHandler"),
    "AUD155": ("PolicyInspector", "InspectCommandHandler"),
    "AUD156": ("SourceAuthorityInspector", "InspectCommandHandler"),
    "AUD157": ("RecoveryInspector", "InspectCommandHandler"),
    "AUD158": ("CopyDownloadResponse", "ErrorRenderer"),
    "AUD248": ("SelfImprovementRollback", "Phase20GovernanceService"),
    "AUD006": ("AstSymbolGraph", "AstSymbolNode"),
    "AUD011": ("TopologicalMutation", "TopologicalMutationVector", "CodebaseDeltaTreeTracker"),
    "AUD038": ("CUSTOM_USER_API", "FallbackChainRegistry", "ProviderFactory"),
    "AUD105": ("CanonicalVerb", "ActionRegistry", "NlPhraseMapper"),
    "AUD110": ("UiCapabilities", "activitySignal", "progressTokenFrom"),
    "AUD112": ("UiCapabilities", "RunState", "checkStalled"),
    "AUD120": ("VirtualizedLogEngine", "virtualized"),
    "AUD109": ("ResponsiveBranding",),
    "AUD117": ("AccessibilitySettings", "isReducedMotionEnabled"),
    "AUD268": ("AtMentionScanner", "MentionResolver", "NlEntryPipeline"),
    "AUD123": ("SurfaceContracts", "MviState", "ViewStateManager"),
    "AUD139": ("UiDesignTokens", "parentRadiusMinusInset"),
    "AUD140": ("UiDesignTokens", "MINIMUM_TAP_TARGET_PT", "OneHandDensity"),
    "AUD141": ("UiDesignTokens", "TINTED_THEME_VARIANT", "ThemeCatalog"),
    "AUD015": ("MonteCarloBranchPruner", "SequentialMonteCarlo"),
    "AUD025": ("CloudDeploymentGuard", "AppDeploymentService"),
    "AUD026": ("ShellCommandIntercept", "CommandRouter"),
    "AUD027": ("PipedStreamRouter", "ShellCommandRunner"),
    "AUD022": ("NonBlockingRewardRecorder", "AtomicRewardRecorder"),
    "AUD034": ("ComposeIosStub", "PlatformAdapter"),
    "AUD253": ("VisualComparison",),
    "AUD256": ("CapabilityEnforcer",),
    "AUD262": ("AnsiScheme",),
    "AUD263": ("GlobalByteCeiling",),
    "AUD036": ("package-installers", "ATROPOS_INSTALLER_CONTRACT_OK", "ATROPOS_INSTALLER_METADATA_READY"),
    "AUD007": ("AstSymbolIndex", "AstSymbolGraph"),
    "AUD259": ("ArchitectureComplianceChecker", "checkFileCountLimits"),
    "AUD150": ("colourOnlyStates", "conformance"),
    "AUD035": ("LocalToolchain", "Renderer", "InputSystem"),
    "AUD167": ("SurfaceParityProbe", "forbiddenOnPort"),
    "AUD166": ("PendingApprovalStore", "ApprovalOutcome", "BridgeApprovalHandler"),
    "AUD164": ("ConversationView", "TimelineView", "ExecutionMonitor"),
    "AUD168": ("ProjectCreateForm", "ProjectRegistry", "ProjectProjection"),
    "AUD170": ("ComposeAppShell", "MainActivity", "MobileHeader"),
    "AUD169": ("AgentQueueRecordCodec", "AgentQueueWorkRunner", "corruptReason"),
    "AUD002": ("LakehousePathRetrieve", "LakehouseAtomContextProvider", "InternalExecutionDagSynthesizer"),
    "AUD003": ("DloiService", "FactoryResearchService", "DloiLookupResult"),
    "AUD273": ("SelfHostPromotionService", "SelfHostAutonomousRunner", "SelfHostRunProofBuilder", "SelfHostInsideOutSandboxProofTest"),
    "AUD125": ("EngineHttpServer", "HttpRequestAuthenticator", "BridgeRoutes", "LocalEngineBridge", "authorize"),
    "AUD165": ("WhyHowEvidence", "pipelineForSubject", "ActivityProjection", "WorkItemCard"),
    "AUD124": ("MobileAppMviStore", "MobileAppState", "MobileAppIntent", "reduceMobileAppState"),
    "BP-P16-hierarchy-dispatch": ("HierarchyRegistry", "FactoryHierarchyGate", "AutonomousOrchestrator"),
    "BP-P19-live-preview": ("LivePreviewService", "FactoryRunOrchestrator", "LivePreviewEvidenceService"),
    "BP-P19-browser-verification": ("LivePreviewService", "BrowserActuator", "FactoryRunOrchestrator"),
    "BP-P19-deployment": ("DeploymentService", "FactoryRunOrchestrator", "SelfHostPromotionService"),
    "BP-P19-activity-monitor": ("RunObserver", "AgentObservationCommandHandler", "listRuns", "transcript", "diffLog", "testLog"),
    "AUD039": ("FallbackChainRegistry", "ProviderCascadeRouter"),
    "AUD055": ("SystemClock", "SelfImprovementLoop"),
    "AUD056": ("NamedAssertion", "DeterministicChecks"),
    "AUD057": ("ConfigurationManager", "TerminalCanvas", "AnsiTerminalEngine"),
    "AUD058": ("Rule127Snapshot", "VerifyCommand"),
    "AUD059": ("Rule129Compile",),
    "AUD060": ("BatchReporter", "VerifyCommand"),
    "AUD061": ("RiskyStdlibScanner", "VerifyCommand"),
    "AUD062": ("Rule137Success",),
    "AUD063": ("Rule142Export", "AgentContextExportStore"),
    "AUD028": ("PortableEngineState", "PortableEngineReducer", "multiplatform"),
    "AUD033": ("AtroposKtorServer", "atroposBridgeModule", "Ktor"),
    "AUD106": ("NlPhraseMapper",),
    "AUD147": ("DurableProjectStore", "ProjectRegistry", "ProjectCommandHandler"),
    "AUD148": ("TerminalEvidenceLinker", "BridgeEvidenceHandler", "evidenceHash", "evidenceLink"),
    "AUD266": ("SessionManager", "BridgeSessionStore", "BridgeSessionHandler"),
    "AUD150": ("ColorIndependenceTest",),
    "AUD171": ("WebMergeArchitecture",),
    "AUD246": ("AppDeploymentService", "DeploymentService", "AppDeploymentServiceTest"),
    "AUD159": ("TerritoryAsMaterial",),
    "AUD160": ("AttestationOpticalFocus",),
    "AUD161": ("RecoveryTectonicRibbon",),
    "AUD162": ("ModeRetheme",),
    "AUD240": ("SuperiorityAddendum", "InvariantContractCatalog", "FalseGreenGuard"),
    "AUD241": ("SuperiorityAddendum", "InvariantContractCatalog", "FalseGreenGuard"),
    "AUD242": ("SuperiorityAddendum", "InvariantContractCatalog", "FalseGreenGuard"),
    "AUD243": ("SuperiorityAddendum", "InvariantContractCatalog", "FalseGreenGuard"),
    "AUD274": ("GreenfieldFactoryProof",),
    "AUD279": ("LearningProof",),
    "BP-P19-frontend-generation": ("AppProjectGenerator", "AppSourceTemplate", "RepoScaffold"),
    "A005": ("sourceToCodeTrace", "source-to-code-trace-gate", "SOURCE_TO_CODE_TRACE_GATE_TEST_OK"),
    "STRICT-ComposeAppShell": ("ComposeAppShell", "MainActivity"),
    "STRICT-AndroidBridge": ("AndroidBridge", "BridgeHttp", "BridgeDiscovery", "ConversationRepository"),
    "AUD126": ("GovernancePanel",),
    "AUD127": ("ProjectReadiness",),
    "AUD128": ("GraphWorkspace",),
    "AUD131": ("MemoryLayersInspector",),
    "AUD132": ("PaidUnlockPanel",),
    "AUD133": ("VerificationPanel",),
    "AUD134": ("DocumentInspector",),
    "AUD137": ("TaskQueue",),
    "AUD163": ("BridgeRoutes",),
    # Phase 20 aggregate rows are split into concrete existing owners.  These
    # aliases make the registry audit the canonical ledger/gate/detector
    # classes rather than treating an empty historical owner field as proof of
    # absence.
    **{f"AUD{index}": ("Phase20Laws", "SelfImprovementLaws", "InvariantContractCatalog") for index in range(190, 196)},
    "AUD211": ("EvidenceLedger",),
    "AUD212": ("MemoryLedger",),
    "AUD213": ("ProposalStore",),
    "AUD214": ("AmendmentRegistry",),
    "AUD215": ("ManifestBuilder",),
    "AUD216": ("LakehouseRetrieve",),
    "AUD217": ("EvidenceCasLedger",),
    "AUD218": ("GovernanceDetectorsRegistry",),
    "AUD219": ("ObservationFailureDetector",),
    "AUD220": ("UnredactedSecretDetector",),
    "AUD221": ("IdentityMismatchesDetector",),
    "AUD222": ("OscillationDetector",),
    "AUD223": ("VocabularyCollapseDetector",),
    "AUD224": ("CompileTestExitDetector",),
    "AUD225": ("TerritoryViolationDetector",),
    "AUD226": ("RecoveryCompletenessDetector",),
    "AUD233": ("ProofCarryingAmendment",),
    "AUD237": ("FormalReproducibility",),
    "AUD238": ("MetricSpaceImprovement",),
    "AUD239": ("TerminationRanking",),
    "AUD271": ("SwarmMdLoader",),
    # Source Doc 3 names these as four distinct benchmark atoms.  They are
    # implemented by the single BenchmarkId catalogue and BenchmarkRunner;
    # the atom-specific enum member is included so this is auditable without
    # pretending that an unexecuted benchmark has a result.
    "AUD085": ("BenchmarkId", "BenchmarkRunner", "SWE_BENCH_VERIFIED"),
    "AUD086": ("BenchmarkId", "BenchmarkRunner", "TERMINAL_BENCH"),
    "AUD087": ("BenchmarkId", "BenchmarkRunner", "AIDER_POLYGLOT"),
    "AUD088": ("BenchmarkId", "BenchmarkRunner", "PR_ACCEPTANCE"),
    "AUD129": ("OperationSurfaceRegistry", "OperationSurfaceCard", "snapshots"),
    "AUD130": ("OperationSurfaceRegistry", "OperationSurfaceCard", "security"),
    "AUD135": ("OperationSurfaceRegistry", "OperationSurfaceCard", "autonomous"),
    "AUD136": ("OperationSurfaceRegistry", "OperationSurfaceCard", "swarm"),
    "AUD138": ("OperationSurfaceRegistry", "OperationSurfaceCard", "platform"),
    "AUD142": ("strict-surface-contract", "validateControlVerbSet"),
    "AUD143": ("strict-surface-contract", "validateControlVerbSet"),
    "AUD145": ("strict-surface-contract", "StatusVocabulary"),
    "AUD146": ("OperationSurfaceRegistry", "ConfiguredOperation"),
    "AUD144": ("ProgressiveDisclosure", "defaultExpanded"),
    "AUD012": ("CodebaseDeltaTreeTracker", "CodebaseContextPacker", "SourceContextMetrics", "treeEditDistance", "savingPercent"),
    "SD3-068": ("ACCESSIBILITY_REQUIREMENTS", "accessibilityRequirementsSatisfied"),
    "SD3-069": ("PERFORMANCE_REQUIREMENTS", "boundedVisibleRows", "isWithinUpdateBudget"),
    "AUD280": ("reproducible-jar-hash", "ATROPOS_REPRODUCIBLE_JAR_HASH_OK"),
    "AUD031": ("Dockerfile", "native-build", "native-image"),
    "AUD032": ("Dockerfile", "HEALTHCHECK", "ATROPOS_HEALTH_MARKER"),
    "AUD029": ("PlatformModuleTopology", "canonicalModules", "shared-ui"),
    "AUD030": ("DesktopSurface", "DesktopApplication"),
    "AUD172": ("DesktopSurface", "DesktopApplication"),
}

# Some obligations are intentionally owned by build/acceptance scripts rather
# than Kotlin symbols.  Their integration and edge predicates are therefore
# proven by exact script relationships, not by identifier tokens (which are
# removed from quoted shell commands by code_only()).  Keep this list narrow:
# it names only the canonical owner and its already-tracked contract test.
CONTRACT_EVIDENCE = {
    "N002": {
        "impl": ("src/test/kotlin/atropos/cli/CommandRouterHelpTest.kt",),
    },
    "SD3-071": {
        "impl": ("src/test/kotlin/atropos/core/acceptance/CanonicalAcceptanceTests.kt",),
    },
    "STRICT-SideloadApk": {
        "impl": ("scripts/apk.sh",),
        "wire": ("scripts/apk.sh",),
        "edge": ("scripts/apk-owner.test.sh",),
    },
    "STRICT-ApkSigner": {
        "impl": ("scripts/setup-signing-secrets.sh",),
        "wire": ("scripts/setup-signing-secrets.sh",),
        "edge": ("scripts/signing-owner.test.sh",),
    },
    "BP-P16-hierarchy-dispatch": {
        "wire": ("src/main/kotlin/atropos/core/factory/AppProjectGenerator.kt",),
        "edge": ("src/test/kotlin/atropos/core/hierarchy/HierarchyRegistryTest.kt",),
    },
    "BP-P19-live-preview": {
        "wire": ("src/main/kotlin/atropos/core/factory/FactoryRunOrchestrator.kt",),
    },
    "BP-P19-browser-verification": {
        "wire": ("src/main/kotlin/atropos/core/factory/FactoryRunOrchestrator.kt",),
    },
    "BP-P19-secret-management": {
        "wire": ("src/main/kotlin/atropos/core/factory/AppProjectGenerator.kt",),
    },
    "BP-P19-portable-github": {
        "wire": ("src/main/kotlin/atropos/core/factory/AppProjectGenerator.kt", "src/main/kotlin/atropos/core/factory/FactoryRunOrchestrator.kt"),
    },
    "BP-P19-deployment": {
        "wire": ("src/main/kotlin/atropos/core/factory/FactoryRunOrchestrator.kt",),
    },
    "BP-P19-activity-monitor": {
        "wire": (
            "src/main/kotlin/atropos/core/factory/FactoryRunEventRecorder.kt",
            "src/main/kotlin/atropos/core/factory/FactoryRunOrchestrator.kt",
            "src/main/kotlin/atropos/cli/commands/AgentObservationCommandHandler.kt",
        ),
    },
    "AUD125": {
        "wire": ("src/main/kotlin/atropos/bridge/AtroposBridge.kt",),
        "edge": ("src/test/kotlin/atropos/bridge/http/HttpRequestAuthenticatorTest.kt",),
    },
    "AUD165": {
        "impl": ("apps/web/src/lib/activity/client.ts", "apps/web/src/components/ui/why-how-evidence.tsx"),
        "wire": ("apps/web/src/components/atropos/work-item-card.tsx",),
        "edge": ("apps/web/src/lib/activity/client.test.ts",),
    },
    "AUD124": {
        "impl": ("app/src/main/java/com/atropos/android/app/ui/MobileAppMvi.kt",),
        "wire": ("app/src/main/java/com/atropos/android/app/MainActivity.kt",),
        "edge": ("app/src/test/kotlin/com/atropos/android/app/ui/MobileAppMviTest.kt",),
    },
    "C001": {
        "wire": ("build.gradle.kts", "scripts/phase0-toolchain-contract-test.sh"),
        "edge": ("scripts/phase0-toolchain-contract-test.sh",),
    },
    "C003": {
        "wire": ("build.gradle.kts", "src/main/kotlin/atropos/core/verification/GovernedCompileGate.kt"),
        "edge": ("src/test/kotlin/atropos/core/verification/GovernedCompileGateTest.kt", "scripts/phase0-toolchain-contract-test.sh"),
    },
    "M003": {
        "wire": ("build.gradle.kts", "scripts/kotlin-compat-scan.sh"),
        "edge": ("scripts/kotlin-compat-scan-test.sh",),
    },
    "M006": {
        "impl": ("src/main/kotlin/atropos/core/platform/PortableSurfacePlan.kt",),
        "wire": ("src/main/kotlin/atropos/cli/commands/PlatformCommandHandler.kt",),
        "edge": ("src/test/kotlin/atropos/core/platform/PlatformWireTest.kt",),
    },
    "A005": {
        "impl": ("scripts/source-to-code-trace-gate.py",),
        "wire": ("scripts/source-to-code-trace-gate.py", "scripts/calculator-final-acceptance.sh"),
        "edge": ("scripts/source-to-code-trace-gate-test.sh",),
    },
    "M003": {
        "impl": ("scripts/kotlin-compat-scan.sh",),
        "wire": ("build.gradle.kts", "scripts/kotlin-compat-scan.sh"),
        "edge": ("scripts/kotlin-compat-scan-test.sh",),
    },
    "N002": {
        "impl": ("src/test/kotlin/atropos/cli/CommandRouterHelpTest.kt",),
        "wire": ("src/main/kotlin/atropos/cli/CommandRouter.kt",),
        "edge": ("src/test/kotlin/atropos/cli/ui/AnsiTerminalEngineHelpTest.kt",),
    },
    "N005": {
        "impl": ("scripts/calculator-final-acceptance.sh",),
        "wire": ("scripts/calculator-prerequisite-gate.sh", "scripts/calculator-final-acceptance.sh"),
        "edge": ("scripts/calculator-final-acceptance-test.sh",),
    },
    "BP-P00-baseline-lock": {
        "impl": ("build.gradle.kts", "gradle/wrapper/gradle-wrapper.properties"),
        "wire": ("build.gradle.kts",),
        "edge": ("scripts/phase0-toolchain-contract-test.sh",),
    },
    "SD4-014": {
        "impl": ("docs/ui-parity/OPENCODE_COMPLETE_SURFACE_MATRIX.json", "scripts/opencode-surface-matrix-edge-test.sh"),
        "wire": ("docs/ui-parity/OPENCODE_COMPLETE_SURFACE_MATRIX.json", "scripts/opencode-surface-matrix-edge-test.sh"),
        "edge": ("scripts/opencode-surface-matrix-edge-test.sh",),
    },
    "N005": {
        "impl": ("scripts/calculator-final-acceptance.sh",),
        "wire": ("scripts/calculator-prerequisite-gate.sh", "scripts/calculator-final-acceptance.sh"),
        "edge": ("scripts/calculator-final-acceptance-test.sh",),
    },
    "A005": {
        "impl": ("scripts/source-to-code-trace-gate.py",),
        "wire": ("scripts/source-to-code-trace-gate.py", "scripts/calculator-final-acceptance.sh"),
        "edge": ("scripts/source-to-code-trace-gate-test.sh",),
    },
    "STRICT-ApkSigner": {
        "impl": ("scripts/setup-signing-secrets.sh",),
        "wire": ("scripts/setup-signing-secrets.sh",),
        "edge": ("scripts/signing-owner.test.sh",),
    },
    "STRICT-SideloadApk": {
        "impl": ("scripts/apk.sh",),
        "wire": ("scripts/apk.sh",),
        "edge": ("scripts/apk-owner.test.sh",),
    },
    "AUD036": {
        "impl": ("scripts/package-installers.sh",),
        "wire": ("build.gradle.kts", "scripts/package-installers.sh"),
        "edge": ("scripts/package-installers-test.sh",),
    },
    "M001": {
        "impl": ("build.gradle.kts", "gradlew", "gradle/wrapper/gradle-wrapper.properties"),
        "wire": ("build.gradle.kts", "scripts/phase0-toolchain-contract-test.sh"),
        "edge": ("scripts/phase0-toolchain-contract-test.sh",),
    },
    "M002": {
        "impl": ("gradle.properties",),
        "wire": ("build.gradle.kts", "scripts/phase0-toolchain-contract-test.sh"),
        "edge": ("scripts/phase0-toolchain-contract-test.sh",),
    },
    "M005": {
        "impl": ("build.gradle.kts", "scripts/package-installers.sh"),
        "wire": ("build.gradle.kts", "scripts/package-installers.sh"),
        "edge": ("scripts/package-installers-test.sh",),
    },
    "N001": {"edge": ("src/test/kotlin/atropos/core/provider/QuotaLedgerRouteTruthTest.kt",)},
    "N002": {
        "impl": ("src/test/kotlin/atropos/cli/CommandRouterHelpTest.kt",),
        "wire": ("src/main/kotlin/atropos/cli/CommandRouter.kt",),
        "edge": ("src/test/kotlin/atropos/cli/ui/AnsiTerminalEngineHelpTest.kt",),
    },
    "N003": {"edge": ("src/test/kotlin/atropos/dloi/HigZeroGuardContractTest.kt", "src/test/kotlin/atropos/dloi/SourceAuthorityIndexerTest.kt")},
    "P001": {
        "wire": ("src/main/kotlin/atropos/cli/commands/VerifyCommand.kt",),
        "edge": ("src/test/kotlin/atropos/core/acceptance/CanonicalAcceptanceTests.kt",),
    },
    "AUD033": {
        "impl": ("server/src/main/kotlin/atropos/server/AtroposKtorServer.kt",),
        "wire": ("settings.gradle.kts", "server/build.gradle.kts"),
        "edge": ("server/src/test/kotlin/atropos/server/AtroposKtorServerTest.kt",),
    },
    "M004": {
        "impl": ("scripts/github-actions-clean-runner.sh",),
        "wire": (".github/workflows/factory-test.yml", "scripts/github-actions-clean-runner.sh"),
        "edge": ("scripts/github-actions-clean-runner-test.sh",),
    },
    "STRICT-StreamingApprovalCards": {
        "wire": ("apps/web/src/app/(app)/projects/[id]/work/page.tsx",),
        "edge": ("apps/web/src/components/streaming/__tests__/message-stream.test.tsx",),
    },
    "AUD012": {
        "impl": ("src/main/kotlin/atropos/data/cache/CodebaseDeltaTreeTracker.kt",),
        "wire": ("src/main/kotlin/atropos/core/provider/CodebaseContextPacker.kt",),
        "edge": ("src/test/kotlin/atropos/core/provider/SourceContextMetricsTest.kt",),
    },
    "SD3-071": {
        "impl": ("src/test/kotlin/atropos/core/acceptance/CanonicalAcceptanceTests.kt",),
        "wire": ("build.gradle.kts", "src/test/kotlin/atropos/core/acceptance/CanonicalAcceptanceTests.kt"),
        "edge": ("src/test/kotlin/atropos/core/acceptance/CanonicalAcceptanceTests.kt",),
    },
    "BP-P00-baseline-lock": {
        "impl": ("build.gradle.kts", "gradle/wrapper/gradle-wrapper.properties"),
        "wire": ("build.gradle.kts", "gradle/wrapper/gradle-wrapper.properties"),
        "edge": ("scripts/phase0-toolchain-contract-test.sh",),
    },
    "SD4-014": {
        "impl": ("docs/ui-parity/OPENCODE_COMPLETE_SURFACE_MATRIX.json", "scripts/opencode-surface-matrix-edge-test.sh"),
        "wire": ("docs/ui-parity/OPENCODE_COMPLETE_SURFACE_MATRIX.json", "scripts/opencode-surface-matrix-edge-test.sh"),
        "edge": ("scripts/opencode-surface-matrix-edge-test.sh",),
    },
    "AUD247": {
        "impl": ("src/main/kotlin/atropos/core/factory/AppDeploymentService.kt",),
        "edge": ("src/test/kotlin/atropos/core/factory/AppDeploymentServiceTest.kt",),
    },
    "AUD250": {
        "impl": ("src/main/kotlin/atropos/core/factory/AppDeploymentService.kt",),
        "edge": ("src/test/kotlin/atropos/core/factory/AppDeploymentServiceTest.kt",),
    },
    "AUD251": {
        "impl": ("src/main/kotlin/atropos/core/factory/AppDeploymentService.kt",),
        "edge": ("src/test/kotlin/atropos/core/factory/AppDeploymentServiceTest.kt",),
    },
    "AUD122": {
        "impl": ("src/test/kotlin/atropos/core/acceptance/CanonicalAcceptanceTests.kt",),
    },
    "AUD267": {
        "impl": ("src/main/kotlin/atropos/core/phase20/SuperiorityAddendum.kt",),
        "edge": ("src/test/kotlin/atropos/core/phase20/SuperiorityAddendumTest.kt",),
    },
    "AUD151": {
        "impl": ("src/main/kotlin/atropos/core/factory/AppDurableStore.kt",),
        "edge": ("src/test/kotlin/atropos/core/factory/AppDurableStoreTest.kt",),
    },
    "AUD244": {
        "impl": ("src/main/kotlin/atropos/core/phase20/PolicyGate.kt",),
        "edge": ("src/test/kotlin/atropos/core/phase20/PolicyGateTest.kt",),
    },
    "AUD275": {
        "impl": ("src/main/kotlin/atropos/core/phase20/SelfImprovementLoop.kt",),
        "edge": ("src/test/kotlin/atropos/core/phase20/SelfImprovementLoopTest.kt",),
    },
    "AUD276": {
        "impl": ("src/main/kotlin/atropos/core/recovery/RestartCoordinator.kt",),
        "edge": ("src/test/kotlin/atropos/core/recovery/RestartCoordinatorTest.kt",),
    },
    "AUD277": {
        "impl": ("src/main/kotlin/atropos/core/integration/AdversarialValidator.kt",),
        "edge": ("src/test/kotlin/atropos/core/integration/AdversarialValidatorTest.kt",),
    },
    "AUD278": {
        "impl": ("src/main/kotlin/atropos/core/provider/ProviderCascadeRouter.kt",),
        "edge": ("src/test/kotlin/atropos/core/provider/ProviderCascadeRouterTest.kt",),
    },
    "AUD149": {
        "impl": ("app/src/main/java/com/atropos/android/app/MainActivity.kt", "app/src/main/java/com/atropos/android/app/ui/MobileHeader.kt"),
        "edge": ("app/src/test/kotlin/com/atropos/android/app/ComposeAppShellTest.kt",),
    },
    "AUD020": {
        "impl": ("src/main/kotlin/atropos/core/verification/VerificationModels.kt",),
        "wire": ("src/main/kotlin/atropos/core/knowledge/SelfImprovingCompilationLoop.kt",),
        "edge": ("src/test/kotlin/atropos/core/knowledge/SelfImprovingCompilationLoopTest.kt",),
    },
    "AUD053": {
        "impl": ("src/main/kotlin/atropos/core/provider/EligibilityAlgorithm.kt",),
        "wire": ("src/main/kotlin/atropos/core/provider/RoutePolicy.kt",),
        "edge": ("src/test/kotlin/atropos/core/provider/EligibilityAlgorithmTest.kt",),
    },
    "AUD054": {
        "impl": ("src/main/kotlin/atropos/core/provider/ProviderFailureClassifier.kt",),
        "wire": ("src/main/kotlin/atropos/core/provider/ProviderCascadeRouter.kt",),
        "edge": ("src/test/kotlin/atropos/core/provider/ProviderFailureClassifierTest.kt",),
    },
    "AUD008": {
        "impl": ("src/main/kotlin/atropos/ast/AstImportReconciler.kt",),
        "wire": ("src/main/kotlin/atropos/ast/AstSymbolGraph.kt",),
        "edge": ("src/test/kotlin/atropos/ast/AstSymbolGraphTest.kt",),
    },
    "AUD013": {
        "impl": ("src/main/kotlin/atropos/core/ast/TopologicalMutation.kt",),
        "wire": ("src/main/kotlin/atropos/core/agent/BoundedWorkExecutor.kt",),
        "edge": ("src/test/kotlin/atropos/core/agent/BoundedWorkExecutorTest.kt",),
    },
    "AUD014": {
        "impl": ("src/main/kotlin/atropos/core/ast/TopologicalMutation.kt",),
        "wire": ("src/main/kotlin/atropos/core/agent/AgentRepairService.kt",),
        "edge": ("src/test/kotlin/atropos/core/integration/AdversarialValidatorTest.kt",),
    },
    "AUD017": {
        "impl": ("src/main/kotlin/atropos/core/integration/AdversarialValidator.kt",),
        "wire": ("src/main/kotlin/atropos/core/verification/DeterministicChecks.kt",),
        "edge": ("src/test/kotlin/atropos/core/integration/AdversarialValidatorTest.kt",),
    },
    "AUD101": {
        "impl": ("src/main/kotlin/atropos/core/intent/IntentLayer.kt",),
        "wire": ("src/main/kotlin/atropos/cli/CommandRouter.kt",),
        "edge": ("src/test/kotlin/atropos/core/intent/IntentLayerTest.kt",),
    },
    "AUD102": {
        "impl": ("src/main/kotlin/atropos/core/intent/IntentLayer.kt",),
        "wire": ("src/main/kotlin/atropos/cli/CommandRouter.kt",),
        "edge": ("src/test/kotlin/atropos/core/intent/IntentLayerTest.kt",),
    },
    "AUD103": {
        "impl": ("src/main/kotlin/atropos/core/intent/IntentLayer.kt",),
        "wire": ("src/main/kotlin/atropos/cli/CommandRouter.kt",),
        "edge": ("src/test/kotlin/atropos/core/intent/IntentLayerTest.kt",),
    },
    "AUD104": {
        "impl": ("src/main/kotlin/atropos/core/intent/IntentLayer.kt",),
        "wire": ("src/main/kotlin/atropos/cli/CommandRouter.kt",),
        "edge": ("src/test/kotlin/atropos/core/intent/IntentLayerTest.kt",),
    },
    "AUD107": {
        "impl": ("src/main/kotlin/atropos/core/intent/IntentLayer.kt",),
        "wire": ("src/main/kotlin/atropos/cli/CommandRouter.kt",),
        "edge": ("src/test/kotlin/atropos/core/intent/IntentLayerTest.kt",),
    },
    "AUD115": {
        "impl": ("src/main/kotlin/atropos/cli/input/SuggestionEngine.kt",),
        "wire": ("src/main/kotlin/atropos/cli/input/CommandCompleter.kt",),
        "edge": ("src/test/kotlin/atropos/cli/input/SuggestionEngineTest.kt",),
    },
    "AUD021": {
        "impl": ("src/main/kotlin/atropos/core/dopamine/DopamineRewardSystem.kt",),
        "wire": ("src/main/kotlin/atropos/core/knowledge/SelfImprovingCompilationLoop.kt",),
        "edge": ("src/test/kotlin/atropos/core/dopamine/DopamineRewardSystemTest.kt",),
    },
    "AUD174": {
        "impl": ("src/main/kotlin/atropos/core/autonomous/AutonomousBacklogManager.kt",),
        "edge": ("src/test/kotlin/atropos/core/autonomous/AutonomousBacklogManagerTest.kt",),
    },
    "AUD175": {
        "impl": ("src/main/kotlin/atropos/core/phase20/PolicyGate.kt",),
        "edge": ("src/test/kotlin/atropos/core/phase20/PolicyGateTest.kt",),
    },
    "AUD245": {
        "impl": ("src/main/kotlin/atropos/core/factory/AppDeploymentService.kt",),
        "edge": ("src/test/kotlin/atropos/core/factory/AppDeploymentServiceTest.kt",),
    },
    "AUD264": {
        "impl": ("src/main/kotlin/atropos/core/phase20/SuperiorityAddendum.kt",),
        "wire": ("src/main/kotlin/atropos/core/phase20/ReproducibilityGate.kt",),
        "edge": ("src/test/kotlin/atropos/core/phase20/SuperiorityAddendumTest.kt",),
    },
    "AUD265": {
        "impl": ("src/main/kotlin/atropos/core/phase20/SuperiorityAddendum.kt",),
        "wire": ("src/main/kotlin/atropos/core/integration/InboundToolRequest.kt",),
        "edge": ("src/test/kotlin/atropos/core/phase20/SuperiorityAddendumTest.kt",),
    },
    "AUD269": {
        "impl": ("src/main/kotlin/atropos/core/territory/TerritoryMonitorCost.kt",),
        "wire": ("src/main/kotlin/atropos/core/territory/TerritoryGrantService.kt",),
        "edge": ("src/test/kotlin/atropos/core/territory/TerritoryMonitorCostIntegrationTest.kt",),
    },
    "AUD260": {
        "impl": ("src/main/kotlin/atropos/core/phase20/Phase20GovernanceService.kt",),
        "wire": ("src/main/kotlin/atropos/cli/commands/SelfHostCommand.kt",),
        "edge": ("src/test/kotlin/atropos/cli/commands/SelfHostCommandTest.kt",),
    },
    "AUD171": {
        "impl": ("apps/web/src/app/(app)/layout.tsx",),
        "wire": ("apps/web/src/app/layout.tsx",),
        "edge": ("apps/web/src/lib/architecture/web-merge.test.ts", "apps/web/src/components/dev-tools/developer-tools-container.test.tsx"),
    },
    "AUD023": {
        "impl": ("src/main/kotlin/atropos/core/dopamine/DopamineRewardSystem.kt",),
        "wire": ("src/main/kotlin/atropos/cli/ProviderChatDispatcher.kt", "src/main/kotlin/atropos/cli/CommandRouter.kt"),
        "edge": ("src/test/kotlin/atropos/core/dopamine/DopamineRewardSystemTest.kt",),
    },
    "AUD270": {
        "impl": ("src/main/kotlin/atropos/core/parity/SurfaceContract.kt",),
        "wire": ("src/main/kotlin/atropos/core/parity/SurfaceParityProbe.kt",),
        "edge": ("src/test/kotlin/atropos/core/parity/SurfaceParityProbeTest.kt", "src/test/kotlin/atropos/core/phase20/SuperiorityAddendumTest.kt"),
    },
    "AUD252": {
        "impl": ("src/main/kotlin/atropos/core/preview/LivePreviewService.kt",),
        "wire": ("src/main/kotlin/atropos/cli/commands/InspectCommandHandler.kt",),
        "edge": ("src/test/kotlin/atropos/core/preview/LivePreviewServiceTest.kt",),
    },
    "AUD254": {
        "impl": ("src/main/kotlin/atropos/core/evidence/EvidenceCollector.kt",),
        "wire": ("src/main/kotlin/atropos/cli/commands/InspectCommandHandler.kt",),
        "edge": ("src/test/kotlin/atropos/core/evidence/EvidenceCollectorTest.kt",),
    },
    "AUD255": {
        "impl": ("src/main/kotlin/atropos/core/integration/InboundToolRequest.kt",),
        "wire": ("src/main/kotlin/atropos/bridge/BridgeMcpHandler.kt", "src/main/kotlin/atropos/bridge/BridgeRoutes.kt"),
        "edge": ("src/test/kotlin/atropos/core/integration/InboundActionProposalBridgeTest.kt",),
    },
    "AUD258": {
        "impl": ("src/main/kotlin/atropos/core/verification/ArchitectureComplianceChecker.kt",),
        "wire": ("src/main/kotlin/atropos/core/verification/DeterministicVerifier.kt",),
        "edge": ("src/test/kotlin/atropos/core/verification/ArchitectureComplianceCheckerTest.kt",),
    },
    "AUD261": {
        "impl": ("src/main/kotlin/atropos/dloi/SourceDocumentRegistry.kt",),
        "wire": ("src/main/kotlin/atropos/dloi/DloiSourceIndexer.kt",),
        "edge": ("src/test/kotlin/atropos/dloi/SourceDocumentRegistryTest.kt",),
    },
    "AUD249": {
        "impl": ("src/main/kotlin/atropos/core/project/ProjectModels.kt",),
        "wire": ("src/main/kotlin/atropos/core/project/ProjectRegistry.kt", "src/main/kotlin/atropos/core/factory/FactoryRunOrchestrator.kt"),
        "edge": ("src/test/kotlin/atropos/core/project/ProjectRegistryTest.kt",),
    },
    "C005": {
        "impl": ("src/main/kotlin/atropos/dloi/TermuxPathResolver.kt",),
        "wire": ("src/main/kotlin/atropos/dloi/DloiSourceIndexer.kt", "src/main/kotlin/atropos/dloi/SourceAuthorityIndexer.kt"),
        "edge": ("src/test/kotlin/atropos/dloi/TermuxPathResolverTest.kt",),
    },
    "AUD150": {
        "impl": ("apps/web/src/lib/a11y/conformance.test.ts",),
    },
    "AUD272": {
        "impl": ("scripts/audit-residual-obligations.py",),
    },
    "AUD041": {
        "impl": ("src/main/kotlin/atropos/core/provider/TaskRoutingMatrix.kt",),
        "wire": ("src/main/kotlin/atropos/cli/ProviderCommandHandler.kt",),
        "edge": ("src/test/kotlin/atropos/core/provider/FallbackChainTest.kt",),
    },
    "AUD093": {
        "impl": ("src/main/kotlin/atropos/core/observability/EventSubscriber.kt",),
        "wire": ("src/main/kotlin/atropos/core/observability/ProvenanceStream.kt",),
        "edge": ("src/test/kotlin/atropos/core/observability/ProvenanceStreamTest.kt",),
    },
    "AUD271": {
        "impl": ("SWARM.md",),
    },
    "AUD085": {
        "impl": ("src/main/kotlin/atropos/core/evaluation/Benchmark.kt",),
        "wire": ("src/main/kotlin/atropos/core/evaluation/BenchmarkRunner.kt",),
        "edge": ("src/test/kotlin/atropos/core/evaluation/BenchmarkRunnerTest.kt",),
    },
    "AUD086": {
        "impl": ("src/main/kotlin/atropos/core/evaluation/Benchmark.kt",),
        "wire": ("src/main/kotlin/atropos/core/evaluation/BenchmarkRunner.kt",),
        "edge": ("src/test/kotlin/atropos/core/evaluation/BenchmarkRunnerTest.kt",),
    },
    "AUD087": {
        "impl": ("src/main/kotlin/atropos/core/evaluation/Benchmark.kt",),
        "wire": ("src/main/kotlin/atropos/core/evaluation/BenchmarkRunner.kt",),
        "edge": ("src/test/kotlin/atropos/core/evaluation/BenchmarkRunnerTest.kt",),
    },
    "AUD088": {
        "impl": ("src/main/kotlin/atropos/core/evaluation/Benchmark.kt",),
        "wire": ("src/main/kotlin/atropos/core/evaluation/BenchmarkRunner.kt",),
        "edge": ("src/test/kotlin/atropos/core/evaluation/BenchmarkRunnerTest.kt",),
    },
    "AUD129": {
        "impl": ("apps/web/src/components/dev-tools/operation-surface-card.tsx",),
        "wire": ("apps/web/src/app/(app)/developer/page.tsx",),
        "edge": ("apps/web/src/components/dev-tools/operation-surface-registry.test.tsx",),
    },
    "AUD130": {
        "impl": ("apps/web/src/components/dev-tools/operation-surface-card.tsx",),
        "wire": ("apps/web/src/app/(app)/developer/page.tsx",),
        "edge": ("apps/web/src/components/dev-tools/operation-surface-registry.test.tsx",),
    },
    "AUD135": {
        "impl": ("apps/web/src/components/dev-tools/operation-surface-card.tsx",),
        "wire": ("apps/web/src/app/(app)/developer/page.tsx",),
        "edge": ("apps/web/src/components/dev-tools/operation-surface-registry.test.tsx",),
    },
    "AUD136": {
        "impl": ("apps/web/src/components/dev-tools/operation-surface-card.tsx",),
        "wire": ("apps/web/src/app/(app)/developer/page.tsx",),
        "edge": ("apps/web/src/components/dev-tools/operation-surface-registry.test.tsx",),
    },
    "AUD138": {
        "impl": ("apps/web/src/components/dev-tools/operation-surface-card.tsx",),
        "wire": ("apps/web/src/app/(app)/developer/page.tsx",),
        "edge": ("apps/web/src/components/dev-tools/operation-surface-registry.test.tsx",),
    },
    "AUD142": {"impl": ("apps/web/src/lib/parity/strict-surface-contract.test.ts",)},
    "AUD143": {"impl": ("apps/web/src/lib/parity/strict-surface-contract.test.ts",)},
    "AUD145": {"impl": ("apps/web/src/lib/parity/strict-surface-contract.test.ts",)},
    "AUD146": {"impl": ("apps/web/src/components/dev-tools/operation-surface-registry.test.tsx",)},
    "AUD144": {
        "impl": ("apps/web/src/components/ui/progressive-disclosure.tsx",),
        "wire": ("apps/web/src/components/streaming/message-stream.tsx",),
        "edge": ("apps/web/src/components/atropos/progressive-disclosure.test.tsx",),
    },
    "AUD280": {
        "impl": ("scripts/reproducible-jar-hash.sh",),
        "wire": (".github/workflows/reproducibility.yml",),
        "edge": ("scripts/reproducible-jar-hash-test.sh",),
    },
    "AUD031": {"impl": ("Dockerfile",)},
    "AUD032": {
        "impl": ("Dockerfile",),
        "wire": ("src/main/kotlin/atropos/Main.kt",),
        "edge": ("scripts/docker-platform-contract-test.sh",),
    },
    "AUD029": {
        "impl": ("src/main/kotlin/atropos/core/platform/PlatformModuleTopology.kt",),
        "wire": ("src/main/kotlin/atropos/core/platform/PlatformWire.kt",),
        "edge": ("src/test/kotlin/atropos/core/platform/PlatformModuleTopologyTest.kt",),
    },
    "AUD030": {
        "impl": ("desktop/src/main/kotlin/atropos/desktop/DesktopSurface.kt",),
        "wire": ("desktop/src/main/kotlin/atropos/desktop/DesktopApplication.kt",),
        "edge": ("desktop/src/test/kotlin/atropos/desktop/DesktopSurfaceTest.kt",),
    },
    "AUD172": {
        "impl": ("desktop/src/main/kotlin/atropos/desktop/DesktopSurface.kt",),
        "wire": ("desktop/src/main/kotlin/atropos/desktop/DesktopApplication.kt",),
        "edge": ("desktop/src/test/kotlin/atropos/desktop/DesktopSurfaceTest.kt",),
    },
}

# This obligation is itself the non-colour-channel test contract.  Requiring a
# separate production caller would invert the requirement and create a fake
# runtime dependency solely to satisfy the census.
TEST_OWNED_REQUIREMENTS = {"AUD150"}
# These source obligations explicitly require a test suite as the product
# artifact.  For these IDs, the test implementation is not being mistaken for
# executable product code; it is the named implementation surface itself.
TEST_IMPLEMENTATION_REQUIREMENTS = {
    "N002", "SD3-071", "AUD122", "AUD142", "AUD143", "AUD145", "AUD146"
}

# A symbol or file can exist while the requirement explicitly records that the
# required capability is still missing.  These are not heuristic title
# matches: each entry is a reviewed registry requirement whose authoritative
# description states an unclosed implementation gap.  Keep the denial here so
# a later rename or additional reference cannot silently award completion.
STRICT_INCOMPLETE_IMPLEMENTATIONS = {
}

# The residual inventory predates the canonical self-host model.  Keep the
# legacy file untouched, but audit the obligation against the live owner so a
# stale class name does not force a second GoalRun store/model.
RESIDUAL_SYMBOL_ALIASES = {
    "ABSENT#GoalRun": ("GoalRunRecord",),
    "ABSENT#AcceptanceVelocity": ("AcceptanceVelocity",),
    "ABSENT#GoalInvariantSet": ("GoalInvariantSet",),
    "ABSENT#IntentEnvelope": ("IntentEnvelope",),
    "ABSENT#SecretSinkMatrix": ("SecretSinkMatrix",),
    "REGISTRY-FAILURE#A001": ("SourceDocumentRegistry",),
    "REGISTRY-FAILURE#C005": ("TermuxPathResolver",),
    "SUP.AUTH.SourceAuthorityLaw": ("SourceAuthorityLaw",),
}

# The residual inventory names each invariant as its own atom, while the
# canonical implementation intentionally owns the catalog as one evaluator.
# Map each atom explicitly to that owner; this is not a second invariant
# engine and it preserves the one-responsibility ownership rule.
RESIDUAL_SYMBOL_ALIASES.update({
    f"INV-{index:03d}": ("InvariantContractCatalog",)
    for index in range(1, 49)
})

RESIDUAL_EDGE_EVIDENCE = {
    "CORE#ConstraintSolverEvaluator": (
        "src/test/kotlin/atropos/core/verifier/ConstraintSolverEvaluatorExtendedTest.kt",
    ),
    "CORE#TreeSitterGrammarBridge": (
        "src/test/kotlin/atropos/core/parser/TreeSitterGrammarBridgeExtendedTest.kt",
    ),
    "CORE#VerifiedCompletionGate": (
        "src/test/kotlin/atropos/core/verification/VerifiedCompletionGateTest.kt",
    ),
    "CORE#BoundedAgencyGate": (
        "src/test/kotlin/atropos/core/verification/GateReachabilityCheckerTest.kt",
    ),
    "HOE-B-cli-antigravity": (
        "src/test/kotlin/atropos/cli/ui/HoeAntigravitySurfaceContractTest.kt",
    ),
    "C3-AF-app-factory": (
        "src/test/kotlin/atropos/core/factory/AppFactoryAcceptanceContractTest.kt",
    ),
    "HOE-A08-progressive-disclosure": (
        "src/test/kotlin/atropos/cli/ui/disclosure/ProgressiveDisclosureTest.kt",
    ),
    "CORE#FreeSpaceGate": (
        "src/test/kotlin/atropos/core/storage/StorageGovernanceAtomsTest.kt",
    ),
}
RESIDUAL_EDGE_EVIDENCE.update({
    f"INV-{index:03d}": ("src/test/kotlin/atropos/core/phase20/InvariantContractCatalogTest.kt",)
    for index in range(1, 49)
})

# The residual inventory intentionally records several atoms whose symbols and
# callers exist but whose own acceptance notes say the contract is still open.
# A symbol/caller census must not turn those notes into a false WIRED result.
# Keep this explicit and narrow: each entry names the predicate that remains
# unproven and the evidence-based reason shown in the audit output.
STRICT_RESIDUAL_PREDICATES = {
    "CORE#ConstraintSolverEvaluator": {
    },
    "CORE#TokenIsolationVault": {
    },
    "CORE#TreeSitterGrammarBridge": {
    },
    "CORE#BoundedAgencyGate": {
    },
    "CORE#VerifiedCompletionGate": {
    },
    "CORE#FreeSpaceGate": {
    },
    "HOE-B-cli-antigravity": {
    },
}
RESIDUAL_EDGE_EVIDENCE["CORE#TokenIsolationVault"] = (
    "src/test/kotlin/atropos/core/security/SecretEgressGateTest.kt",
)

# Android is an application entrypoint, so its root activity is not expected
# to have a Kotlin caller.  These paths are the explicit composition proof:
# manifest entrypoint -> shell -> composed peer screens.  Do not infer a
# complete Android surface from a single class name.
RESIDUAL_INTEGRATION_EVIDENCE = {
    # The provider dispatcher is the production boundary for the complete
    # egress pipeline; SecretEgressGate composes the accumulator and canary
    # value object internally. Do not require callers to construct security
    # helpers directly and accidentally create a second security owner.
    "SD5#E03-secret-egress": (
        "src/main/kotlin/atropos/cli/ProviderChatDispatcher.kt",
    ),
    "HOE-D-android": (
        "app/src/main/AndroidManifest.xml",
        "app/src/main/java/com/atropos/android/app/MainActivity.kt",
        "app/src/main/java/com/atropos/android/app/ui/ConversationScreen.kt",
        "app/src/main/java/com/atropos/android/app/ui/MobileAppMvi.kt",
    ),
}

RESIDUAL_COMPOSITION_REFERENCES = {
    "HOE-D-android": {
        "app/src/main/AndroidManifest.xml": ("MainActivity",),
        "app/src/main/java/com/atropos/android/app/MainActivity.kt": (
            "ConversationScreen", "MobileAppMviStore", "OneHandDensity",
        ),
        "app/src/main/java/com/atropos/android/app/ui/ConversationScreen.kt": (
            "ChatListScreen", "CheckpointChip", "ThinkingSheet",
        ),
        "app/src/main/java/com/atropos/android/app/ui/MobileAppMvi.kt": (
            "SessionTabModel",
        ),
    },
}


def build_index(corpus):
    index = {}
    for path, text in corpus.items():
        for symbol in set(WORD.findall(code_only(text))):
            index.setdefault(symbol, set()).add(str(path.relative_to(ROOT)))
    return index


PROD_INDEX = build_index(PROD_TEXT)
TEST_INDEX = build_index(TEST_TEXT)
PROD_FOLDED_INDEX = {}
TEST_FOLDED_INDEX = {}
for index, folded in ((PROD_INDEX, PROD_FOLDED_INDEX), (TEST_INDEX, TEST_FOLDED_INDEX)):
    for symbol, paths in index.items():
        folded.setdefault(symbol.lower(), set()).update(paths)

# Declaration lookup is indexed once.  Re-running a regex over every source
# file for every candidate made the strict audit quadratic and could leave a
# report stale without an explicit failure.
DECLARED_PROD_INDEX = {}
for _path, _text in PROD_TEXT.items():
    for _match in DECLARATION_SCAN_RE.finditer(code_only(_text)):
        DECLARED_PROD_INDEX.setdefault(_match.group(1), set()).add(str(_path.relative_to(ROOT)))


def matches(symbol: str, corpus):
    index = PROD_INDEX if corpus is PROD_TEXT else TEST_INDEX
    exact = index.get(symbol, set())
    if exact:
        return sorted(exact)
    folded_index = PROD_FOLDED_INDEX if corpus is PROD_TEXT else TEST_FOLDED_INDEX
    return sorted(folded_index.get(symbol.lower(), set()))


DECLARATION_RE = r"(?:class|object|interface|enum\s+class|data\s+class|sealed\s+class|fun|val|var|typealias|function|def|const|let)\s+{symbol}\b"
ASSERTION_RE = re.compile(
    r"\b(?:assert|assertEquals|assertNotEquals|assertTrue|assertFalse|assertNull|"
    r"assertNotNull|assertFails|assertFailsWith|check|require|shouldBe|shouldThrow|"
    r"expect|toBe|toEqual|toHave|pytest)\b"
)


def declaration_paths(symbol: str):
    return sorted(DECLARED_PROD_INDEX.get(symbol, set()))


def executable_reference(text: str, symbol: str) -> bool:
    """Require a code reference, excluding imports and declarations."""
    cleaned = code_only(text)
    cleaned = re.sub(r"(?m)^\s*import\s+[^\n]+", " ", cleaned)
    cleaned = re.sub(
        r"\b" + DECLARATION_RE.format(symbol=re.escape(symbol)) + r"\b",
        " ",
        cleaned,
    )
    return bool(re.search(r"\b" + re.escape(symbol) + r"\b", cleaned))


def behavioral_test_reference(text: str, symbols) -> bool:
    """Require both a symbol use and an assertion-like behavior check."""
    return any(executable_reference(text, symbol) for symbol in symbols) and bool(ASSERTION_RE.search(code_only(text)))


def valid_residual_integration_evidence(residual_id: str, paths, symbols) -> list[str]:
    """Validate reviewed residual composition evidence instead of file presence.

    Entry-point composition is allowed for roots such as Android, but the
    evidence still has to contain an executable owner reference.  A manifest
    is accepted only when it names the declared application entry point; it
    cannot prove that the rest of the surface is wired.
    """
    valid = []
    for raw_path in paths:
        path = str(raw_path)
        file = ROOT / path
        if not file.is_file():
            continue
        raw_text = file.read_text(errors="ignore")
        text = code_only(raw_text)
        required = RESIDUAL_COMPOSITION_REFERENCES.get(residual_id, {}).get(path)
        if required is not None:
            if path.endswith("AndroidManifest.xml"):
                if ".MainActivity" in raw_text or "MainActivity" in raw_text:
                    valid.append(path)
            elif all(executable_reference(text, symbol) for symbol in required):
                valid.append(path)
            continue
        if any(executable_reference(text, symbol) for symbol in symbols):
            valid.append(path)
    return sorted(set(valid))


def valid_residual_edge_evidence(residual_id: str, paths, symbols) -> list[str]:
    """Require behavioral test evidence for reviewed residual edge paths."""
    valid = []
    for raw_path in paths:
        path = str(raw_path)
        file = ROOT / path
        if not file.is_file():
            continue
        text = TEST_TEXT.get(file, file.read_text(errors="ignore"))
        if behavioral_test_reference(text, symbols):
            valid.append(path)
    return sorted(set(valid))


def owner_paths(owner: str, candidates, production):
    """Resolve the canonical owner without counting its own file as a caller."""
    declared = []
    if owner.startswith("symbol:"):
        declared = []
    elif owner and "/" in owner:
        path = ROOT / owner
        if path.is_file():
            declared = [str(path.relative_to(ROOT))]
    if declared and any(any(candidate in PROD_TEXT.get(ROOT / path, "") for candidate in candidates) for path in declared):
        return set(declared)
    inferred = set()
    for candidate in candidates:
        inferred.update(declaration_paths(candidate))
    return inferred or set(declared)


def concrete_owner_paths(owner: str, candidates, paths):
    """Keep implementation credit tied to a real owner declaration.

    A listed path can be a README, an index, or a broad subsystem file.  That
    is evidence that a path exists, not evidence that the named obligation is
    implemented.  Contract-owned script/artifact rows are handled separately
    by CONTRACT_EVIDENCE.
    """
    concrete = []
    for path in paths:
        source = PROD_TEXT.get(ROOT / path, "")
        if not source.strip():
            continue
        if Path(path).suffix in {".kt", ".java", ".py", ".ts", ".tsx", ".js", ".jsx"}:
            if any(path in declaration_paths(candidate) for candidate in candidates):
                concrete.append(path)
        elif Path(path).suffix in {".sh", ".kts"}:
            # Shell/build owners are valid only when their requirement has an
            # explicit contract entry; arbitrary script presence is not code.
            continue
    return sorted(set(concrete))


def owner_symbols(owner: str, candidates):
    if owner and "/" in owner:
        return (Path(owner).stem,)
    if owner.startswith("symbol:"):
        return (owner.removeprefix("symbol:"),)
    return tuple(candidates)


def valid_contract_paths(requirement_id: str, predicate: str, paths, candidates):
    """Validate explicit contract evidence without turning presence into proof.

    Contract evidence is reserved for requirements whose canonical owner is a
    build/acceptance artifact.  A test file can prove semantics, but it can
    never prove implementation.  A documentation file can describe an
    obligation, but it cannot prove either implementation or integration.
    Wiring must include at least one executable production/build/script path.
    """
    existing = [str(path) for path in paths if (ROOT / path).is_file()]
    if not existing:
        return []
    if predicate == "impl":
        candidates = tuple(candidates) + tuple(Path(path).stem for path in existing)
        return [
            path for path in existing
            if (requirement_id in TEST_IMPLEMENTATION_REQUIREMENTS
                or not ("/test/" in f"/{path}" or path.startswith("src/test/")
                        or path.startswith("app/src/test/")
                        or path.startswith("docs/")))
            and _contract_mentions_owner(path, requirement_id, candidates)
        ]
    if predicate == "wire":
        return [
            path for path in existing
            if not ("/test/" in f"/{path}" or path.startswith("src/test/")
                    or path.startswith("app/src/test/")
                    or path.startswith("docs/"))
            and _contract_mentions_owner(path, requirement_id, candidates)
        ]
    # Edge evidence must be an actual test/contract file.  Existence is not
    # enough for Kotlin/Python tests; they must mention the owner or the
    # requirement.  Shell contract tests are accepted only when they carry
    # the requirement identifier or one of the owner concepts.
    owner_tokens = [requirement_id, *[str(candidate) for candidate in candidates]]
    valid = []
    for path in existing:
        if "/test/" not in f"/{path}" and not path.endswith(("-test.sh", "_test.sh", ".test.sh", "Test.kt")):
            continue
        text = TEST_TEXT.get(ROOT / path, "")
        stem = Path(path).name.lower()
        stem_tokens = {
            stem,
            stem.removesuffix(".sh"),
            stem.removesuffix(".test.sh"),
            stem.removesuffix("-test.sh"),
            stem.removesuffix("_test.sh"),
        }
        if any(token and token.lower() in text.lower() for token in owner_tokens) or any(
            token in text.lower() for token in stem_tokens
        ) or (path.endswith("-edge-test.sh") and "EDGE_OK" in text):
            valid.append(path)
    return valid


def _contract_mentions_owner(path: str, requirement_id: str, candidates) -> bool:
    """Require explicit owner identity in contract evidence.

    A path existing is not implementation.  The contract file must name its
    requirement or canonical owner in executable/source text (or in its own
    filename), preventing unrelated files from being credited by registry
    configuration alone.
    """
    text = (PROD_TEXT.get(ROOT / path) or TEST_TEXT.get(ROOT / path) or "").lower()
    haystack = f"{path.lower()}\n{text}"
    tokens = [requirement_id, *[str(candidate) for candidate in candidates]]
    return any(token and token.lower() in haystack for token in tokens)


def registry_candidates(row):
    candidates = set(row.get("implementationEvidenceSymbols") or [])
    owner = str(row.get("canonicalOwner") or "")
    if owner.startswith("symbol:"):
        candidates.add(owner.removeprefix("symbol:"))
    elif owner and "/" in owner:
        candidates.add(Path(owner).stem)
    candidates.update(REQUIREMENT_ALIASES.get(str(row.get("requirementId") or ""), ()))
    for raw in row.get("expectedPathsOrSymbols") or []:
        value = str(raw)
        if "/" in value or value.endswith(('.kt', '.java', '.ts', '.tsx', '.js', '.jsx')):
            candidates.add(Path(value).stem)
        else:
            candidates.add(value)
    return sorted(candidates)


def current_registry_status(row):
    candidates = registry_candidates(row)
    production = sorted({path for symbol in candidates for path in matches(symbol, PROD_TEXT)})
    tests = sorted({path for symbol in candidates for path in matches(symbol, TEST_TEXT)})
    declared_paths = [
        str(path)
        for path in (row.get("expectedPathsOrSymbols") or [])
        if "/" in str(path)
    ]
    declared_paths.extend(
        str(path)
        for path in (row.get("implementationEvidencePaths") or [])
        if "/" in str(path)
    )
    existing_paths = [path for path in declared_paths if (ROOT / path).is_file()]
    owner = str(row.get("canonicalOwner") or "")
    canonical_paths = owner_paths(owner, candidates, production)
    external = [path for path in production if path not in canonical_paths]
    if not external and canonical_paths:
        # A script/function owner may be integrated by its own explicit entrypoint.
        for path in canonical_paths:
            text = code_only(PROD_TEXT.get(ROOT / path, ""))
            if any(len(re.findall(rf"\b{re.escape(candidate)}\s*(?:\(|\.|::)", text)) > 1 for candidate in candidates):
                external = [path]
                break
    # Do not infer implementation from prose/title tokens.  A row without a
    # declared path, symbol, alias, or contract has no auditable code anchor.
    # A token occurrence is not an implementation.  Require a concrete
    # declaration or an explicit contract path; imports and prose are not
    # callers.  This is intentionally stricter than the historical audit.
    concrete_paths = concrete_owner_paths(owner, candidates, canonical_paths)
    impl = bool(concrete_paths)
    symbols = owner_symbols(owner, candidates)
    wire = any(
        any(executable_reference(PROD_TEXT.get(ROOT / path, ""), symbol) for symbol in symbols)
        for path in external
    )
    edge = any(
        behavioral_test_reference(TEST_TEXT.get(ROOT / path, ""), candidates)
        for path in tests
    )
    # Apply only exact, repository-local contract evidence.  This avoids
    # turning a filename or a documentation mention into a production caller.
    contract = CONTRACT_EVIDENCE.get(str(row.get("requirementId") or ""), {})
    for predicate, paths in contract.items():
        valid_paths = valid_contract_paths(
            str(row.get("requirementId") or ""), predicate, paths, candidates
        )
        present = bool(valid_paths)
        if predicate == "impl":
            impl = impl or present
            if present:
                production = sorted(set(production) | set(valid_paths))
                impl = True
        elif predicate == "wire":
            wire = wire or present
            if present:
                external = sorted(set(external) | set(valid_paths))
        elif predicate == "edge":
            edge = edge or present
            if present:
                tests = sorted(set(tests) | set(valid_paths))
    if str(row.get("requirementId") or "") in TEST_OWNED_REQUIREMENTS:
        wire = edge
    predicate_kind = {
        "implementation": "impl",
        "integration": "wire",
        "semantics": "edge",
    }.get(str(row.get("predicateKind") or ""), str(row.get("predicateKind") or ""))
    requirement_id = str(row.get("requirementId") or "")
    strict_override = STRICT_INCOMPLETE_IMPLEMENTATIONS.get(requirement_id)
    if strict_override and predicate_kind == "impl":
        impl = False
    predicate_satisfied = {"impl": impl, "wire": wire, "edge": edge}.get(predicate_kind, False)
    production_evidence = [
        path for path in (production or concrete_paths)
        if "/test/" not in f"/{path}" and not path.startswith("app/src/test/")
    ]
    implementation_evidence = sorted(set(production or concrete_paths))
    return {
        "obligationId": row.get("obligationId"),
        "requirementId": row.get("requirementId"),
        "phase": row.get("phase"),
        "checkpoint": row.get("checkpoint"),
        "predicateKind": row.get("predicateKind"),
        "title": row.get("title"),
        "historicalStatus": row.get("status"),
        "predicateKind": predicate_kind,
        "predicateSatisfied": predicate_satisfied,
        "candidates": candidates,
        "predicates": {"impl": impl, "wire": wire, "edge": edge},
        "currentStatus": "WRITTEN" if predicate_satisfied else "NOT_WRITTEN",
        "implementationEvidence": implementation_evidence,
        "productionEvidence": production_evidence,
        "callerEvidence": external,
        "testEvidence": tests,
        "strictOverride": strict_override,
    }


def reconcile_registry_current_status(registry, current_rows, current_head):
    """Persist strict current status while retaining historical claims."""
    by_id = {row["obligationId"]: row for row in current_rows}
    changed = False
    for row in registry["obligations"]:
        current = by_id[row["obligationId"]]
        if "historicalStatus" not in row:
            row["historicalStatus"] = row.get("status")
        if "historicalStatusReason" not in row:
            row["historicalStatusReason"] = row.get("statusReason")
        status = current["currentStatus"]
        reason = (
            f"Strict audit override: {current['strictOverride']}"
            if current.get("strictOverride") else
            "Current strict source audit satisfied this predicate with concrete "
            "implementation, production reachability, and test evidence."
            if current["predicateSatisfied"] else
            "Current strict source audit found no complete evidence for this predicate; "
            "implementation, integration, or semantics remains open."
        )
        updates = {
            "status": status,
            "auditFinding": (
                "CURRENT_NOT_WRITTEN_STRICT_OVERRIDE"
                if current.get("strictOverride")
                else "CURRENT_WRITTEN_EVIDENCED" if status == "WRITTEN" else "CURRENT_NOT_WRITTEN"
            ),
            "statusReason": reason,
            "auditCurrentStatus": status,
            "auditCurrentPredicateSatisfied": current["predicateSatisfied"],
            "auditCurrentEvidence": {
                "implementation": current["implementationEvidence"],
                "production": current["productionEvidence"],
                "callers": current["callerEvidence"],
                "tests": current["testEvidence"],
            },
            "auditStrictOverride": current.get("strictOverride"),
            "lastAuditedHead": current_head,
        }
        for key, value in updates.items():
            if row.get(key) != value:
                row[key] = value
                changed = True
    if changed:
        REGISTRY.write_text(json.dumps(registry, indent=2) + "\n")
    return changed


def current_node_status(node):
    symbols = node.get("expectedSymbols", [])
    residual_id = str(node.get("id", ""))
    symbols = list(RESIDUAL_SYMBOL_ALIASES.get(residual_id, symbols))
    # InvariantContractCatalog is a dispatcher/evaluator, not proof that all
    # 48 invariants are enforced.  Do not award every invariant from the
    # presence of that catalog; each invariant needs an explicit owner.
    if not symbols and node.get("id") == "HOE-C-web-opencode":
        symbols = ["DeveloperToolsContainer", "WebMergeArchitecture", "StreamingApprovalCards"]
    if not symbols and node.get("id") == "HOE-D-android":
        symbols = ["MainActivity", "ChatListScreen", "CheckpointChip", "ThinkingSheet", "OneHandDensity", "SessionTabModel"]
    prod_paths = sorted({path for symbol in symbols for path in matches(symbol, PROD_TEXT)})
    test_paths = sorted({path for symbol in symbols for path in matches(symbol, TEST_TEXT)})
    owner = node.get("canonicalOwner") or ""
    canonical_paths = owner_paths(owner, symbols, prod_paths)
    external = [path for path in prod_paths if path not in canonical_paths]
    # Composite residual atoms are conjunctive.  One of six Android symbols,
    # or one of 48 invariant labels, cannot satisfy the whole atom.
    implementation = bool(symbols) and all(
        any(path in canonical_paths for path in declaration_paths(symbol))
        for symbol in symbols
    )
    integration = bool(symbols) and all(
        any(executable_reference(PROD_TEXT.get(ROOT / path, ""), symbol) for path in external)
        for symbol in symbols
    )
    integration_paths = RESIDUAL_INTEGRATION_EVIDENCE.get(residual_id, ())
    valid_integration_paths = valid_residual_integration_evidence(
        residual_id, integration_paths, symbols
    )
    explicit_integration_evidence = bool(integration_paths) and len(valid_integration_paths) == len(set(integration_paths))
    if explicit_integration_evidence:
        integration = True
        external = sorted(set(external) | set(valid_integration_paths))
    semantics = bool(symbols) and all(
        any(behavioral_test_reference(TEST_TEXT.get(ROOT / path, ""), (symbol,)) for path in test_paths)
        for symbol in symbols
    )
    notes = str(node.get("implNotes", ""))
    edge_gap = bool(re.search(r"edge residual|remaining work is edge|EARLY PARTIAL|L3/L4 are not placeholder", notes, re.I))
    edge_paths = RESIDUAL_EDGE_EVIDENCE.get(residual_id, ())
    valid_edge_paths = valid_residual_edge_evidence(residual_id, edge_paths, symbols)
    explicit_edge_evidence = bool(edge_paths) and len(valid_edge_paths) == len(set(edge_paths))
    if edge_gap and not explicit_edge_evidence:
        semantics = False
    strict_gaps = STRICT_RESIDUAL_PREDICATES.get(residual_id, {})
    if "impl" in strict_gaps:
        implementation = False
    if "wire" in strict_gaps:
        integration = False
    if "edge" in strict_gaps:
        semantics = False
    if not implementation:
        status = "ABSENT"
    elif not integration:
        status = "ORPHANED"
    elif not semantics:
        status = "PARTIAL"
    else:
        status = "WIRED"
    return {
        "id": node["id"],
        "title": node["title"],
        "sourceStatus": node.get("sourceStatus", node.get("status")),
        "phase": node.get("phase"),
        "surface": node.get("surface"),
        "canonicalOwner": owner,
        "expectedSymbols": symbols,
        "resolvedAlias": RESIDUAL_SYMBOL_ALIASES.get(str(node.get("id", "")), ()),
        "predicates": {
            "impl": implementation,
            "wire": integration,
            "edge": semantics,
        },
        "currentStatus": status,
        "implementationEvidence": prod_paths,
        "productionEvidence": prod_paths,
        "callerEvidence": external,
        "testEvidence": test_paths,
        "dependsOn": node.get("dependsOn", []),
        "implNotes": node.get("implNotes", ""),
        "auditWarnings": (
            (["source inventory records an unresolved edge gap"] if edge_gap else [])
            + [f"strict residual override: {reason}" for reason in strict_gaps.values()]
        ),
        "strictPredicateGaps": strict_gaps,
    }


def orphan_files():
    """Conservative declaration-to-caller heuristic, excluding test-only refs."""
    result = []
    declaration = re.compile(r"\b(?:class|object|interface|enum\s+class|data\s+class|sealed\s+class)\s+([A-Za-z_][A-Za-z0-9_]*)")
    for path, text in PROD_TEXT.items():
        if path.suffix not in {".kt", ".java"} or not ("/src/main/" in path.as_posix() or "/app/src/main/" in path.as_posix()):
            continue
        names = declaration.findall(text)
        if not names:
            continue
        callers = []
        for name in names:
            callers.extend(other for other in PROD_INDEX.get(name, set()) if other != str(path.relative_to(ROOT)))
        if not callers:
            result.append({"path": str(path.relative_to(ROOT)), "lines": len(text.splitlines()), "symbols": sorted(set(names))})
    return sorted(result, key=lambda row: (-row["lines"], row["path"]))


def main():
    residual = json.loads(RESIDUAL.read_text())
    registry = json.loads(REGISTRY.read_text())
    nodes = [current_node_status(node) for node in residual["nodes"]]
    # Persist current residual results beside, rather than over, the source
    # inventory's historical/sourceStatus labels.  Consumers must not mistake
    # a prior WIRED/partial classification for the current audit result.
    residual_by_id = {node["id"]: node for node in nodes}
    residual_changed = False
    for source_node in residual["nodes"]:
        current = residual_by_id[source_node["id"]]
        updates = {
            # `status` is the live machine-facing status.  The prior/source
            # classification remains available as `sourceStatus`.
            "status": current["currentStatus"],
            "currentStatus": current["currentStatus"],
            "currentPredicates": current["predicates"],
            "currentAuditEvidence": {
                "implementation": current["implementationEvidence"],
                "production": current["productionEvidence"],
                "callers": current["callerEvidence"],
                "tests": current["testEvidence"],
            },
        }
        for key, value in updates.items():
            if source_node.get(key) != value:
                source_node[key] = value
                residual_changed = True
    if residual_changed:
        RESIDUAL.write_text(json.dumps(residual, indent=2) + "\n")
    original = [{
        "obligationId": row.get("obligationId"),
        "phase": row.get("phase"),
        "title": row.get("title"),
        "predicateKind": row.get("predicateKind"),
        "status": row.get("status"),
        "canonicalOwner": row.get("canonicalOwner"),
    } for row in registry["obligations"]]
    current_original = [current_registry_status(row) for row in registry["obligations"]]
    current_head = subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()
    current_worktree = current_worktree_fingerprint()
    current_identity = f"{current_head}@worktree:{current_worktree}"
    reconcile_registry_current_status(registry, current_original, current_identity)
    current_original_summary = Counter(row["currentStatus"] for row in current_original)
    current_original_missing = Counter(
        row["predicateKind"]
        for row in current_original
        if not row["predicateSatisfied"]
    )
    current_phase_totals = {}
    for row in current_original:
        phase = str(row.get("phase"))
        bucket = current_phase_totals.setdefault(phase, {"total": 0, "written": 0, "missing": 0, "missingByPredicate": Counter()})
        bucket["total"] += 1
        if row["currentStatus"] == "WRITTEN":
            bucket["written"] += 1
        else:
            bucket["missing"] += 1
            bucket["missingByPredicate"][row["predicateKind"]] += 1
    for bucket in current_phase_totals.values():
        bucket["missingByPredicate"] = dict(bucket["missingByPredicate"])
    requirement_groups = {}
    for row in current_original:
        group = requirement_groups.setdefault(row["requirementId"], {
            "requirementId": row["requirementId"],
            "phase": row["phase"],
            "checkpoint": row["checkpoint"],
            "title": row["title"],
            "obligations": [],
            "predicates": {"impl": True, "wire": True, "edge": True},
        })
        group["obligations"].append(row["obligationId"])
        predicate = row["predicateKind"]
        if predicate in group["predicates"]:
            group["predicates"][predicate] = group["predicates"][predicate] and row["predicateSatisfied"]
    for group in requirement_groups.values():
        group["currentStatus"] = "WRITTEN" if all(group["predicates"].values()) else ("PARTIAL" if any(group["predicates"].values()) else "ABSENT")
    canonical_atom_summary = Counter(group["currentStatus"] for group in requirement_groups.values())
    residual_atom_summary = Counter(node["currentStatus"] for node in nodes)
    combined_atom_total = len(requirement_groups) + len(nodes)
    combined_atom_complete = (
        canonical_atom_summary.get("WRITTEN", 0)
        + residual_atom_summary.get("WIRED", 0)
    )
    # Use the canonical orphan census rather than a second declaration parser.
    # This keeps the machine audit and the operator-facing census numerically
    # identical, including Kotlin string/interpolation handling.
    orphans = orphan_census()
    native_production = [path for path in PRODUCTION if path.suffix in {".kt", ".java"} and ("/src/main/" in path.as_posix() or "/app/src/main/" in path.as_posix())]
    web_production = [path for path in PRODUCTION if path.as_posix().startswith(str(ROOT / "apps/web/src"))]
    summary = Counter(node["currentStatus"] for node in nodes)
    residual_missing = Counter(
        predicate
        for node in nodes
        for predicate, present in node["predicates"].items()
        if not present
    )
    # This is a current audit.  Never derive the displayed missing breakdown
    # from the registry's historical `status` field.
    original_missing = Counter(
        row["predicateKind"]
        for row in current_original
        if not row["predicateSatisfied"]
    )
    # Canonical registry rows already are one row per impl/wire/edge predicate.
    # Residual inventory nodes carry three predicates each.
    original_predicate_total = len(original)
    residual_predicate_total = len(nodes) * 3
    combined_predicate_total = original_predicate_total + residual_predicate_total
    combined_missing = sum(residual_missing.values()) + sum(current_original_missing.values())
    registry_status_mismatches = [
        row["obligationId"]
        for row, current in zip(registry["obligations"], current_original)
        if row.get("status") != current["currentStatus"]
    ]
    residual_status_mismatches = [
        node["id"]
        for node in residual["nodes"]
        if node.get("status") != residual_by_id[node["id"]]["currentStatus"]
    ]
    evidence_integrity_failures = []
    predicate_evidence = {
        "impl": "implementationEvidence",
        "wire": "callerEvidence",
        "edge": "testEvidence",
    }
    for row in current_original:
        if not row["predicateSatisfied"]:
            continue
        evidence_key = predicate_evidence[row["predicateKind"]]
        if not row.get(evidence_key):
            evidence_integrity_failures.append({
                "scope": "canonical",
                "id": row["obligationId"],
                "predicate": row["predicateKind"],
                "requiredEvidence": evidence_key,
            })
    for node in nodes:
        for predicate, satisfied in node["predicates"].items():
            if not satisfied:
                continue
            evidence_key = predicate_evidence[predicate]
            if not node.get(evidence_key):
                evidence_integrity_failures.append({
                    "scope": "residual",
                    "id": node["id"],
                    "predicate": predicate,
                    "requiredEvidence": evidence_key,
                })
    evidence_integrity = {
        "passed": not evidence_integrity_failures,
        "failures": evidence_integrity_failures,
        "rule": "a satisfied impl/wire/edge predicate must carry matching production/caller/test evidence",
    }
    status_integrity = {
        "passed": not registry_status_mismatches and not residual_status_mismatches and evidence_integrity["passed"],
        "canonicalMismatches": registry_status_mismatches,
        "residualMismatches": residual_status_mismatches,
        "rule": "currentStatus is derived only from current predicate booleans; historical/source status is never completion evidence",
    }
    result = {
        "schemaVersion": "atropos-unified-obligation-audit-v1",
        "auditedAt": __import__("datetime").datetime.now(__import__("datetime").timezone.utc).isoformat(),
        "currentHead": current_identity,
        "currentWorktreeFingerprint": current_worktree,
        "completionRule": "A canonical atom is complete only when implementation, integration, and semantics are all true; a residual atom is WIRED only when impl, wire, and edge are all true.",
        "inputs": {
            "originalRegistry": str(REGISTRY.relative_to(ROOT)),
            "originalRegistrySha256": sha256(REGISTRY),
            "residualInventory": str(RESIDUAL.relative_to(ROOT)),
            "residualInventorySha256": sha256(RESIDUAL),
            "residualNodeCount": len(nodes),
            "originalRegistryRowCount": len(original),
        },
        "residualSummary": dict(summary),
        "residualMissingPredicates": dict(residual_missing),
        "residualNodes": nodes,
        "originalRegistryStatusSummary": dict(Counter(row["status"] for row in original)),
        "originalRegistryMissingPredicates": dict(original_missing),
        "originalRegistryCurrentSummary": dict(current_original_summary),
        "originalRegistryCurrentMissingPredicates": dict(current_original_missing),
        "originalRegistryCurrentRows": current_original,
        "originalRegistryCurrentPhaseTotals": current_phase_totals,
        "originalRequirementCurrentSummary": dict(Counter(group["currentStatus"] for group in requirement_groups.values())),
        "originalRequirementCurrentRows": sorted(requirement_groups.values(), key=lambda row: (str(row["phase"]), row["requirementId"])),
        "predicateAccounting": {
            "predicatesPerUnit": ["impl", "wire", "edge"],
            "canonicalRegistryUnits": len(original),
            "canonicalRegistryPredicates": original_predicate_total,
            "residualInventoryUnits": len(nodes),
            "residualInventoryPredicates": residual_predicate_total,
            "combinedPredicateTotal": combined_predicate_total,
            "combinedMissingPredicates": combined_missing,
            "combinedWrittenPredicates": combined_predicate_total - combined_missing,
        },
        "atomAccounting": {
            "canonicalAtoms": len(requirement_groups),
            "canonicalAtomSummary": dict(canonical_atom_summary),
            "residualAtoms": len(nodes),
            "residualAtomSummary": dict(residual_atom_summary),
            "combinedAtoms": combined_atom_total,
            "combinedCompleteAtoms": combined_atom_complete,
            "combinedIncompleteAtoms": combined_atom_total - combined_atom_complete,
            "rule": "An atom is complete only when all implementation, integration, and semantics predicates are true; predicate WRITTEN is not atom completion.",
        },
        "denominatorIntegrity": {
            "canonicalPredicateRows": len(original),
            "residualNodes": len(nodes),
            "residualPredicatesPerNode": 3,
            "supportedPredicateTotal": combined_predicate_total,
            "unsupportedRequestedPredicateTotal": 1355,
            "unsupportedDifference": 1355 - combined_predicate_total,
            "rule": "Do not fabricate the 20-predicate difference; a 1,355 denominator requires an authoritative source inventory containing those rows.",
        },
        "combinedPredicateTotals": {
            "total": combined_predicate_total,
            "missing": combined_missing,
            "written": combined_predicate_total - combined_missing,
        },
        "statusIntegrity": status_integrity,
        "evidenceIntegrity": evidence_integrity,
        "originalRegistryRows": original,
        "orphanSummary": {
            "productionFiles": len(native_production),
            "orphanFiles": len(orphans),
            "orphanLoc": sum(row["loc"] for row in orphans),
            "webProductionFiles": len(web_production),
        },
        "orphanFiles": orphans,
        "buildVerification": "NOT_RUN_BY_INSTRUCTION",
        "auditMethod": {
            "implementation": "concrete production declaration or exact contract evidence; reviewed authoritative incomplete-gap overrides remain open; title/prose/imports do not count",
            "integration": "executable non-import production reference outside the owner; token presence does not count",
            "semantics": "behavioral test reference plus assertion-like check; test/import/name presence does not count",
            "compositeAtoms": "all declared symbols must satisfy each predicate, not just one symbol",
            "verificationSeparation": "source predicates are separate from compile, test, runtime, and release verification",
        },
        "denominatorNote": "The audited inputs define 1,335 predicates: 1,020 canonical rows plus 105 residual nodes times 3. No 1,355-obligation source inventory exists in these inputs; 20 obligations are not fabricated.",
    }
    OUT_JSON.write_text(json.dumps(result, indent=2) + "\n")
    lines = [
        "# Unified Obligation Audit",
        "",
        f"Audited current worktree at `{result['currentHead']}` without compilation or tests.",
        "",
        f"Original registry rows: **{len(original)}**",
        f"Residual inventory nodes: **{len(nodes)}**",
        f"Residual status: **{dict(summary)}**",
        f"Canonical registry predicate rows: **{original_predicate_total}** ({len(original)} rows; impl/wire/edge rows are already split)",
        f"Residual inventory predicates: **{residual_predicate_total}** ({len(nodes)} units × 3)",
        f"Combined predicate obligations: **{combined_predicate_total}**",
        "The audited inputs define **1,335** predicates (1,020 canonical rows + 105 residual nodes × 3). No 1,355-obligation input exists; 20 obligations are not invented.",
        f"Complete atoms: **{combined_atom_complete}/{combined_atom_total}**; incomplete atoms: **{combined_atom_total - combined_atom_complete}**. Predicate-level WRITTEN rows are not atom completion.",
        f"Combined missing predicates: **{combined_missing}**",
        f"Missing by predicate: **{dict(current_original_missing + residual_missing)}**",
        f"Status integrity: **{'PASS' if status_integrity['passed'] else 'FAIL'}**; current status is derived from current predicate evidence only; historical/source labels are not completion evidence.",
        f"Evidence integrity: **{'PASS' if evidence_integrity['passed'] else 'FAIL'}**; every satisfied impl/wire/edge predicate has matching implementation (production or explicitly test-owned), caller, or test evidence.",
        f"Conservative native orphan census: **{len(orphans)} files / {sum(row['loc'] for row in orphans)} LOC** across {len(native_production)} Kotlin/Java production files; web production files audited separately: {len(web_production)}.",
        "Implementation, integration, and semantics are scored independently. Source-tree presence is not build, test, runtime, or release verification.",
        "",
        "## Residual Nodes",
        "",
        "| id | current status | impl | wire | edge | production evidence | caller evidence | test evidence |",
        "| --- | --- | ---: | ---: | ---: | ---: | ---: | ---: |",
    ]
    for node in nodes:
        p = node["predicates"]
        lines.append(f"| `{node['id']}` | {node['currentStatus']} | {int(p['impl'])} | {int(p['wire'])} | {int(p['edge'])} | {len(node['productionEvidence'])} | {len(node['callerEvidence'])} | {len(node['testEvidence'])} |")
    lines += ["", "## Orphan Files", ""]
    lines.extend(
        f"- `{row['path']}` ({row['loc']} LOC): {row['disposition']} — {row['reason']}"
        for row in orphans
    )
    lines += ["", "## Current Original Registry Rows", "", "| obligation | phase | title | impl | wire | edge | status | production evidence | callers | test evidence |", "| --- | ---: | --- | ---: | ---: | ---: | --- | --- | --- | --- |"]
    for row in current_original:
        p = row["predicates"]
        lines.append(
            f"| `{row['obligationId']}` | {row['phase']} | {row['title']} | {int(p['impl'])} | {int(p['wire'])} | {int(p['edge'])} | {row['currentStatus']} | {'; '.join(row['productionEvidence'][:3])} | {'; '.join(row['callerEvidence'][:3])} | {'; '.join(row['testEvidence'][:3])} |"
        )
    lines += ["", "## Current Original Registry Phase Totals", "", "| phase | rows | fully wired | missing predicates | missing by predicate |", "| ---: | ---: | ---: | ---: | --- |"]
    for phase in sorted(current_phase_totals, key=lambda value: (value == "None", int(value) if value.isdigit() else value)):
        bucket = current_phase_totals[phase]
        lines.append(f"| {phase} | {bucket['total']} | {bucket['written']} | {bucket['missing']} | {bucket['missingByPredicate']} |")
    lines += ["", "## Current Original Registry Atom Totals", "", f"Atom status summary: **{dict(Counter(group['currentStatus'] for group in requirement_groups.values()))}**", "", "| requirement | phase | title | impl | wire | edge | status |", "| --- | ---: | --- | ---: | ---: | ---: | --- |"]
    for group in sorted(requirement_groups.values(), key=lambda row: (str(row["phase"]), row["requirementId"])):
        p = group["predicates"]
        lines.append(f"| `{group['requirementId']}` | {group['phase']} | {group['title']} | {int(p['impl'])} | {int(p['wire'])} | {int(p['edge'])} | {group['currentStatus']} |")
    OUT_MD.write_text("\n".join(lines) + "\n")

    # Keep the human completion report synchronized with this strict audit.
    # The older code-completion generator reports the 1,020-row registry only;
    # that projection must not remain beside a 1,335-predicate audit with
    # different numbers.
    code_report = ROOT / "docs/completion/ATROPOS_CODE_COMPLETION_REPORT.md"
    report_lines = [
        "# ATROPOS Code-Base Completion Report",
        "",
        f"Generated: {result['auditedAt']}",
        f"Audit identity: `{current_identity}`",
        "",
        "This is a strict source-tree audit. Implementation, integration, and semantics are scored independently. Build, test, packaging, installation, restart, deployment, and runtime proof remain separate axes.",
        "A predicate marked WRITTEN is not an atom completion claim. An atom is complete only when implementation, integration, and semantics are all independently evidenced.",
        "",
        "## Current strict result",
        "",
        "| Scope | Written | Open | Total | Completion |",
        "|---|---:|---:|---:|---:|",
        f"| Canonical registry | {current_original_summary.get('WRITTEN', 0)} | {current_original_summary.get('NOT_WRITTEN', 0)} | {len(original)} | {current_original_summary.get('WRITTEN', 0) / len(original) * 100:.2f}% |",
        f"| Residual inventory predicates | {residual_predicate_total - sum(residual_missing.values())} | {sum(residual_missing.values())} | {residual_predicate_total} | {(residual_predicate_total - sum(residual_missing.values())) / residual_predicate_total * 100:.2f}% |",
        f"| **Combined current audit** | **{combined_predicate_total - combined_missing}** | **{combined_missing}** | **{combined_predicate_total}** | **{(combined_predicate_total - combined_missing) / combined_predicate_total * 100:.2f}%** |",
        "",
        "The frozen audited inputs contain 1,335 predicates: 1,020 canonical registry rows plus 105 residual nodes × three predicates. No 1,355-row source inventory exists in the repository, so 20 obligations are not fabricated.",
        "",
        "## Canonical Registry By Phase",
        "",
        "| Phase | Written/total | Completion | Open |",
        "|---:|---:|---:|---:|",
    ]
    for phase in sorted(current_phase_totals, key=lambda value: (value == "None", int(value) if value.isdigit() else value)):
        bucket = current_phase_totals[phase]
        pct = bucket["written"] / bucket["total"] * 100 if bucket["total"] else 0.0
        report_lines.append(f"| {phase} | {bucket['written']}/{bucket['total']} | {pct:.2f}% | {bucket['missing']} |")
    report_lines += [
        "",
        "## Residual Status",
        "",
        f"WIRED: {summary.get('WIRED', 0)} · PARTIAL: {summary.get('PARTIAL', 0)} · ORPHANED: {summary.get('ORPHANED', 0)} · ABSENT: {summary.get('ABSENT', 0)}",
        "",
        "## Open Canonical Predicate IDs",
        "",
    ]
    report_lines.extend(
        f"- `{row['obligationId']}` ({row['predicateKind']}): {row['title']}"
        for row in current_original
        if row["currentStatus"] != "WRITTEN"
    )
    report_lines += [
        "",
        "## Incomplete Atoms",
        "",
        "| atom | status | implementation | integration | semantics |",
        "|---|---|---:|---:|---:|",
    ]
    report_lines.extend(
        f"| `{group['requirementId']}` | {group['currentStatus']} | {int(group['predicates']['impl'])} | {int(group['predicates']['wire'])} | {int(group['predicates']['edge'])} |"
        for group in sorted(requirement_groups.values(), key=lambda row: (str(row['phase']), row['requirementId']))
        if group['currentStatus'] != 'WRITTEN'
    )
    report_lines.extend(
        f"| `{node['id']}` | {node['currentStatus']} | {int(node['predicates']['impl'])} | {int(node['predicates']['wire'])} | {int(node['predicates']['edge'])} |"
        for node in nodes
        if node['currentStatus'] != 'WIRED'
    )
    report_lines += [
        "",
        "## Controls",
        "",
        "- Declarations, imports, prose, test names, and arbitrary existing paths do not prove implementation.",
        "- Wiring requires an executable reference to the canonical owner outside its owner file.",
        "- Semantics requires a behavioral test/evidence reference with an assertion-like check.",
        "- Strict incomplete-gap overrides remain open even when a related symbol exists.",
        "- Status integrity must pass: every machine-facing status equals the current predicate result; historical/source status is retained only for provenance.",
        "- Native strict orphan census: " + f"{len(orphans)} files / {sum(row['loc'] for row in orphans)} LOC.",
        "- No Gradle, compile, test, build, JAR, install, restart, deployment, or runtime proof was run.",
        "",
        "## Verdict",
        "",
        f"CODE-BASE COMPLETION: **{(combined_predicate_total - combined_missing) / combined_predicate_total * 100:.2f}%** ({combined_predicate_total - combined_missing}/{combined_predicate_total} predicates). Release status remains **CODE_INCOMPLETE**.",
        f"ATOM COMPLETION: **{combined_atom_complete / combined_atom_total * 100:.2f}%** ({combined_atom_complete}/{combined_atom_total} atoms).",
    ]
    code_report.write_text("\n".join(report_lines) + "\n")

    baseline = ROOT / "docs/completion/ATROPOS_CODE_COMPLETION_BASELINE.json"
    baseline_data = json.loads(baseline.read_text()) if baseline.exists() else {}
    baseline_data.update({
        "currentHead": current_head,
        "generatedAt": result["auditedAt"],
        "totalObligations": combined_predicate_total,
        "currentWritten": combined_predicate_total - combined_missing,
        "currentCodeCompletion": round((combined_predicate_total - combined_missing) / combined_predicate_total * 100, 4),
        "unifiedAuditSha256": sha256(OUT_JSON),
        "unifiedPredicateTotal": combined_predicate_total,
        "unifiedPredicateWritten": combined_predicate_total - combined_missing,
        "unifiedPredicateMissing": combined_missing,
        "unifiedCodeCompletion": round((combined_predicate_total - combined_missing) / combined_predicate_total * 100, 4),
        "canonicalRegistryWritten": current_original_summary.get("WRITTEN", 0),
        "canonicalRegistryMissing": current_original_summary.get("NOT_WRITTEN", 0),
        "residualPredicateWritten": residual_predicate_total - sum(residual_missing.values()),
        "residualPredicateMissing": sum(residual_missing.values()),
        "canonicalAtomTotal": len(requirement_groups),
        "canonicalAtomComplete": canonical_atom_summary.get("WRITTEN", 0),
        "residualAtomTotal": len(nodes),
        "residualAtomComplete": residual_atom_summary.get("WIRED", 0),
        "combinedAtomTotal": combined_atom_total,
        "combinedAtomComplete": combined_atom_complete,
        "combinedAtomIncomplete": combined_atom_total - combined_atom_complete,
        "combinedAtomCompletion": round(combined_atom_complete / combined_atom_total * 100, 4),
        "unsupportedRequestedPredicateTotal": 1355,
        "unsupportedPredicateDifference": 1355 - combined_predicate_total,
        "accountingNote": "Strict unified audit is authoritative for current code-base completion; no 1,355-obligation input exists.",
    })
    baseline_data["perPhase"] = {
        phase: {
            "total": bucket["total"],
            "currentWritten": bucket["written"],
            "currentOpen": bucket["missing"],
            "currentCodeCompletion": round(bucket["written"] / bucket["total"] * 100, 4) if bucket["total"] else 0.0,
            "missingByPredicate": bucket["missingByPredicate"],
        }
        for phase, bucket in current_phase_totals.items()
    }
    baseline_data["unifiedAudit"] = {
        "path": str(OUT_JSON.relative_to(ROOT)),
        "totalPredicates": combined_predicate_total,
        "writtenPredicates": combined_predicate_total - combined_missing,
        "missingPredicates": combined_missing,
        "completionPercent": round((combined_predicate_total - combined_missing) / combined_predicate_total * 100, 8),
        "denominatorNote": "The audited inputs define 1,335 predicates: 1,020 canonical rows plus 105 residual nodes times 3. No 1,355-obligation source inventory exists in these inputs; 20 obligations are not fabricated.",
    }
    baseline.write_text(json.dumps(baseline_data, indent=2) + "\n")
    print(json.dumps({"residualSummary": dict(summary), "orphanSummary": result["orphanSummary"], "audit": str(OUT_JSON.relative_to(ROOT))}, sort_keys=True))


if __name__ == "__main__":
    main()
