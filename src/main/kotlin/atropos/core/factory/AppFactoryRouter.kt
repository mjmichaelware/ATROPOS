package atropos.core.factory

import atropos.core.assets.AssetKind
import atropos.core.assets.AssetRequest
import atropos.core.assets.LocalAssetGenerator
import atropos.core.AtroposRepoRootLocator
import atropos.core.execution.LocalWorkQueue
import atropos.core.memory.LocalMemoryStore
import atropos.core.memory.MemoryKind
import atropos.core.paid.EmergencyPaidGate
import atropos.core.project.ProjectRegistry
import atropos.core.project.RepositoryBinding
import atropos.core.planning.InternalPlanningGraphService
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
    val plannedAtomIds: List<String> = emptyList()
)

class AppFactoryRouter(
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val memory: LocalMemoryStore = LocalMemoryStore(),
    private val queue: LocalWorkQueue = LocalWorkQueue(),
    private val assets: LocalAssetGenerator = LocalAssetGenerator(),
    private val paidGate: EmergencyPaidGate = EmergencyPaidGate(),
    private val projectRegistry: ProjectRegistry = ProjectRegistry(repoRoot),
    private val projectSpecParser: AppProjectSpecParser = AppProjectSpecParser(),
    private val planningGraph: InternalPlanningGraphService = InternalPlanningGraphService(repoRoot)
) {
    fun plan(prompt: String): FactoryPlan {
        val clean = prompt.trim().ifBlank { "build local app" }
        val projectSpec = projectSpecParser.parse(clean)
        val intent = classify(clean)
        val steps = stepsFor(intent)
        return FactoryPlan(
            id = "factory-${Math.abs(clean.hashCode()).toString(16)}",
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
        val memoryRecord = memory.remember(
            kind = MemoryKind.DECISION,
            title = "factory ${base.intent}",
            body = base.prompt,
            tags = listOf("factory", base.intent)
        )
        val queued = mutableListOf<String>()
        val assetFiles = mutableListOf<String>()
        val planningDag = planningGraph.planFromTexts(
            projectId = base.id,
            label = base.projectSpec.intent.name,
            sources = mapOf("nl-prompt" to base.prompt)
        )
        val generatedProject = AppProjectGenerator(repoRoot).generateApp(
            base.projectSpec,
            base.id,
            planningDagId = planningDag.id,
            plannedAtomIds = planningDag.nodes.map { it.id }
        )
        val project = projectRegistry.register(
            name = base.projectSpec.intent.name,
            kind = base.projectSpec.intent.kind,
            binding = RepositoryBinding(
                repoRoot = generatedProject.path,
                branch = generatedProject.branch,
                baselineCommit = generatedProject.commitId,
                dirtyFingerprint = generatedProject.treeSha256.take(16)
            ),
            objective = base.prompt
        ).record

        if (base.steps.any { it.kind == FactoryStepKind.ASSET }) {
            val artifact = assets.generate(
                AssetRequest(
                    kind = AssetKind.SVG,
                    name = base.projectSpec.intent.name,
                    prompt = base.prompt,
                    tags = listOf("factory", "local", base.projectSpec.intent.kind)
                )
            )
            assetFiles += artifact.file.path
        }

        val compileJob = queue.enqueueLocalCompile()
        queued += compileJob.id

        return base.copy(
            queuedWork = queued,
            assetFiles = assetFiles,
            memoryRecordId = memoryRecord.id,
            projectRecordId = project.id,
            generatedProject = generatedProject,
            planningDagId = planningDag.id,
            plannedAtomIds = planningDag.nodes.map { it.id }
        )
    }

    fun render(plan: FactoryPlan): String {
        return buildString {
            appendLine("app factory plan:")
            appendLine("  id: ${plan.id}")
            appendLine("  intent: ${plan.intent}")
            appendLine("  paid allowed: ${plan.paidAllowed}")
            appendLine("  prompt: ${plan.prompt}")
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
        common += FactoryStep(FactoryStepKind.CI, "local_queue -> github_actions optional", true, "queued reproducibility lane")

        return common
    }
}
