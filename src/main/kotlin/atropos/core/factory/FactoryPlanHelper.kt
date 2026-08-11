package atropos.core.factory

import atropos.core.security.RedactionFilter
import java.util.Locale

object FactoryPlanHelper {
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
            plan.promptFingerprint?.let { appendLine("  prompt_fingerprint: $it") }
            plan.promptSha256?.let { appendLine("  prompt_sha256: $it") }
            plan.promptSpans?.let { appendLine("  prompt_spans: $it") }
            plan.confidenceScore?.let { appendLine("  confidence: $it") }
            plan.confidenceBreakdown?.let { appendLine("  confidence_breakdown: $it") }
            plan.researchSha256?.let { appendLine("  research_sha256: $it") }
            plan.researchChannels?.let {
                appendLine("  research_channels:")
                it.lineSequence().filter(String::isNotBlank).forEach { channel -> appendLine("    $channel") }
            }
            plan.researchState?.let { appendLine("  research_state: $it") }
            plan.proposalSha256?.let { appendLine("  proposal_sha256: $it") }
            if (plan.memoryPointers.isNotEmpty()) appendLine("  memory_pointers: ${plan.memoryPointers.joinToString(",")}")
            plan.contextHash?.let { appendLine("  context_hash: $it") }
            plan.specGraphStatus?.let { appendLine("  specgraph_status: $it") }
            plan.eventJournalPath?.let { appendLine("  event_journal: $it") }
            plan.generatedProject?.let {
                appendLine("  generated_project: ${it.path}")
                appendLine("  generated_commit: ${it.commitId}")
                appendLine("  generated_branch: ${it.branch}")
                appendLine("  generated_evidence: ${it.evidencePath}")
                appendLine("  generated_export: ${it.exportPath}")
                appendLine("  generated_journal: .atropos/runs/${plan.id}/events.journal")
            }
        }
    }

    fun classify(projectSpecParser: AppProjectSpecParser, prompt: String): String {
        val text = prompt.lowercase(Locale.US)
        val appRequest = projectSpecParser.isAppRequest(prompt)
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

    fun stepsFor(intent: String): List<FactoryStep> {
        val common = mutableListOf(
            FactoryStep(FactoryStepKind.PLAN, "local_classifier -> provider suggestions optional", true, "bounded local plan before optional provider proposals"),
            FactoryStep(FactoryStepKind.RESEARCH, "short_term -> long_term -> dLoI/lakehouse -> bounded fetch", true, "ordered, scoped research with soft-fail markers"),
            FactoryStep(FactoryStepKind.MEMORY, "local_memory", true, "record decision locally"),
            FactoryStep(FactoryStepKind.ATOMIZE, "SpecGraph detect -> internal DAG fallback", true, "prompt-linked requirements and atoms"),
            FactoryStep(FactoryStepKind.CODE, "local_template -> provider proposals optional -> queue", true, "local generation; providers propose only")
        )

        if (intent == "app_ui" || intent == "app_build") {
            common += FactoryStep(FactoryStepKind.ASSET, "local_svg -> huggingface/fal/replicate optional", true, "local asset generation never blocks code")
        }

        common += FactoryStep(FactoryStepKind.VALIDATE, "local_kotlinc", true, "local compile before remote CI")
        common += FactoryStep(FactoryStepKind.REPAIR, "local_stderr -> provider proposals optional -> queue", true, "stderr slicing before optional provider repair proposal")
        common += FactoryStep(
            FactoryStepKind.CI,
            "generated-evidence -> github_actions optional",
            true,
            "local generated verification is recorded; external CI remains optional"
        )
        common += FactoryStep(
            FactoryStepKind.EVIDENCE,
            "local_manifest -> repository_export",
            true,
            "lineage, hashes, audit, gate, commit, and export evidence"
        )

        return common
    }
}
