package atropos.core.factory

import atropos.core.assets.AssetKind
import atropos.core.assets.AssetRequest
import atropos.core.assets.LocalAssetGenerator
import atropos.core.AtroposRepoRootLocator
import atropos.core.memory.LocalMemoryStore
import atropos.core.memory.MemoryAuthority
import atropos.core.memory.MemoryKind
import atropos.core.paid.EmergencyPaidGate
import atropos.core.project.ProjectRegistry
import atropos.core.project.ProjectStatus
import atropos.core.project.RepositoryBinding
import atropos.core.planning.InternalPlanningGraphService
import atropos.core.journal.EventCategory
import atropos.core.journal.EventJournalService
import atropos.core.provider.ContextEnvelopeFactory
import atropos.core.security.RedactionFilter
import java.util.Locale
import java.nio.file.Path

enum class FactoryStepKind {
    PLAN,
    RESEARCH,
    CODE,
    ATOMIZE,
    VALIDATE,
    REPAIR,
    ASSET,
    MEMORY,
    CI,
    EVIDENCE
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
    val softFailures: List<String> = emptyList(),
    val promptFingerprint: String? = null,
    val promptSha256: String? = null,
    val promptSpans: String? = null,
    val confidenceScore: Int? = null,
    val confidenceBreakdown: String? = null,
    val researchSha256: String? = null,
    val researchChannels: String? = null,
    val researchState: String? = null,
    val proposalSha256: String? = null,
    val memoryPointers: List<String> = emptyList(),
    val contextHash: String? = null,
    val specGraphStatus: String? = null,
    val eventJournalPath: String? = null
)

class AppFactoryRouter(
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val memory: LocalMemoryStore? = runCatching {
        LocalMemoryStore(repoRoot.resolve(".atropos/memory").toFile())
    }.getOrNull(),
    private val assets: LocalAssetGenerator = LocalAssetGenerator(repoRoot.resolve(".atropos/assets").toFile()),
    private val paidGate: EmergencyPaidGate = EmergencyPaidGate(),
    private val projectRegistry: ProjectRegistry = ProjectRegistry(repoRoot),
    private val projectSpecParser: AppProjectSpecParser = AppProjectSpecParser(),
    private val planningGraph: InternalPlanningGraphService = InternalPlanningGraphService(repoRoot),
    private val journal: EventJournalService = EventJournalService(repoRoot)
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

    fun runLocal(prompt: String): FactoryPlan = runLocalInternal(prompt)

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
        return runLocalInternal(FactoryClarificationRequest.loadPrompt(runRoot), projectId, answers)
    }

    private fun runLocalInternal(
        prompt: String,
        projectIdOverride: String? = null,
        clarificationAnswers: List<Boolean> = emptyList()
    ): FactoryPlan {
        val planned = plan(prompt)
        val base = projectIdOverride?.let { planned.copy(id = it) } ?: planned
        val lineage = FactoryLineage.prepare(
            repoRoot,
            base.id,
            base.prompt,
            base.projectSpec,
            runMemory = memory,
            clarificationAnswers = clarificationAnswers
        )
        recordFactoryEvent(
            runId = base.id,
            category = EventCategory.LIFECYCLE,
            payload = "factory_started intent=${base.intent} app=${base.projectSpec.intent.name}",
            promptFingerprint = lineage.promptFingerprint
        )
        recordFactoryEvent(
            runId = base.id,
            category = EventCategory.STATUS,
            payload = "factory_step kind=${FactoryStepKind.RESEARCH} state=${lineage.researchState} research_sha256=${lineage.researchSha256} channels=${lineage.researchChannels}",
            promptFingerprint = lineage.promptFingerprint
        )
        base.steps.forEach { step ->
            recordFactoryEvent(
                runId = base.id,
                category = EventCategory.STATUS,
                payload = "factory_step kind=${step.kind} state=PLANNED route=${step.route} local_first=${step.localFirst}",
                promptFingerprint = lineage.promptFingerprint
            )
        }
        val redactedPrompt = RedactionFilter().redact(base.prompt)
        val memoryRecord = memory?.let { memoryStore -> runCatching {
            memoryStore.remember(
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
        }.getOrNull() }
        recordFactoryEvent(
            runId = base.id,
            category = EventCategory.STATUS,
            payload = "factory_step kind=${FactoryStepKind.MEMORY} state=${if (memoryRecord != null) "COMPLETED" else "SKIPPED_SOFT_FAIL"}",
            promptFingerprint = lineage.promptFingerprint
        )
        val assetFiles = mutableListOf<String>()
        val softFailures = mutableListOf<String>()
        val planningDag = planningGraph.planFromTexts(
            projectId = base.id,
            label = base.projectSpec.intent.name,
            sources = mapOf(
                "nl-prompt" to "prompt_fingerprint=${lineage.promptFingerprint}\nprompt_sha256=${lineage.promptSha256}\nprompt_spans=${lineage.promptSpans}\n$redactedPrompt",
                "requirements" to lineage.researchDocument
            ),
            promptFingerprint = lineage.promptFingerprint,
            promptSpans = lineage.promptSpans
        )
        val plannedAtomIds = planningDag.nodes.map { it.id }
        recordFactoryEvent(
            runId = base.id,
            category = EventCategory.DAG,
            payload = "factory_plan dag=${planningDag.id} atoms=${plannedAtomIds.joinToString(",")}",
            dagId = planningDag.id,
            promptFingerprint = lineage.promptFingerprint
        )
        recordFactoryEvent(
            runId = base.id,
            category = EventCategory.STATUS,
            payload = "factory_step kind=${FactoryStepKind.PLAN} state=COMPLETED dag=${planningDag.id}",
            dagId = planningDag.id,
            promptFingerprint = lineage.promptFingerprint
        )
        val atomResearch = FactoryResearchService(memory = memory).researchOpenAtoms(
            atomIds = plannedAtomIds,
            promptFingerprint = lineage.promptFingerprint,
            promptSpans = lineage.promptSpans,
            researchDocumentSha256 = lineage.researchSha256
        )
        val atomMemory = memory?.let { memoryStore ->
            runCatching {
                memoryStore.rememberDetailed(
                    kind = MemoryKind.SOURCE,
                    title = "factory atomization artifact",
                    body = buildString {
                        appendLine("project_id=${base.id}")
                        appendLine("prompt_fingerprint=${lineage.promptFingerprint}")
                        appendLine("prompt_sha256=${lineage.promptSha256}")
                        appendLine("prompt_spans=${lineage.promptSpans}")
                        appendLine("planning_dag=${planningDag.id}")
                        plannedAtomIds.forEach { appendLine("atom=$it") }
                        atomResearch.forEach(::appendLine)
                    },
                    tags = listOf("factory", base.id, lineage.promptFingerprint, "atomization"),
                    subjectType = "factory-atoms",
                    subjectId = base.id,
                    sourceCoordinate = ".atropos/research/atoms.md",
                    authority = MemoryAuthority.SOURCE_REFERENCE
                )
            }.getOrNull()
        }
        val plannedLineage = lineage.withPlan(
            planningDag.id,
            plannedAtomIds,
            atomResearch,
            memoryPointers = listOfNotNull(atomMemory?.id?.let { "st:$it" })
        )
        recordFactoryEvent(
            runId = base.id,
            category = EventCategory.STATUS,
            payload = "factory_step kind=${FactoryStepKind.ATOMIZE} state=${plannedLineage.atomizationState()} specgraph=${plannedLineage.atomizerStatus} atoms=${plannedAtomIds.size}",
            dagId = planningDag.id,
            promptFingerprint = lineage.promptFingerprint
        )
        val plannedPath = AppProjectGenerator.targetPath(repoRoot, base.projectSpec, base.id)
        val plannedTerritory = repoRoot.toAbsolutePath().normalize()
            .relativize(plannedPath.toAbsolutePath().normalize())
            .toString()
        val plannedBranch = AppProjectGenerator.branchName(base.projectSpec, base.id)
        val context = ContextEnvelopeFactory.createForFactory(
            projectId = base.id,
            promptFingerprint = plannedLineage.promptFingerprint,
            promptSha256 = plannedLineage.promptSha256,
            researchSha256 = plannedLineage.researchSha256,
            atomIds = plannedAtomIds,
            territory = listOf(plannedTerritory),
            repoRoot = repoRoot,
            researchChannels = plannedLineage.researchChannels,
            promptSpans = plannedLineage.promptSpans,
            memoryPointers = plannedLineage.memoryPointers,
            branch = plannedBranch
        )
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
            recordFactoryEvent(
                runId = base.id,
                category = EventCategory.FILE_MUTATION,
                payload = "factory_mutation target=${plannedTerritory} branch=$plannedBranch",
                dagId = planningDag.id,
                promptFingerprint = lineage.promptFingerprint
            )
            AppProjectGenerator(repoRoot).generateApp(
                base.projectSpec,
                base.id,
                planningDagId = planningDag.id,
                plannedAtomIds = plannedAtomIds,
                lineage = plannedLineage.withContext(context.canonicalContextHash)
            )
        } catch (failure: Throwable) {
            recordFactoryEvent(
                runId = base.id,
                category = EventCategory.FAILURE,
                payload = "factory_generation_failed type=${failure.javaClass.simpleName}",
                dagId = planningDag.id,
                promptFingerprint = lineage.promptFingerprint
            )
            projectRegistry.setStatus(registration.record, ProjectStatus.FAILED, actor = "factory")
            throw failure
        }
        recordFactoryEvent(
            runId = base.id,
            category = EventCategory.VERIFICATION,
            payload = "factory_verified commit=${generatedProject.commitId} tree=${generatedProject.treeSha256}",
            dagId = planningDag.id,
            promptFingerprint = lineage.promptFingerprint
        )
        recordFactoryEvent(
            runId = base.id,
            category = EventCategory.TEST,
            payload = "factory_tests state=PASSED evidence=${generatedProject.evidencePath}",
            dagId = planningDag.id,
            promptFingerprint = lineage.promptFingerprint
        )
        recordFactoryEvent(
            runId = base.id,
            category = EventCategory.DIFF,
            payload = "factory_diff state=RECORDED files=${generatedProject.files.size} tree=${generatedProject.treeSha256}",
            dagId = planningDag.id,
            promptFingerprint = lineage.promptFingerprint
        )
        recordFactoryEvent(
            runId = base.id,
            category = EventCategory.STATUS,
            payload = "factory_step kind=${FactoryStepKind.CODE} state=COMPLETED project=${generatedProject.path}",
            dagId = planningDag.id,
            promptFingerprint = lineage.promptFingerprint
        )
        recordFactoryEvent(
            runId = base.id,
            category = EventCategory.STATUS,
            payload = "factory_step kind=${FactoryStepKind.VALIDATE} state=COMPLETED evidence=${generatedProject.evidencePath}",
            dagId = planningDag.id,
            promptFingerprint = lineage.promptFingerprint
        )
        recordFactoryEvent(
            runId = base.id,
            category = EventCategory.STATUS,
            payload = "factory_step kind=${FactoryStepKind.REPAIR} state=NOT_NEEDED verification=passed",
            dagId = planningDag.id,
            promptFingerprint = lineage.promptFingerprint
        )
        recordFactoryEvent(
            runId = base.id,
            category = EventCategory.TOOL_CALL,
            payload = "factory_artifact_ready export=${generatedProject.exportPath}",
            dagId = planningDag.id,
            promptFingerprint = lineage.promptFingerprint
        )
        recordFactoryEvent(
            runId = base.id,
            category = EventCategory.STATUS,
            payload = "factory_step kind=${FactoryStepKind.EVIDENCE} state=COMPLETED path=${generatedProject.evidencePath}",
            dagId = planningDag.id,
            promptFingerprint = lineage.promptFingerprint
        )
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
                recordFactoryEvent(
                    runId = base.id,
                    category = EventCategory.TOOL_CALL,
                    payload = "factory_asset_ready path=${artifact.file.path}",
                    dagId = planningDag.id,
                    promptFingerprint = lineage.promptFingerprint
                )
                recordFactoryEvent(
                    runId = base.id,
                    category = EventCategory.STATUS,
                    payload = "factory_step kind=${FactoryStepKind.ASSET} state=COMPLETED path=${artifact.file.path}",
                    dagId = planningDag.id,
                    promptFingerprint = lineage.promptFingerprint
                )
            }.onFailure { failure ->
                softFailures += "asset=SKIPPED_SOFT_FAIL:${failure.javaClass.simpleName.lowercase().replace(Regex("[^a-z0-9]+"), "_").take(80)}"
                recordFactoryEvent(
                    runId = base.id,
                    category = EventCategory.WARNING,
                    payload = "factory_asset_skipped type=${failure.javaClass.simpleName}",
                    dagId = planningDag.id,
                    promptFingerprint = lineage.promptFingerprint
                )
                recordFactoryEvent(
                    runId = base.id,
                    category = EventCategory.STATUS,
                    payload = "factory_step kind=${FactoryStepKind.ASSET} state=SKIPPED_SOFT_FAIL",
                    dagId = planningDag.id,
                    promptFingerprint = lineage.promptFingerprint
                )
            }
        }

        recordFactoryEvent(
            runId = base.id,
            category = EventCategory.STATUS,
            payload = "factory_step kind=${FactoryStepKind.CI} state=OPTIONAL_NOT_REQUESTED",
            dagId = planningDag.id,
            promptFingerprint = lineage.promptFingerprint
        )
        recordFactoryEvent(
            runId = base.id,
            category = EventCategory.STATUS,
            payload = "factory_deployment state=OPTIONAL_NOT_REQUESTED",
            dagId = planningDag.id,
            promptFingerprint = lineage.promptFingerprint
        )

        val project = try {
            projectRegistry.update(
                generatedRecord.copy(
                    status = ProjectStatus.COMPLETED,
                    evidenceIds = (generatedRecord.evidenceIds + generatedProject.evidencePath).distinct()
                ),
                event = "completed",
                actor = "factory",
                message = "factory completion evidence linked: ${generatedProject.evidencePath}"
            )
        } catch (failure: Throwable) {
            projectRegistry.setStatus(generatedRecord, ProjectStatus.FAILED, actor = "factory")
            recordFactoryEvent(
                runId = base.id,
                category = EventCategory.FAILURE,
                payload = "factory_completion_registration_failed type=${failure.javaClass.simpleName}",
                dagId = planningDag.id,
                promptFingerprint = lineage.promptFingerprint
            )
            throw failure
        }
        try {
            recordFactoryEvent(
                runId = base.id,
                category = EventCategory.COMPLETION,
                payload = "factory_completed project=${project.id} evidence=${generatedProject.evidencePath}",
                dagId = planningDag.id,
                promptFingerprint = lineage.promptFingerprint
            )
        } catch (failure: Throwable) {
            projectRegistry.setStatus(project, ProjectStatus.FAILED, actor = "factory")
            throw failure
        }

        return base.copy(
            queuedWork = emptyList(),
            assetFiles = assetFiles,
            memoryRecordId = memoryRecord?.id,
            projectRecordId = project.id,
            generatedProject = generatedProject,
            planningDagId = planningDag.id,
            plannedAtomIds = plannedAtomIds,
            softFailures = softFailures,
            promptFingerprint = lineage.promptFingerprint,
            promptSha256 = lineage.promptSha256,
            promptSpans = lineage.promptSpans,
            confidenceScore = plannedLineage.confidence.score,
            confidenceBreakdown = plannedLineage.confidence.breakdown,
            researchSha256 = plannedLineage.researchSha256,
            researchChannels = plannedLineage.researchChannels,
            researchState = plannedLineage.researchState,
            proposalSha256 = generatedProject.proposalSha256,
            memoryPointers = plannedLineage.memoryPointers,
            contextHash = context.canonicalContextHash,
            specGraphStatus = plannedLineage.atomizerStatus,
            eventJournalPath = ".atropos/runs/${base.id}/events.journal"
        )
    }

    private fun recordFactoryEvent(
        runId: String,
        category: EventCategory,
        payload: String,
        dagId: String? = null,
        promptFingerprint: String? = null
    ) {
        journal.record(
            runId = runId,
            category = category,
            payload = buildString {
                promptFingerprint?.takeIf { it.isNotBlank() }?.let { append("prompt_fingerprint=$it ") }
                append(payload)
            },
            projectId = runId,
            dagId = dagId
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

    private fun classify(prompt: String): String {
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

    private fun stepsFor(intent: String): List<FactoryStep> {
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
