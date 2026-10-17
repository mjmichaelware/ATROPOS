/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.orchestration

import atropos.core.agent.AgentQueueService
import atropos.core.agent.AgentJobStore
import atropos.core.dag.DagDefinition
import atropos.core.dag.DagExecutionService
import atropos.core.dag.DagNode
import atropos.core.dag.DagNodeState
import atropos.core.dag.DagStore
import atropos.core.factory.Deployment
import atropos.core.factory.DeploymentEnvironment
import atropos.core.factory.FactoryAcceptanceFreeze
import atropos.core.factory.FactoryObligationLoop
import atropos.core.factory.FactoryTerminationBudget
import atropos.core.factory.FactoryProgressGuard
import atropos.core.factory.TargetPlatform
import atropos.core.memory.LocalMemoryStore
import atropos.core.ops.VercelDeploymentDriver
import atropos.core.planning.PartitionCluster
import atropos.core.policy.BoundedAgencyGate
import atropos.core.policy.ExecutionPolicyEngine
import atropos.core.provider.ProviderCascadeRouter
import atropos.core.provider.ProviderOnboardingService
import atropos.core.territory.TerritoryGrantService
import atropos.core.territory.TerritoryService
import atropos.core.territory.TerritoryStore
import atropos.core.verification.VerifiedCompletionGate
import atropos.core.worktree.IsolatedWorktreeService
import atropos.core.AtroposRepoRootLocator
import atropos.core.ProviderFactory
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * Autonomous wave executor that drives the factory DAG through typed waves.
 *
 * Replaces the old template-driven generation pipeline. All synthesis originates
 * from the Wave-0 ContractLatticeSynthesizer; no static templates remain.
 */
class AutonomousWaveOrchestrator(
    private val repoRoot: Path,
    private val dagStore: DagStore,
    private val executionService: DagExecutionService,
    private val worktreeService: IsolatedWorktreeService,
    private val territoryService: TerritoryGrantService,
    private val completionGate: VerifiedCompletionGate,
    private val progressGuard: FactoryProgressGuard,
    private val agentQueue: AgentQueueService,
    private val agentJobStore: AgentJobStore,
    private val memoryStore: LocalMemoryStore,
    private val onboarding: ProviderOnboardingService,
    private val agencyGate: BoundedAgencyGate,
    private val contractSynthesizer: ContractLatticeSynthesizer
) {
    private val researchOrchestrator: atropos.core.research.ResearchOrchestrator =
        atropos.core.research.ResearchOrchestrator(atropos.core.research.SoftFailResearchStack(memoryStore))
    private val confidenceEngine: atropos.core.confidence.HysteresisConfidenceEngine =
        atropos.core.confidence.HysteresisConfidenceEngine()
    private val partitionClusterManager: atropos.core.planning.PartitionClusterManager =
        atropos.core.planning.PartitionClusterManager()

    private val waveBatchCoordinator = WaveBatchExecutionCoordinator(
        repoRoot = repoRoot,
        dagStore = dagStore,
        worktreeService = worktreeService,
        completionGate = completionGate,
        memoryStore = memoryStore,
        contractSynthesizer = contractSynthesizer
    )

    private val deploymentSeam = AutonomousDeploymentSeam(
        agencyGate = agencyGate,
        memoryStore = memoryStore
    )

    companion object {
        fun createDefault(repoRoot: Path): AutonomousWaveOrchestrator {
            val dagStore = DagStore(repoRoot)
            val worktreeService = IsolatedWorktreeService(repoRoot)
            val territoryService = TerritoryGrantService(TerritoryService(TerritoryStore(repoRoot)))
            val completionGate = VerifiedCompletionGate()
            val progressGuard = FactoryProgressGuard(dagStore)
            val agentQueue = AgentQueueService()
            val agentJobStore = AgentJobStore(repoRoot)
            val memoryStore = LocalMemoryStore(repoRoot.resolve(".atropos/memory").toFile())
            val onboarding = ProviderOnboardingService()
            val agencyGate = BoundedAgencyGate(
                policyEngine = ExecutionPolicyEngine(repoRoot),
                territory = territoryService
            )
            val contractSynthesizer = ContractLatticeSynthesizer(repoRoot)
            val executionService = DagExecutionService(
                repoRoot = repoRoot,
                worktreeService = worktreeService,
                territoryService = territoryService,
                completionGate = completionGate
            )
            return AutonomousWaveOrchestrator(
                repoRoot = repoRoot,
                dagStore = dagStore,
                executionService = executionService,
                worktreeService = worktreeService,
                territoryService = territoryService,
                completionGate = completionGate,
                progressGuard = progressGuard,
                agentQueue = agentQueue,
                agentJobStore = agentJobStore,
                memoryStore = memoryStore,
                onboarding = onboarding,
                agencyGate = agencyGate,
                contractSynthesizer = contractSynthesizer
            )
        }
    }

    constructor(
        researchOrchestrator: atropos.core.research.ResearchOrchestrator,
        confidenceEngine: atropos.core.confidence.HysteresisConfidenceEngine,
        partitionManager: atropos.core.planning.PartitionClusterManager,
        latticeSynthesizer: atropos.core.contract.ContractLatticeSynthesizer,
        workerSynthesizer: atropos.core.synthesis.DecoupledWorkerSynthesizer,
        diAssembler: atropos.core.composition.DependencyInjectionAssembler,
        toolchains: atropos.core.toolchain.NativeToolchainRegistry
    ) : this(
        repoRoot = AtroposRepoRootLocator.resolve(),
        dagStore = DagStore(AtroposRepoRootLocator.resolve()),
        executionService = DagExecutionService(repoRoot = AtroposRepoRootLocator.resolve()),
        worktreeService = IsolatedWorktreeService(AtroposRepoRootLocator.resolve()),
        territoryService = TerritoryGrantService(TerritoryService(TerritoryStore(AtroposRepoRootLocator.resolve()))),
        completionGate = VerifiedCompletionGate(),
        progressGuard = FactoryProgressGuard(DagStore(AtroposRepoRootLocator.resolve())),
        agentQueue = AgentQueueService(),
        agentJobStore = AgentJobStore(AtroposRepoRootLocator.resolve()),
        memoryStore = LocalMemoryStore(AtroposRepoRootLocator.resolve().resolve(".atropos/memory").toFile()),
        onboarding = ProviderOnboardingService(),
        agencyGate = BoundedAgencyGate(
            policyEngine = ExecutionPolicyEngine(AtroposRepoRootLocator.resolve()),
            territory = TerritoryGrantService(TerritoryService(TerritoryStore(AtroposRepoRootLocator.resolve())))
        ),
        contractSynthesizer = ContractLatticeSynthesizer(AtroposRepoRootLocator.resolve())
    )

    fun executeOrchestration(
        promptText: String,
        projectId: String,
        targetDir: Path
    ): OrchestrationReport {
        val promptArtifact = atropos.core.research.PromptArtifactStore.persist(
            prompt = promptText,
            projectId = projectId,
            dir = targetDir.resolve(".atropos/prompts")
        )

        val doc = researchOrchestrator.research(promptArtifact)
        val action = confidenceEngine.evaluate(doc)
        if (action == atropos.core.confidence.ConfidenceAction.PARETO_DECISION_PAUSE) {
            return OrchestrationReport(
                success = false,
                artifactPath = null,
                h0 = promptArtifact.h0,
                hDoc = doc.hDoc,
                stageReached = "RESEARCH_PARETO_PAUSE",
                failureReason = "Confidence score ${doc.confidence.score} triggered PARETO_DECISION_PAUSE"
            )
        }

        val dagDef = DagDefinition(
            id = "dag-$projectId",
            label = "Wave DAG: $projectId",
            nodes = listOf(
                DagNode(id = "node-1", label = "Core", territory = listOf("src/main/kotlin"), state = DagNodeState.READY)
            ),
            projectId = projectId
        )
        val clusters = partitionClusterManager.partition(dagDef)
        val cluster = clusters.firstOrNull() ?: PartitionCluster(
            clusterId = "c0",
            nodes = emptyList(),
            tokenCount = 0,
            assignedTerritory = emptySet()
        )

        val contractLatticeSynth = atropos.core.contract.ContractLatticeSynthesizer()
        val lattice = contractLatticeSynth.synthesize(doc, cluster, targetDir)

        val testSuiteGen = atropos.core.verification.Wave1TestSuiteGenerator()
        testSuiteGen.generate(lattice, targetDir)

        val workerSynth = atropos.core.synthesis.DecoupledWorkerSynthesizer(
            worktreeService = worktreeService,
            cascadeRouter = ProviderCascadeRouter(factory = ProviderFactory())
        )
        workerSynth.synthesizeCluster(cluster, lattice, promptArtifact.h0)

        val diAssembler = atropos.core.composition.DependencyInjectionAssembler()
        val diModule = diAssembler.assemble(lattice, mapOf(), targetDir)

        val driver = atropos.core.toolchain.NativeToolchainRegistry.getDriver("kotlin")
            ?: atropos.core.toolchain.NativeToolchainRegistry.requireDriver("kotlin")
        val compileResult = driver.compile(targetDir)

        return OrchestrationReport(
            success = compileResult.passed && compileResult.exitCode == 0,
            artifactPath = if (compileResult.passed) diModule else null,
            h0 = promptArtifact.h0,
            hDoc = doc.hDoc,
            stageReached = if (compileResult.passed) "WAVE_4_GLOBAL_VERIFIED" else "WAVE_4_COMPILE_ERROR",
            failureReason = if (compileResult.passed) null else compileResult.stderr
        )
    }

    /**
     * Execute a wave request through the full autonomous cycle.
     */
    fun execute(request: WaveExecutionRequest): WaveExecutionResult {
        val startTime = Instant.now()
        val requestId = request.requestId

        memoryStore.rememberDetailed(
            kind = atropos.core.memory.MemoryKind.NOTE,
            title = "wave execution started: $requestId",
            body = "prompt=${request.prompt.take(200)} target=${request.targetDirectory}",
            tags = listOf("wave", "start", "orchestrator"),
            subjectType = "wave_execution",
            subjectId = requestId
        )

        val promptArtifact = atropos.core.research.PromptArtifactStore.persist(
            prompt = request.prompt,
            projectId = request.requestId,
            dir = repoRoot.resolve(".atropos/prompts")
        )
        val researchDoc = researchOrchestrator.research(promptArtifact)
        confidenceEngine.evaluate(researchDoc)
        atropos.core.neurosymbolic.EpistemicUncertaintyBounding().checkBound(researchDoc.confidence)
        atropos.core.neurosymbolic.ResidualObligationLedger(repoRoot.resolve(".atropos/obligations.jsonl")).activeObligations()

        val dag = contractSynthesizer.synthesize(request)
        val createdDag = dagStore.createDag(dag.label, dag.nodes, dag.projectId)

        val freeze = FactoryAcceptanceFreeze.create(
            promptSha256 = request.lineage.promptSha256 ?: "",
            researchSha256 = request.lineage.researchSha256 ?: "",
            atomIds = request.plannedAtomIds.ifEmpty { createdDag.nodes.map { it.id } },
            promptSpans = request.lineage.promptSpans ?: ""
        )

        val runRoot = repoRoot.resolve(".atropos/research/factory/$requestId")
        Files.createDirectories(runRoot)
        val planPath = runRoot.resolve("plan.md")
        if (!Files.exists(planPath)) {
            Files.writeString(planPath, "# Factory Plan for $requestId\ndag=${createdDag.id}\nprompt=${request.prompt}\n")
        }
        val freezePath = runRoot.resolve("acceptance-freeze.md")
        Files.writeString(freezePath, freeze.document)

        val obligationLoop = FactoryObligationLoop(dagStore, FactoryTerminationBudget(
            maxExecutionWaves = request.budget.maxExecutionWaves
        ))

        val initialSnapshot = obligationLoop.snapshot(createdDag)
        atropos.core.factory.FactoryRunHandoff.write(
            repoRoot = repoRoot,
            runId = requestId,
            dagId = createdDag.id,
            snapshot = initialSnapshot,
            freeze = freeze
        )

        val executeWave: (List<DagNode>) -> Set<String> = { readyNodes ->
            waveBatchCoordinator.executeWaveBatch(readyNodes, request, freeze)
        }

        val loopResult = obligationLoop.executeUntilSettled(createdDag.id, freeze, executeWave)

        val finalDag = dagStore.readDag(createdDag.id)
        val result = if (finalDag != null) {
            waveBatchCoordinator.collectResults(finalDag, loopResult, request)
        } else {
            WaveExecutionResult(
                requestId = requestId,
                success = false,
                completedAtoms = emptyList(),
                failedAtoms = listOf(createdDag.id),
                blockedAtoms = emptyList(),
                message = "DAG vanished after execution",
                evidencePaths = emptyList(),
                gitCommit = null,
                treeSha256 = null
            )
        }

        val finalSnapshot = obligationLoop.snapshot(finalDag ?: createdDag)
        atropos.core.factory.FactoryRunHandoff.write(
            repoRoot = repoRoot,
            runId = requestId,
            dagId = createdDag.id,
            snapshot = finalSnapshot,
            freeze = freeze,
            evidencePath = result.evidencePaths.firstOrNull()
        )

        if (result.success) {
            atropos.core.neurosymbolic.ChaosDigitalTwinRunner(10).runChaosSimulation(request.targetDirectory)
        }

        memoryStore.rememberDetailed(
            kind = atropos.core.memory.MemoryKind.NOTE,
            title = "wave execution finished: $requestId",
            body = "success=${result.success} completed=${result.completedAtoms.size} failed=${result.failedAtoms.size} blocked=${result.blockedAtoms.size} duration=${Instant.now().toEpochMilli() - startTime.toEpochMilli()}ms",
            tags = listOf("wave", if (result.success) "success" else "failure", "orchestrator"),
            subjectType = "wave_execution",
            subjectId = requestId
        )

        return result
    }

    /**
     * Resume an existing DAG execution from its durable handoff and acceptance freeze.
     */
    fun resume(
        request: WaveExecutionRequest,
        dagId: String,
        freeze: FactoryAcceptanceFreeze
    ): WaveExecutionResult {
        val requestId = request.requestId
        atropos.core.thinking.Thinking.step(
            "resume",
            "AutonomousWaveOrchestrator resuming run '$requestId' on DAG '$dagId' with freeze_sha256=${freeze.sha256}"
        )
        val dag = dagStore.readDag(dagId)
            ?: return WaveExecutionResult(
                requestId = requestId,
                success = false,
                completedAtoms = emptyList(),
                failedAtoms = listOf(dagId),
                blockedAtoms = emptyList(),
                message = "DAG '$dagId' vanished or not found in DagStore",
                evidencePaths = emptyList(),
                gitCommit = null,
                treeSha256 = null
            )

        val obligationLoop = FactoryObligationLoop(dagStore, FactoryTerminationBudget(
            maxExecutionWaves = request.budget.maxExecutionWaves
        ))

        val loopResult = obligationLoop.executeUntilSettled(dagId, freeze) { readyNodes ->
            waveBatchCoordinator.executeWaveBatch(readyNodes, request, freeze)
        }

        val finalDag = dagStore.readDag(dagId)
        val result = if (finalDag != null) {
            waveBatchCoordinator.collectResults(finalDag, loopResult, request)
        } else {
            WaveExecutionResult(
                requestId = requestId,
                success = false,
                completedAtoms = emptyList(),
                failedAtoms = listOf(dagId),
                blockedAtoms = emptyList(),
                message = "DAG vanished after resumed execution",
                evidencePaths = emptyList(),
                gitCommit = null,
                treeSha256 = null
            )
        }

        val finalSnapshot = obligationLoop.snapshot(finalDag ?: dag)
        atropos.core.factory.FactoryRunHandoff.write(
            repoRoot = repoRoot,
            runId = requestId,
            dagId = dagId,
            snapshot = finalSnapshot,
            freeze = freeze,
            evidencePath = result.evidencePaths.firstOrNull()
        )
        return result
    }

    fun deployCloud(
        adapter: CloudDeploymentAdapter,
        targetDir: Path,
        domain: String
    ): CloudDeploymentResult = deploymentSeam.deployCloud(adapter, targetDir, domain)

    fun deliverAutonomous(
        platform: TargetPlatform,
        env: DeploymentEnvironment,
        domain: String,
        gitCommitHash: String,
        worktreePath: Path
    ): Deployment = deploymentSeam.deliverAutonomous(platform, env, domain, gitCommitHash, worktreePath)

    fun validateTokens() = deploymentSeam.validateTokens()

    fun verifyVercelWireContract(driver: VercelDeploymentDriver, sampleBytes: ByteArray): Map<String, String> =
        deploymentSeam.verifyVercelWireContract(driver, sampleBytes)

    fun handleStageFailure(crashContext: atropos.core.sentry.SentryCrashContext): atropos.core.sentry.SentryPromptRepairResult {
        val coordinator = atropos.core.sentry.SentryRepairCoordinator(repoRoot)
        return coordinator.generateRepairPrompt(crashContext)
    }

    fun benchmark(predictionsJsonl: java.io.File, runId: String, token: String): Int {
        val evaluator = atropos.core.benchmark.SweBenchCloudEvaluator()
        return evaluator.submitPredictions(predictionsJsonl, runId, token)
    }
}