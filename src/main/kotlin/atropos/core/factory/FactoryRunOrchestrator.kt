package atropos.core.factory

import atropos.core.thinking.Narrate
import atropos.core.dag.DagStore
import atropos.core.assets.LocalAssetGenerator
import atropos.core.memory.LocalMemoryStore
import atropos.core.memory.MemoryAuthority
import atropos.core.memory.MemoryKind
import atropos.core.project.ProjectRegistry
import atropos.core.project.ProjectStatus
import atropos.core.project.RepositoryBinding
import atropos.core.planning.InternalPlanningGraphService
import atropos.core.journal.EventJournalService
import atropos.core.provider.ContextEnvelopeFactory
import atropos.core.security.RedactionFilter
import atropos.core.preview.LivePreviewService
import atropos.core.multimodal.BrowserEvidenceStatus
import java.time.Duration
import java.time.Instant
import java.nio.file.Path

class FactoryRunOrchestrator(
    private val repoRoot: Path,
    private val memory: LocalMemoryStore?,
    private val assets: LocalAssetGenerator,
    private val projectRegistry: ProjectRegistry,
    private val planningGraph: InternalPlanningGraphService,
    private val journal: EventJournalService,
    private val deploymentService: DeploymentService = DeploymentService(),
    private val previewService: LivePreviewService = LivePreviewService(repoRoot),
    /** Optional real repair action; absent means verification failure stays failed. */
    private val repairVerificationFailure: ((FactoryPlan, Path, Throwable, FactoryAcceptanceFreeze) -> FactoryAcceptanceFreeze.RepairEvidence)? = null
) {
    fun orchestrateRun(
        plan: FactoryPlan,
        lineage: FactoryLineage
    ): FactoryPlan {
        val startedAt = Instant.now()
        val recorder = FactoryRunEventRecorder(journal)

        recorder.recordLifecycleStart(
            runId = plan.id,
            intent = plan.intent,
            appName = plan.projectSpec.intent.name,
            promptFingerprint = lineage.promptFingerprint
        )
        recorder.recordResearchStatus(
            runId = plan.id,
            state = lineage.researchState,
            researchSha256 = lineage.researchSha256,
            channels = lineage.researchChannels,
            promptFingerprint = lineage.promptFingerprint
        )
        plan.steps.forEach { step ->
            recorder.recordPlannedStep(
                runId = plan.id,
                kind = step.kind,
                state = "PLANNED",
                route = step.route,
                localFirst = step.localFirst,
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
        recorder.recordMemoryStep(
            runId = plan.id,
            state = if (memoryRecord != null) "COMPLETED" else "SKIPPED_SOFT_FAIL",
            promptFingerprint = lineage.promptFingerprint
        )
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
        // The two numbers an operator waits through a long plan for. Both were
        // computed here and reported only into the run journal, which is not
        // something anyone reads while the run is still going.
        Narrate.plan.headlineCount("nodes planned", planningDag.nodes.size)
        Narrate.plan.counted(
            "edges between them",
            planningDag.nodes.sumOf { it.dependencies.size }
        )
        planningDag.nodes.forEachIndexed { index, node ->
            Narrate.plan.item(index + 1, planningDag.nodes.size, node.id, node.label)
        }
        recorder.recordPlanDag(
            runId = plan.id,
            dagId = planningDag.id,
            atomIds = plannedAtomIds.joinToString(","),
            promptFingerprint = lineage.promptFingerprint
        )
        recorder.recordPlanCompletion(
            runId = plan.id,
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
        val frozenLineage = plannedLineage.withAcceptanceFreeze(plannedAtomIds)
        val acceptanceFreeze = requireNotNull(frozenLineage.acceptanceFreeze)
        persistResumeArtifacts(plan, acceptanceFreeze)
        val obligationLoop = FactoryObligationLoop(DagStore(repoRoot))
        val initialObligations = obligationLoop.beforeMutation(planningDag)
        recorder.recordAcceptanceFreeze(
            runId = plan.id,
            freezeSha256 = acceptanceFreeze.sha256,
            openWork = initialObligations.openWork,
            canaryAtomIds = initialObligations.runnableAtomIds,
            dagId = planningDag.id,
            promptFingerprint = lineage.promptFingerprint
        )
        recorder.recordObligationLoop(
            runId = plan.id,
            snapshot = initialObligations,
            dagId = planningDag.id,
            promptFingerprint = lineage.promptFingerprint
        )
        FactoryRunHandoff.write(
            repoRoot = repoRoot,
            runId = plan.id,
            dagId = planningDag.id,
            snapshot = initialObligations,
            freeze = acceptanceFreeze
        )
        recorder.recordAtomizationStatus(
            runId = plan.id,
            state = frozenLineage.atomizationState(),
            specgraph = frozenLineage.atomizerStatus,
            atomCount = plannedAtomIds.size,
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
            branch = plannedBranch,
            acceptanceFreezeSha256 = acceptanceFreeze.sha256,
            openAtomCount = initialObligations.openWork,
            nonGoals = listOf("host repository mutation", "provider prose execution")
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
        var repairWasExecuted = false
        val generatedProject = try {
            recorder.recordFileMutation(
                runId = plan.id,
                territory = plannedTerritory,
                branch = plannedBranch,
                dagId = planningDag.id,
                promptFingerprint = lineage.promptFingerprint
            )
            AppProjectGenerator(repoRoot).generateApp(
                plan.projectSpec,
                plan.id,
                planningDagId = planningDag.id,
                plannedAtomIds = plannedAtomIds,
                lineage = frozenLineage.withContext(context.canonicalContextHash)
            )
        } catch (failure: Throwable) {
            initialObligations.runnableAtomIds.firstOrNull()?.let { atomId ->
                val failureSnapshot = obligationLoop.recordFailure(
                    dagId = planningDag.id,
                    atomId = atomId,
                    failure = failure.message ?: failure.javaClass.name
                )
                recorder.recordObligationLoop(
                    runId = plan.id,
                    snapshot = failureSnapshot,
                    dagId = planningDag.id,
                    promptFingerprint = lineage.promptFingerprint
                )
            }
            recorder.recordGenerationFailure(
                runId = plan.id,
                failureType = failure.javaClass.simpleName,
                dagId = planningDag.id,
                promptFingerprint = lineage.promptFingerprint
            )
            val repairAction = repairVerificationFailure ?: run {
                projectRegistry.setStatus(registration.record, ProjectStatus.FAILED, actor = "factory")
                throw failure
            }
            var suppliedRepairEvidence: FactoryAcceptanceFreeze.RepairEvidence? = null
            val repairedProject = runCatching {
                // An injected repair action may mutate only through the
                // caller's existing bounded path; its evidence is checked
                // by FactoryAcceptanceFreeze below. Without this authority
                // the run failed closed above; no repair success is inferred.
                repairWasExecuted = true
                suppliedRepairEvidence = repairAction(plan, plannedPath, failure, acceptanceFreeze)
                AppProjectGenerator(repoRoot).generateApp(
                    plan.projectSpec,
                    plan.id,
                    planningDagId = planningDag.id,
                    plannedAtomIds = plannedAtomIds,
                    lineage = frozenLineage.withContext(context.canonicalContextHash)
                )
            }.getOrElse { repairFailure ->
                failure.addSuppressed(repairFailure)
                projectRegistry.setStatus(registration.record, ProjectStatus.FAILED, actor = "factory")
                throw failure
            }
            val repairEvidence = requireNotNull(suppliedRepairEvidence) {
                "repair callback returned no acceptance evidence"
            }
            val handoff = FactoryRunHandoff.read(repoRoot, plan.id)
            val repairResult = FactoryRepairExecutor(obligationLoop).repairAndResume(
                handoff = handoff,
                freeze = acceptanceFreeze,
                repair = { repairEvidence },
                executeWave = FactoryEvidenceWaveExecutor(
                    java.nio.file.Path.of(repairedProject.evidencePath),
                    acceptanceFreeze
                )::execute
            )
            recorder.recordRepair(
                runId = plan.id,
                state = "REENTERED_OBLIGATION_LOOP",
                verification = repairResult.evidence,
                dagId = planningDag.id,
                promptFingerprint = lineage.promptFingerprint
            )
            repairedProject
        }
        recorder.recordVerification(
            runId = plan.id,
            commitId = generatedProject.commitId,
            treeSha256 = generatedProject.treeSha256,
            dagId = planningDag.id,
            promptFingerprint = lineage.promptFingerprint
        )
        recorder.recordTests(
            runId = plan.id,
            state = "PASSED",
            evidencePath = generatedProject.evidencePath,
            dagId = planningDag.id,
            promptFingerprint = lineage.promptFingerprint
        )
        recorder.recordDiff(
            runId = plan.id,
            state = "RECORDED",
            fileCount = generatedProject.files.size,
            treeSha256 = generatedProject.treeSha256,
            dagId = planningDag.id,
            promptFingerprint = lineage.promptFingerprint
        )
        val previewFiles = generatedProject.files.filter {
            it.endsWith(".html", ignoreCase = true) ||
                it.endsWith(".tsx", ignoreCase = true) ||
                it.endsWith(".jsx", ignoreCase = true)
        }
        val previewImpacts = previewService.inspectUI(previewFiles)
        val browserEvidence = previewFiles.firstOrNull { it.endsWith(".html", ignoreCase = true) }
            ?.let { relative ->
                val html = java.nio.file.Files.readString(java.nio.file.Path.of(generatedProject.path).resolve(relative))
                previewService.captureStaticHtml("factory-${plan.id}", html)
            }
        recorder.recordPreview(
            runId = plan.id,
            state = when {
                previewFiles.isEmpty() -> "SKIPPED_NO_RENDERABLE_SURFACE"
                browserEvidence?.status == BrowserEvidenceStatus.CAPTURED -> "STATIC_CAPTURED_SOFT"
                else -> "SKIPPED_SOFT_BROWSER_UNAVAILABLE"
            },
            impactedSymbols = previewImpacts.size,
            browserStatus = browserEvidence?.status?.name ?: BrowserEvidenceStatus.UNSUPPORTED.name,
            dagId = planningDag.id,
            promptFingerprint = lineage.promptFingerprint
        )
        recorder.recordCodeCompletion(
            runId = plan.id,
            projectPath = generatedProject.path,
            dagId = planningDag.id,
            promptFingerprint = lineage.promptFingerprint
        )
        recorder.recordValidation(
            runId = plan.id,
            evidencePath = generatedProject.evidencePath,
            dagId = planningDag.id,
            promptFingerprint = lineage.promptFingerprint
        )
        recorder.recordRepair(
            runId = plan.id,
            state = if (repairWasExecuted) "REENTERED_OBLIGATION_LOOP" else "NOT_NEEDED",
            verification = if (repairWasExecuted) "repair_evidence_recorded" else "passed",
            dagId = planningDag.id,
            promptFingerprint = lineage.promptFingerprint
        )
        recorder.recordArtifactReady(
            runId = plan.id,
            exportPath = generatedProject.exportPath,
            dagId = planningDag.id,
            promptFingerprint = lineage.promptFingerprint
        )
        recorder.recordEvidenceCompletion(
            runId = plan.id,
            evidencePath = generatedProject.evidencePath,
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

        val assetHandler = FactoryAssetHandler(assets, recorder)
        val (assetFiles, assetSoftFailures) = assetHandler.generateAssets(
            planSteps = plan.steps,
            projectName = plan.projectSpec.intent.name,
            redactedPrompt = redactedPrompt,
            projectTags = listOf(plan.projectSpec.intent.kind),
            runId = plan.id,
            dagId = planningDag.id,
            promptFingerprint = lineage.promptFingerprint
        )
        softFailures += assetSoftFailures

        recorder.recordCiStep(
            runId = plan.id,
            state = "OPTIONAL_NOT_REQUESTED",
            dagId = planningDag.id,
            promptFingerprint = lineage.promptFingerprint
        )
        val previewDeployment = deploymentService.deploy(
            env = DeploymentEnvironment.PREVIEW,
            domain = "local://${generatedProject.path}",
            gitCommitHash = generatedProject.commitId
        )
        recorder.recordDeployment(
            runId = plan.id,
            state = "PREVIEW_REGISTERED id=${previewDeployment.id}",
            dagId = planningDag.id,
            promptFingerprint = lineage.promptFingerprint
        )

        val loopResult = try {
            obligationLoop.executeUntilSettled(
                planningDag.id,
                acceptanceFreeze,
                FactoryEvidenceWaveExecutor(
                    java.nio.file.Path.of(generatedProject.evidencePath),
                    acceptanceFreeze
                )::execute
            )
        } catch (failure: Throwable) {
            projectRegistry.setStatus(generatedRecord, ProjectStatus.FAILED, actor = "factory")
            recorder.recordCompletionFailure(
                runId = plan.id,
                failureType = failure.javaClass.simpleName,
                dagId = planningDag.id,
                promptFingerprint = lineage.promptFingerprint
            )
            throw failure
        }
        val finalObligations = loopResult.snapshot
        recorder.recordObligationLoop(
            runId = plan.id,
            snapshot = finalObligations,
            dagId = planningDag.id,
            promptFingerprint = lineage.promptFingerprint
        )
        check(finalObligations.canComplete) {
            "factory completion refused: open_work=${finalObligations.openWork} " +
                "blocked=${finalObligations.blockedAtomIds} failed=${finalObligations.failedAtomIds}"
        }
        check(loopResult.terminationReason == "open_work=0" && finalObligations.canComplete) {
            "factory completion refused: termination=${loopResult.terminationReason} open_work=${finalObligations.openWork} " +
                "blocked=${finalObligations.blockedAtomIds} failed=${finalObligations.failedAtomIds}"
        }
        val terminationReason = "open_work=0 waves=${loopResult.wavesExecuted} acceptance_freeze_green completion_gate_green evidence_complete"
        val economics = FactoryRunEconomics(
            wallTimeMillis = Duration.between(startedAt, Instant.now()).toMillis(),
            providerCalls = null,
            tokens = null,
            atomsDone = finalObligations.doneAtomIds.size,
            atomsFailed = finalObligations.failedAtomIds.size,
            atomsBlocked = finalObligations.blockedAtomIds.size,
            softSkips = softFailures.size,
            gateDecision = "PASS",
            terminationReason = terminationReason
        )
        recorder.recordEconomics(
            runId = plan.id,
            economics = economics,
            dagId = planningDag.id,
            promptFingerprint = lineage.promptFingerprint
        )
        FactoryRunHandoff.write(
            repoRoot = repoRoot,
            runId = plan.id,
            dagId = planningDag.id,
            snapshot = finalObligations,
            freeze = acceptanceFreeze,
            lastGoodCommit = generatedProject.commitId
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
            recorder.recordCompletionFailure(
                runId = plan.id,
                failureType = failure.javaClass.simpleName,
                dagId = planningDag.id,
                promptFingerprint = lineage.promptFingerprint
            )
            throw failure
        }
        try {
            recorder.recordCompletion(
                runId = plan.id,
                projectId = project.id,
                evidencePath = generatedProject.evidencePath,
                dagId = planningDag.id,
                promptFingerprint = lineage.promptFingerprint
            )
        } catch (failure: Throwable) {
            projectRegistry.setStatus(project, ProjectStatus.FAILED, actor = "factory")
            throw failure
        }

        val resultBuilder = FactoryResultBuilder()
        return resultBuilder.buildResult(
            originalPlan = plan,
            lineage = frozenLineage,
            generatedProject = generatedProject,
            planningDagId = planningDag.id,
            plannedAtomIds = plannedAtomIds,
            assetFiles = assetFiles,
            softFailures = softFailures,
            memoryRecordId = memoryRecord?.id,
            projectRecordId = project.id,
            contextHash = context.canonicalContextHash,
            acceptanceFreezeSha256 = acceptanceFreeze.sha256,
            economics = economics,
            terminationReason = terminationReason
        )
    }

    private fun persistResumeArtifacts(plan: FactoryPlan, freeze: FactoryAcceptanceFreeze) {
        val runRoot = repoRoot.toAbsolutePath().normalize()
            .resolve(".atropos/research/factory").resolve(plan.id).normalize()
        require(runRoot.startsWith(repoRoot.toAbsolutePath().normalize())) {
            "factory resume artifact path escaped repository"
        }
        java.nio.file.Files.createDirectories(runRoot)
        java.nio.file.Files.writeString(runRoot.resolve("plan.md"), FactoryPlanHelper.render(plan))
        java.nio.file.Files.writeString(runRoot.resolve("acceptance-freeze.md"), freeze.document)
    }
}
