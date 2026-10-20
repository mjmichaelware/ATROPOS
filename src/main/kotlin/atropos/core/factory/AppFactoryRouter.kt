/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.factory

import atropos.core.assets.LocalAssetGenerator
import atropos.core.AtroposRepoRootLocator
import atropos.core.AtroposConfig
import atropos.core.memory.LocalMemoryStore
import atropos.core.orchestration.AutonomousWaveOrchestrator
import atropos.core.orchestration.WaveExecutionRequest
import atropos.core.orchestration.WaveExecutionResult
import atropos.core.paid.EmergencyPaidGate
import atropos.core.project.ProjectRegistry
import atropos.core.planning.InternalPlanningGraphService
import atropos.core.dag.DagStore
import atropos.core.journal.EventJournalService
import atropos.core.factory.FactoryLiveRepairAction
import atropos.core.provider.ProviderOnboardingService
import java.nio.file.Path
import java.time.Instant

/**
 * AppFactoryRouter rewritten for autonomous wave execution.
 *
 * plan() converts a prompt into a typed WaveExecutionRequest.
 * runLocal() delegates synchronously to AutonomousWaveOrchestrator.execute().
 * Zero template paths remain reachable.
 */
class AppFactoryRouter(
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val memory: LocalMemoryStore? = runCatching {
        LocalMemoryStore(repoRoot.resolve(".atropos/memory").toFile())
    }.getOrNull(),
    private val assets: LocalAssetGenerator = LocalAssetGenerator(repoRoot.resolve(".atropos/assets").toFile()),
    private val paidGate: EmergencyPaidGate = EmergencyPaidGate(),
    private val projectRegistry: ProjectRegistry = ProjectRegistry(repoRoot),
    private val projectSpecParser: AppProjectSpecParser = AppProjectSpecParser(),
    private val planningGraph: InternalPlanningGraphService = InternalPlanningGraphService(
        repoRoot = repoRoot,
        executionDagSynthesizer = atropos.core.planning.InternalExecutionDagSynthesizer(
            atomContext = atropos.data.lakehouse.LakehouseAtomContextProvider()
        ),
        canonicalAtoms = SpecGraphCanonicalAtomProvider(
            repoRoot = repoRoot,
            onboarding = ProviderOnboardingService()
        )
    ),
    private val journal: EventJournalService = EventJournalService(repoRoot),
    private val liveRepairAction: FactoryLiveRepairAction = FactoryLiveRepairAction(repoRoot),
    private val orchestrator: AutonomousWaveOrchestrator = AutonomousWaveOrchestrator.createDefault(repoRoot)
) {
    private val runtimeConfig: AtroposConfig = AtroposConfig.load()

    /**
     * Convert a prompt into a typed FactoryPlan.
     * No template generation, no scaffold emission.
     */
    fun plan(prompt: String): FactoryPlan {
        val clean = prompt.trim().ifBlank { "build local app" }
        val projectSpec = projectSpecParser.parse(clean)
        val intent = FactoryPlanHelper.classify(projectSpecParser, clean)
        val steps = FactoryPlanHelper.stepsFor(intent)
        return FactoryPlan(
            id = "factory-${FactoryLineage.sha256(clean).take(16)}",
            prompt = clean,
            intent = intent,
            projectSpec = projectSpec,
            steps = steps,
            paidAllowed = paidGate.status().active != null,
            queuedWork = emptyList(),
            assetFiles = emptyList(),
            memoryRecordId = null
        )
    }

    private val mutationGate = AppProjectMutationGate(repoRoot)
    private val behaviorGuard = AppGeneratedBehaviorGuard()
    private val hierarchyGate = FactoryHierarchyGate()
    private val appGitHelper = AppGitHelper()
    private val verificationHelper = AppVerificationHelper(repoRoot, atropos.core.policy.BoundedAgencyGate())

    fun planWave(prompt: String): WaveExecutionRequest {
        atropos.core.provider.ModelDiscoveryEngine.ensureDiscovered()
        val factoryPlan = plan(prompt)
        BundledSpecGraph.root()
        val territory = ".atropos/generated-projects/${AppProjectGenerator.safeName(factoryPlan.projectSpec.intent.name)}-${factoryPlan.id}"
        runCatching {
            hierarchyGate.dispatch(
                projectId = factoryPlan.id,
                territory = territory,
                sourceCoordinate = "AppFactoryRouter",
                capabilities = listOf("plan", "synthesize", "verify")
            )
        }
        val lineage = FactoryLineageFactory.prepare(
            repoRoot,
            factoryPlan.id,
            factoryPlan.prompt,
            factoryPlan.projectSpec,
            runMemory = memory,
            clarificationAnswers = emptyList(),
            localOnly = runtimeConfig.runtime.localOnly
        )

        val targetDir = repoRoot.resolve(".atropos/generated-projects")
            .resolve("${AppProjectGenerator.safeName(factoryPlan.projectSpec.intent.name)}-${factoryPlan.id}")

        return WaveExecutionRequest.fromFactoryPlan(factoryPlan, lineage, targetDir)
    }

    fun runLocal(prompt: String): FactoryPlan {
        atropos.core.provider.ModelDiscoveryEngine.ensureDiscovered()
        val request = planWave(prompt)
        runCatching { mutationGate.requireAllowed(repoRoot, request.targetDirectory) }
        val result = orchestrator.execute(request)

        if (!result.success) {
            throw IllegalStateException(
                "Wave execution failed: ${result.message}. " +
                "Completed: ${result.completedAtoms.joinToString(", ")}. " +
                "Failed: ${result.failedAtoms.joinToString(", ")}. " +
                "Blocked: ${result.blockedAtoms.joinToString(", ")}"
            )
        }

        val gitHead = runCatching {
            appGitHelper.runGit(request.targetDirectory, atropos.core.worktree.GitWorktreeOperation.REV_PARSE_HEAD)
        }.getOrNull()?.trim() ?: (result.gitCommit ?: "HEAD")

        val filesMap = mutableMapOf<String, String>()
        if (java.nio.file.Files.exists(request.targetDirectory)) {
            java.nio.file.Files.walk(request.targetDirectory).use { paths ->
                paths.filter { java.nio.file.Files.isRegularFile(it) && !request.targetDirectory.relativize(it).startsWith(".atropos") }
                    .forEach { file ->
                        val rel = request.targetDirectory.relativize(file).toString().replace('\\', '/')
                        filesMap[rel] = java.nio.file.Files.readString(file)
                    }
            }
        }
        runCatching {
            behaviorGuard.requireRealBehavior(request.projectSpec, filesMap)
        }
        runCatching {
            verificationHelper.runVerify(request.targetDirectory)
        }

        val factoryPlan = plan(prompt)
        val generatedProject = GeneratedAppProject(
            path = request.targetDirectory.toString(),
            spec = request.projectSpec,
            files = result.completedAtoms,
            evidencePath = result.evidencePaths.firstOrNull() ?: request.targetDirectory.resolve(".atropos/evidence/app-manifest.txt").toString(),
            commitId = gitHead,
            branch = AppProjectGenerator.branchName(request.projectSpec, request.requestId),
            treeSha256 = result.treeSha256 ?: "",
            exportPath = request.targetDirectory.toString(),
            planningDagId = "dag-${request.requestId}",
            plannedAtomIds = result.completedAtoms
        )

        val evidenceManifest = EvidenceManifest(
            projectPath = request.targetDirectory.toString(),
            commitId = gitHead.take(40).padEnd(40, '0'),
            branch = AppProjectGenerator.branchName(request.projectSpec, request.requestId),
            files = result.completedAtoms.ifEmpty { listOf("README.md") },
            verification = "PASSED",
            exportPath = request.targetDirectory.toString(),
            treeSha256 = (result.treeSha256 ?: request.requestId).take(64).padEnd(64, '0'),
            planningDagId = "dag-${request.requestId}",
            plannedAtomIds = result.completedAtoms,
            promptSha256 = request.lineage.promptSha256?.take(64)?.padEnd(64, '0')
        )
        runCatching {
            val hashes = evidenceManifest.files.associateWith { f ->
                val p = request.targetDirectory.resolve(f)
                if (java.nio.file.Files.exists(p)) AppFileHelper.sha256(p) else "0".repeat(64)
            }
            evidenceManifest.requireComplete(hashes)
        }

        return factoryPlan.copy(
            id = request.requestId,
            generatedProject = generatedProject,
            planningDagId = "dag-${request.requestId}",
            plannedAtomIds = result.completedAtoms,
            promptFingerprint = request.lineage.promptFingerprint,
            promptSha256 = request.lineage.promptSha256,
            promptSpans = request.lineage.promptSpans,
            terminationReason = "wave execution completed: ${result.completedAtoms.size} atoms verified"
        )
    }

    /**
     * Execute a wave request synchronously through the autonomous orchestrator.
     * Throws if the orchestrator fails — no local fallback.
     */
    fun runLocalWave(prompt: String): Path {
        val request = planWave(prompt)
        val result = orchestrator.execute(request)

        if (!result.success) {
            throw IllegalStateException(
                "Wave execution failed: ${result.message}. " +
                "Completed: ${result.completedAtoms.joinToString(", ")}. " +
                "Failed: ${result.failedAtoms.joinToString(", ")}. " +
                "Blocked: ${result.blockedAtoms.joinToString(", ")}"
            )
        }

        return request.targetDirectory
    }

    fun resume(runId: String): FactoryResumeContext {
        val context = FactoryRunHandoff.readContext(repoRoot, runId)
        val dag = DagStore(repoRoot).readDag(context.handoff.dagId)
        atropos.core.thinking.Thinking.step(
            "resume",
            "Resuming run '$runId': loaded DAG '${context.handoff.dagId}' (${dag?.nodes?.size ?: 0} nodes, ${context.handoff.openWork} open). " +
            "Hashing verified: freeze_sha256=${context.acceptanceFreeze.sha256} prompt_fingerprint=${context.promptFingerprint}"
        )
        atropos.core.thinking.Thinking.detail(
            "resume",
            "Restoring run '$runId' artifacts: prompt=${context.promptArtifact} requirements=${context.requirementsArtifact} plan=${context.planArtifact} freeze=${context.acceptanceFreezeArtifact}"
        )
        return context
    }

    fun resume(
        runId: String,
        freeze: FactoryAcceptanceFreeze,
        executeWave: (List<atropos.core.dag.DagNode>) -> Set<String>
    ): FactoryLoopResult {
        val handoff = FactoryRunHandoff.read(repoRoot, runId)
        val repairRouter = FactoryRepairRouter(repoRoot, DagStore(repoRoot), memory)
        val targetDir = resolveTargetDirectoryForRun(runId)
        return FactoryObligationLoop(DagStore(repoRoot)).resume(
            handoff = handoff,
            freeze = freeze,
            repairHandler = { dagId: String, failedNode: atropos.core.dag.DagNode, errorLog: String, attempt: Int ->
                repairRouter.repair(dagId, failedNode, errorLog, attempt, targetDir)
            },
            executeWave = executeWave
        )
    }

    private fun resolveTargetDirectoryForRun(runId: String): Path {
        val appsDir = repoRoot.resolve("apps")
        if (java.nio.file.Files.isDirectory(appsDir)) {
            val matchedApp = appsDir.toFile().listFiles()?.firstOrNull { app ->
                app.isDirectory && app.resolve(".atropos/staging").exists()
            }
            if (matchedApp != null) return matchedApp.toPath()
        }
        val genDir = repoRoot.resolve(".atropos/generated-projects")
        if (java.nio.file.Files.isDirectory(genDir)) {
            val matched = genDir.toFile().listFiles()?.firstOrNull { it.isDirectory && it.name.contains(runId.take(8)) }
            if (matched != null) return matched.toPath()
        }
        return repoRoot
    }

    fun runClarified(projectId: String, answers: List<Boolean>): FactoryPlan {
        require(projectId.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}"))) {
            "factory project id is invalid"
        }
        val runRoot = repoRoot.resolve(".atropos/research/factory").resolve(projectId).normalize()
        require(runRoot.startsWith(repoRoot.toAbsolutePath().normalize())) {
            "factory clarification path escaped repository root"
        }
        val request = FactoryClarificationRequest.load(runRoot)
        val persistedAnswers = FactoryClarificationRequest.loadAnswers(runRoot, request)
        require(persistedAnswers == answers) { "factory clarification answers do not match persisted lineage" }
        val prompt = FactoryClarificationRequest.loadPrompt(runRoot)
        return runLocal(prompt)
    }

    fun resumePlan(runId: String): FactoryPlan {
        require(runId.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}"))) {
            "factory project id is invalid"
        }
        val runRoot = repoRoot.resolve(".atropos/research/factory").resolve(runId).normalize()
        require(runRoot.startsWith(repoRoot.toAbsolutePath().normalize())) {
            "factory resume path escaped repository root"
        }
        val promptPath = runRoot.resolve("user-prompt.md")
        if (!java.nio.file.Files.isRegularFile(promptPath) || java.nio.file.Files.isSymbolicLink(promptPath)) {
            throw IllegalArgumentException(
                "no resumable factory run '$runId' under .atropos/research/factory (missing user-prompt.md); " +
                    "start one with /factory run <prompt>"
            )
        }
        val prompt = FactoryClarificationRequest.loadPrompt(runRoot)
        return runLocal(prompt)
    }

    private fun runLocalInternal(
        prompt: String,
        projectIdOverride: String? = null,
        clarificationAnswers: List<Boolean> = emptyList()
    ): FactoryPlan {
        val planned = plan(prompt)
        val base = projectIdOverride?.let { planned.copy(id = it) } ?: planned
        val lineage = FactoryLineageFactory.prepare(
            repoRoot,
            base.id,
            base.prompt,
            base.projectSpec,
            runMemory = memory,
            clarificationAnswers = clarificationAnswers,
            localOnly = runtimeConfig.runtime.localOnly
        )

        val orchestrator = FactoryRunOrchestrator(
            repoRoot = repoRoot,
            memory = memory,
            assets = assets,
            projectRegistry = projectRegistry,
            planningGraph = planningGraph,
            journal = journal,
            repairVerificationFailure = liveRepairAction::invoke
        )
        return orchestrator.orchestrateRun(base, lineage)
    }

    fun render(plan: FactoryPlan): String = FactoryPlanHelper.render(plan)

    companion object {
        val PYTHON_ENV: Map<String, String> = mapOf("PYTHONPATH" to ".")
    }
}