package atropos.core.factory

import atropos.core.assets.AssetKind
import atropos.core.assets.AssetRequest
import atropos.core.assets.LocalAssetGenerator
import atropos.core.AtroposRepoRootLocator
import atropos.core.memory.LocalMemoryStore
import atropos.core.memory.MemoryKind
import atropos.core.paid.EmergencyPaidGate
import atropos.core.project.ProjectRegistry
import atropos.core.project.ProjectStatus
import atropos.core.project.RepositoryBinding
import atropos.core.planning.InternalPlanningGraphService
import atropos.core.provider.ContextEnvelopeFactory
import atropos.core.security.RedactionFilter
import java.util.Locale
import java.nio.file.Path

enum class FactoryStepKind {
    PLAN,
    CODE,
    VALIDATE,
    REPAIR,
    ASSET,
    MEMORY,
    CI
}

data class FactoryStep(
    val kind: FactoryStepKind,
    val route: String,
    val localFirst: Boolean,
    val description: String
)

data class FactoryPlan(
    val id: String,
    val prompt: String,
    val intent: String,
    val projectSpec: AppProjectSpec,
    val steps: List<FactoryStep>,
    val paidAllowed: Boolean,
    val queuedWork: List<String>,
    val assetFiles: List<String>,
    val memoryRecordId: String?,
    val projectRecordId: String? = null,
    val generatedProject: GeneratedAppProject? = null,
    val planningDagId: String? = null,
    val plannedAtomIds: List<String> = emptyList(),
    val softFailures: List<String> = emptyList()
)

class AppFactoryRouter(
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val memory: LocalMemoryStore = LocalMemoryStore(repoRoot.resolve(".atropos/memory").toFile()),
    private val assets: LocalAssetGenerator = LocalAssetGenerator(repoRoot.resolve(".atropos/assets").toFile()),
    private val paidGate: EmergencyPaidGate = EmergencyPaidGate(),
    private val projectRegistry: ProjectRegistry = ProjectRegistry(repoRoot),
    private val projectSpecParser: AppProjectSpecParser = AppProjectSpecParser(),
    private val appActions: AppActionRegistry = AppActionRegistry(),
    private val planningGraph: InternalPlanningGraphService = InternalPlanningGraphService(repoRoot)
) {
    fun plan(prompt: String): FactoryPlan {
        val clean = prompt.trim().ifBlank { "build local app" }
        val projectSpec = projectSpecParser.parse(clean)
        val intent = classify(clean)
        val steps = stepsFor(intent)
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

    fun runLocal(prompt: String): FactoryPlan {
        val base = plan(prompt)
        val lineage = FactoryLineage.prepare(repoRoot, base.id, base.prompt, base.projectSpec, runMemory = memory)
        val redactedPrompt = RedactionFilter().redact(base.prompt)
        val memoryRecord = runCatching {
            memory.remember(
                kind = MemoryKind.DECISION,
                title = "factory ${base.intent}",
                body = buildString {
                    appendLine("project_id=${base.id}")
                    appendLine("repository=${repoRoot.fileName}")
                    System.getenv("ATROPOS_OPERATOR_ID")?.takeIf { it.isNotBlank() }?.let { appendLine("operator_id=$it") }
                    appendLine("prompt_fingerprint=${lineage.promptFingerprint}")
                    appendLine("prompt_sha256=${lineage.promptSha256}")
                    append(redactedPrompt)
                },
                tags = buildList {
                    add("factory")
                    add(base.intent)
                    add(base.id)
                    add(lineage.promptFingerprint)
                    System.getenv("ATROPOS_OPERATOR_ID")?.takeIf { it.isNotBlank() }?.let { add("operator-$it") }
                }
            )
        }.getOrNull()
        val assetFiles = mutableListOf<String>()
        val softFailures = mutableListOf<String>()
        val planningDag = planningGraph.planFromTexts(
            projectId = base.id,
            label = base.projectSpec.intent.name,
            sources = mapOf(
                "nl-prompt" to "prompt_fingerprint=${lineage.promptFingerprint}\nprompt_sha256=${lineage.promptSha256}\nprompt_spans=${lineage.promptSpans}\n$redactedPrompt",
                "requirements" to lineage.researchDocument
            )
        )
        val plannedAtomIds = planningDag.nodes.map { it.id }
        val atomResearch = FactoryResearchService(memory = memory).researchOpenAtoms(
            atomIds = plannedAtomIds,
            promptFingerprint = lineage.promptFingerprint,
            promptSpans = lineage.promptSpans
        )
        val plannedLineage = lineage.withPlan(planningDag.id, plannedAtomIds, atomResearch)
        val context = ContextEnvelopeFactory.createForFactory(
            projectId = base.id,
            promptFingerprint = plannedLineage.promptFingerprint,
            researchSha256 = plannedLineage.researchSha256,
            atomIds = plannedAtomIds,
            territory = listOf(".atropos/generated-projects/${base.projectSpec.intent.name.replace(Regex("[^A-Za-z0-9._-]"), "_")}-${base.id}"),
            repoRoot = repoRoot,
            researchChannels = plannedLineage.researchChannels,
            promptSpans = plannedLineage.promptSpans
        )
        val plannedPath = AppProjectGenerator.targetPath(repoRoot, base.projectSpec, base.id)
        val plannedBranch = AppProjectGenerator.branchName(base.projectSpec, base.id)
        val registration = projectRegistry.register(
            name = base.projectSpec.intent.name,
            kind = base.projectSpec.intent.kind,
            binding = RepositoryBinding(
                repoRoot = plannedPath.toString(),
                branch = plannedBranch
            ),
            objective = redactedPrompt
        )
        val generatedProject = try {
            AppProjectGenerator(repoRoot).generateApp(
                base.projectSpec,
                base.id,
                planningDagId = planningDag.id,
                plannedAtomIds = plannedAtomIds,
                lineage = plannedLineage.withContext(context.canonicalContextHash)
            )
        } catch (failure: Throwable) {
            projectRegistry.setStatus(registration.record, ProjectStatus.FAILED, actor = "factory")
            throw failure
        }
        val generatedRecord = projectRegistry.update(
            registration.record.copy(
                binding = RepositoryBinding(
                    repoRoot = generatedProject.path,
                    branch = generatedProject.branch,
                    baselineCommit = generatedProject.commitId,
                    dirtyFingerprint = generatedProject.treeSha256.take(16)
                ),
                status = ProjectStatus.WORKING
            ),
            event = "generated",
            actor = "factory",
            message = "generated project verified; evidence=${generatedProject.evidencePath}"
        )

        if (base.steps.any { it.kind == FactoryStepKind.ASSET }) {
            runCatching {
                assets.generate(
                    AssetRequest(
                        kind = AssetKind.SVG,
                        name = base.projectSpec.intent.name,
                        prompt = redactedPrompt,
                        tags = listOf("factory", "local", base.projectSpec.intent.kind)
                    )
                )
            }.onSuccess { artifact ->
                assetFiles += artifact.file.path
            }.onFailure { failure ->
                softFailures += "asset=SKIPPED_SOFT_FAIL:${failure.javaClass.simpleName.lowercase().replace(Regex("[^a-z0-9]+"), "_").take(80)}"
            }
        }

        val project = projectRegistry.update(
            generatedRecord.copy(
                status = ProjectStatus.COMPLETED,
                evidenceIds = (generatedRecord.evidenceIds + generatedProject.evidencePath).distinct()
            ),
            event = "completed",
            actor = "factory",
            message = "factory completion evidence linked: ${generatedProject.evidencePath}"
        )

        return base.copy(
            queuedWork = emptyList(),
            assetFiles = assetFiles,
            memoryRecordId = memoryRecord?.id,
            projectRecordId = project.id,
            generatedProject = generatedProject,
            planningDagId = planningDag.id,
            plannedAtomIds = plannedAtomIds,
            softFailures = softFailures
        )
    }

    fun render(plan: FactoryPlan): String {
        return buildString {
            appendLine("app factory plan:")
            appendLine("  id: ${plan.id}")
            appendLine("  intent: ${plan.intent}")
            appendLine("  paid allowed: ${plan.paidAllowed}")
            appendLine("  prompt: ${RedactionFilter().redact(plan.prompt)}")
            appendLine("  app_name: ${plan.projectSpec.intent.name}")
            appendLine("  app_kind: ${plan.projectSpec.intent.kind}")
            appendLine("  app_features: ${plan.projectSpec.intent.features.joinToString(", ")}")
            plan.planningDagId?.let { appendLine("  planning_dag: $it") }
            if (plan.plannedAtomIds.isNotEmpty()) appendLine("  planning_atoms: ${plan.plannedAtomIds.joinToString(",")}")
            appendLine("  steps:")
            plan.steps.forEachIndexed { index, step ->
                appendLine("    ${index + 1}. ${step.kind.name.lowercase(Locale.US)} route=${step.route} local_first=${step.localFirst} - ${step.description}")
            }
            if (plan.memoryRecordId != null) appendLine("  memory: ${plan.memoryRecordId}")
            if (plan.queuedWork.isNotEmpty()) appendLine("  queued: ${plan.queuedWork.joinToString(",")}")
            if (plan.assetFiles.isNotEmpty()) appendLine("  assets: ${plan.assetFiles.joinToString(",")}")
            if (plan.softFailures.isNotEmpty()) appendLine("  soft_failures: ${plan.softFailures.joinToString("; ")}")
            plan.generatedProject?.let {
                appendLine("  generated_project: ${it.path}")
                appendLine("  generated_commit: ${it.commitId}")
                appendLine("  generated_branch: ${it.branch}")
                appendLine("  generated_evidence: ${it.evidencePath}")
                appendLine("  generated_export: ${it.exportPath}")
            }
        }
    }

    private fun classify(prompt: String): String {
        val text = prompt.lowercase(Locale.US)
        val tokens = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        val appRequest = appActions.isAppRequest(tokens)
        if (appRequest) {
            return when {
                text.contains("ui") || text.contains("screen") || text.contains("asset") || text.contains("image") -> "app_ui"
                text.contains("api") || text.contains("route") || text.contains("endpoint") -> "app_api"
                else -> "app_build"
            }
        }
        return when {
            text.contains("fix") || text.contains("error") || text.contains("compile") -> "repair"
            text.contains("ui") || text.contains("screen") || text.contains("asset") || text.contains("image") -> "app_ui"
            text.contains("api") || text.contains("route") || text.contains("endpoint") -> "app_api"
            text.contains("test") || text.contains("verify") -> "validation"
            else -> "app_build"
        }
    }

    private fun stepsFor(intent: String): List<FactoryStep> {
        val common = mutableListOf(
            FactoryStep(FactoryStepKind.PLAN, "local_classifier -> gemini/groq optional", true, "bounded local plan before provider use"),
            FactoryStep(FactoryStepKind.MEMORY, "local_memory", true, "record decision locally"),
            FactoryStep(FactoryStepKind.CODE, "groq -> openrouter -> github_models -> queue", true, "free-first code route")
        )

        if (intent == "app_ui" || intent == "app_build") {
            common += FactoryStep(FactoryStepKind.ASSET, "local_svg -> huggingface/fal/replicate optional", true, "local asset generation never blocks code")
        }

        common += FactoryStep(FactoryStepKind.VALIDATE, "local_kotlinc", true, "local compile before remote CI")
        common += FactoryStep(FactoryStepKind.REPAIR, "local_stderr -> groq -> openrouter -> queue", true, "stderr slicing before LLM repair")
        common += FactoryStep(
            FactoryStepKind.CI,
            "generated-evidence -> github_actions optional",
            true,
            "local generated verification is recorded; external CI remains optional"
        )

        return common
    }
}
