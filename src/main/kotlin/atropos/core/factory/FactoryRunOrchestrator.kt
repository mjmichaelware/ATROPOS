package atropos.core.factory

import atropos.core.assets.AssetKind
import atropos.core.assets.AssetRequest
import atropos.core.assets.LocalAssetGenerator
import atropos.core.memory.LocalMemoryStore
import atropos.core.memory.MemoryAuthority
import atropos.core.memory.MemoryKind
import atropos.core.project.ProjectRegistry
import atropos.core.project.ProjectStatus
import atropos.core.project.RepositoryBinding
import atropos.core.planning.InternalPlanningGraphService
import atropos.core.journal.EventCategory
import atropos.core.journal.EventJournalService
import atropos.core.provider.ContextEnvelopeFactory
import atropos.core.security.RedactionFilter
import java.nio.file.Path

class FactoryRunOrchestrator(
    private val repoRoot: Path,
    private val memory: LocalMemoryStore?,
    private val assets: LocalAssetGenerator,
    private val projectRegistry: ProjectRegistry,
    private val planningGraph: InternalPlanningGraphService,
    private val journal: EventJournalService
) {
    fun orchestrateRun(
        plan: FactoryPlan,
        lineage: FactoryLineage
    ): FactoryPlan {
        recordFactoryEvent(
            runId = plan.id,
            category = EventCategory.LIFECYCLE,
            payload = "factory_started intent=${plan.intent} app=${plan.projectSpec.intent.name}",
            promptFingerprint = lineage.promptFingerprint
        )
        recordFactoryEvent(
            runId = plan.id,
            category = EventCategory.STATUS,
            payload = "factory_step kind=${FactoryStepKind.RESEARCH} state=${lineage.researchState} research_sha256=${lineage.researchSha256} channels=${lineage.researchChannels}",
            promptFingerprint = lineage.promptFingerprint
        )
        plan.steps.forEach { step ->
            recordFactoryEvent(
                runId = plan.id,
                category = EventCategory.STATUS,
                payload = "factory_step kind=${step.kind} state=PLANNED route=${step.route} local_first=${step.localFirst}",
                promptFingerprint = lineage.promptFingerprint
            )
        }
        val redactedPrompt = RedactionFilter().redact(plan.prompt)
        val memoryRecord = memory?.let { memoryStore -> runCatching {
            memoryStore.remember(
                kind = MemoryKind.DECISION,
                title = "factory ${plan.intent}",
                body = buildString {
                    appendLine("project_id=${plan.id}")
                    appendLine("repository=${repoRoot.fileName}")
                    System.getenv("ATROPOS_OPERATOR_ID")?.takeIf { it.isNotBlank() }?.let { appendLine("operator_id=$it") }
                    appendLine("prompt_fingerprint=${lineage.promptFingerprint}")
                    appendLine("prompt_sha256=${lineage.promptSha256}")
                    append(redactedPrompt)
                },
                tags = buildList {
                    add("factory")
                    add(plan.intent)
                    add(plan.id)
                    add(lineage.promptFingerprint)
                    System.getenv("ATROPOS_OPERATOR_ID")?.takeIf { it.isNotBlank() }?.let { add("operator-$it") }
                }
            )
        }.getOrNull() }
        recordFactoryEvent(
            runId = plan.id,
            category = EventCategory.STATUS,
            payload = "factory_step kind=${FactoryStepKind.MEMORY} state=${if (memoryRecord != null) "COMPLETED" else "SKIPPED_SOFT_FAIL"}",
            promptFingerprint = lineage.promptFingerprint
        )
        val assetFiles = mutableListOf<String>()
        val softFailures = mutableListOf<String>()
        val planningDag = planningGraph.planFromTexts(
            projectId = plan.id,
            label = plan.projectSpec.intent.name,
            sources = mapOf(
                "nl-prompt" to "prompt_fingerprint=${lineage.promptFingerprint}\nprompt_sha256=${lineage.promptSha256}\nprompt_spans=${lineage.promptSpans}\n$redactedPrompt",
                "requirements" to lineage.researchDocument
            ),
            promptFingerprint = lineage.promptFingerprint,
            promptSpans = lineage.promptSpans
        )
        val plannedAtomIds = planningDag.nodes.map { it.id }
        recordFactoryEvent(
            runId = plan.id,
            category = EventCategory.DAG,
            payload = "factory_plan dag=${planningDag.id} atoms=${plannedAtomIds.joinToString(",")}",
            dagId = planningDag.id,
            promptFingerprint = lineage.promptFingerprint
        )
        recordFactoryEvent(
            runId = plan.id,
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
                        appendLine("project_id=${plan.id}")
                        appendLine("prompt_fingerprint=${lineage.promptFingerprint}")
                        appendLine("prompt_sha256=${lineage.promptSha256}")
                        appendLine("prompt_spans=${lineage.promptSpans}")
                        appendLine("planning_dag=${planningDag.id}")
                        plannedAtomIds.forEach { appendLine("atom=$it") }
                        atomResearch.forEach(::appendLine)
                    },
                    tags = listOf("factory", plan.id, lineage.promptFingerprint, "atomization"),
                    subjectType = "factory-atoms",
                    subjectId = plan.id,
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
            runId = plan.id,
            category = EventCategory.STATUS,
            payload = "factory_step kind=${FactoryStepKind.ATOMIZE} state=${plannedLineage.atomizationState()} specgraph=${plannedLineage.atomizerStatus} atoms=${plannedAtomIds.size}",
            dagId = planningDag.id,
            promptFingerprint = lineage.promptFingerprint
        )
        val plannedPath = AppProjectGenerator.targetPath(repoRoot, plan.projectSpec, plan.id)
        val plannedTerritory = repoRoot.toAbsolutePath().normalize()
            .relativize(plannedPath.toAbsolutePath().normalize())
            .toString()
        val plannedBranch = AppProjectGenerator.branchName(plan.projectSpec, plan.id)
        val context = ContextEnvelopeFactory.createForFactory(
            projectId = plan.id,
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
            name = plan.projectSpec.intent.name,
            kind = plan.projectSpec.intent.kind,
            binding = RepositoryBinding(
                repoRoot = plannedPath.toString(),
                branch = plannedBranch
            ),
            objective = redactedPrompt
        )
        val generatedProject = try {
            recordFactoryEvent(
                runId = plan.id,
                category = EventCategory.FILE_MUTATION,
                payload = "factory_mutation target=${plannedTerritory} branch=$plannedBranch",
                dagId = planningDag.id,
                promptFingerprint = lineage.promptFingerprint
            )
            AppProjectGenerator(repoRoot).generateApp(
                plan.projectSpec,
                plan.id,
                planningDagId = planningDag.id,
                plannedAtomIds = plannedAtomIds,
                lineage = plannedLineage.withContext(context.canonicalContextHash)
            )
        } catch (failure: Throwable) {
            recordFactoryEvent(
                runId = plan.id,
                category = EventCategory.FAILURE,
                payload = "factory_generation_failed type=${failure.javaClass.simpleName}",
                dagId = planningDag.id,
                promptFingerprint = lineage.promptFingerprint
            )
            projectRegistry.setStatus(registration.record, ProjectStatus.FAILED, actor = "factory")
            throw failure
        }
        recordFactoryEvent(
            runId = plan.id,
            category = EventCategory.VERIFICATION,
            payload = "factory_verified commit=${generatedProject.commitId} tree=${generatedProject.treeSha256}",
            dagId = planningDag.id,
            promptFingerprint = lineage.promptFingerprint
        )
        recordFactoryEvent(
            runId = plan.id,
            category = EventCategory.TEST,
            payload = "factory_tests state=PASSED evidence=${generatedProject.evidencePath}",
            dagId = planningDag.id,
            promptFingerprint = lineage.promptFingerprint
        )
        recordFactoryEvent(
            runId = plan.id,
            category = EventCategory.DIFF,
            payload = "factory_diff state=RECORDED files=${generatedProject.files.size} tree=${generatedProject.treeSha256}",
            dagId = planningDag.id,
            promptFingerprint = lineage.promptFingerprint
        )
        recordFactoryEvent(
            runId = plan.id,
            category = EventCategory.STATUS,
            payload = "factory_step kind=${FactoryStepKind.CODE} state=COMPLETED project=${generatedProject.path}",
            dagId = planningDag.id,
            promptFingerprint = lineage.promptFingerprint
        )
        recordFactoryEvent(
            runId = plan.id,
            category = EventCategory.STATUS,
            payload = "factory_step kind=${FactoryStepKind.VALIDATE} state=COMPLETED evidence=${generatedProject.evidencePath}",
            dagId = planningDag.id,
            promptFingerprint = lineage.promptFingerprint
        )
        recordFactoryEvent(
            runId = plan.id,
            category = EventCategory.STATUS,
            payload = "factory_step kind=${FactoryStepKind.REPAIR} state=NOT_NEEDED verification=passed",
            dagId = planningDag.id,
            promptFingerprint = lineage.promptFingerprint
        )
        recordFactoryEvent(
            runId = plan.id,
            category = EventCategory.TOOL_CALL,
            payload = "factory_artifact_ready export=${generatedProject.exportPath}",
            dagId = planningDag.id,
            promptFingerprint = lineage.promptFingerprint
        )
        recordFactoryEvent(
            runId = plan.id,
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

        if (plan.steps.any { it.kind == FactoryStepKind.ASSET }) {
            runCatching {
                assets.generate(
                    AssetRequest(
                        kind = AssetKind.SVG,
                        name = plan.projectSpec.intent.name,
                        prompt = redactedPrompt,
                        tags = listOf("factory", "local", plan.projectSpec.intent.kind)
                    )
                )
            }.onSuccess { artifact ->
                assetFiles += artifact.file.path
                recordFactoryEvent(
                    runId = plan.id,
                    category = EventCategory.TOOL_CALL,
                    payload = "factory_asset_ready path=${artifact.file.path}",
                    dagId = planningDag.id,
                    promptFingerprint = lineage.promptFingerprint
                )
                recordFactoryEvent(
                    runId = plan.id,
                    category = EventCategory.STATUS,
                    payload = "factory_step kind=${FactoryStepKind.ASSET} state=COMPLETED path=${artifact.file.path}",
                    dagId = planningDag.id,
                    promptFingerprint = lineage.promptFingerprint
                )
            }.onFailure { failure ->
                softFailures += "asset=SKIPPED_SOFT_FAIL:${failure.javaClass.simpleName.lowercase().replace(Regex("[^a-z0-9]+"), "_").take(80)}"
                recordFactoryEvent(
                    runId = plan.id,
                    category = EventCategory.WARNING,
                    payload = "factory_asset_skipped type=${failure.javaClass.simpleName}",
                    dagId = planningDag.id,
                    promptFingerprint = lineage.promptFingerprint
                )
                recordFactoryEvent(
                    runId = plan.id,
                    category = EventCategory.STATUS,
                    payload = "factory_step kind=${FactoryStepKind.ASSET} state=SKIPPED_SOFT_FAIL",
                    dagId = planningDag.id,
                    promptFingerprint = lineage.promptFingerprint
                )
            }
        }

        recordFactoryEvent(
            runId = plan.id,
            category = EventCategory.STATUS,
            payload = "factory_step kind=${FactoryStepKind.CI} state=OPTIONAL_NOT_REQUESTED",
            dagId = planningDag.id,
            promptFingerprint = lineage.promptFingerprint
        )
        recordFactoryEvent(
            runId = plan.id,
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
                runId = plan.id,
                category = EventCategory.FAILURE,
                payload = "factory_completion_registration_failed type=${failure.javaClass.simpleName}",
                dagId = planningDag.id,
                promptFingerprint = lineage.promptFingerprint
            )
            throw failure
        }
        try {
            recordFactoryEvent(
                runId = plan.id,
                category = EventCategory.COMPLETION,
                payload = "factory_completed project=${project.id} evidence=${generatedProject.evidencePath}",
                dagId = planningDag.id,
                promptFingerprint = lineage.promptFingerprint
            )
        } catch (failure: Throwable) {
            projectRegistry.setStatus(project, ProjectStatus.FAILED, actor = "factory")
            throw failure
        }

        return plan.copy(
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
            eventJournalPath = ".atropos/runs/${plan.id}/events.journal"
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
}
