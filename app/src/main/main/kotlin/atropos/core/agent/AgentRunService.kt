package atropos.core.agent

import atropos.core.AtroposConfig
import atropos.ast.AstSymbolGraph
import atropos.core.memory.LocalMemoryStore
import atropos.core.security.RedactionFilter
import atropos.dloi.DloiService
import atropos.dloi.HigZeroGuard
import java.time.Instant

class AgentRunService(
    private val config: AtroposConfig = AtroposConfig.load(),
    private val collector: AgentContextCollector = AgentContextCollector(),
    private val agentService: AgentService = AgentService(config, collector),
    private val patchStore: AgentPatchStore = AgentPatchStore(collector.repoRoot),
    private val jobStore: AgentJobStore = AgentJobStore(collector.repoRoot),
    private val smokeRunner: AgentSmokeRunner = AgentSmokeRunner(collector.repoRoot),
    private val contextExporter: AgentContextExportStore = AgentContextExportStore(collector.repoRoot),
    private val memoryStore: LocalMemoryStore = LocalMemoryStore(collector.repoRoot.resolve(".atropos/memory").toFile()),
    private val dloiService: DloiService = DloiService(collector.repoRoot),
    /** The only way this service reaches DLOI: failures arrive typed, not thrown. */
    private val higZeroGuard: HigZeroGuard = HigZeroGuard(dloiService),
    private val astSymbolGraph: AstSymbolGraph = AstSymbolGraph(collector.repoRoot),
    private val redactionFilter: RedactionFilter = RedactionFilter(),
    private val reporter: AgentRunReporter = AgentRunReporter(redactionFilter),
    private val repoStatus: AgentRunRepoStatus = AgentRunRepoStatus(collector.repoRoot),
    private val sourceResolver: AgentRunSourceResolver = AgentRunSourceResolver(collector.repoRoot, higZeroGuard, astSymbolGraph, memoryStore),
    private val outcomeMemory: AgentRunOutcomeMemory = AgentRunOutcomeMemory(memoryStore),
    private val localPatchSynthesizer: AgentLocalPatchSynthesizer = AgentLocalPatchSynthesizer(patchStore)
) {
    private val prompts = AgentRunPromptComposer()
    private val lifecycle = AgentJobLifecycle(jobStore)
    private val failureSummary = AgentFailureSummary(redactionFilter)

    fun run(
        activeProviderName: String,
        task: String,
        smokeCommand: String? = null,
        hooks: AgentRunHooks = AgentRunHooks.NONE
    ): AgentJobRecord {
        val smokeRequested = smokeCommand?.trim()?.takeIf { it.isNotBlank() }
        if (smokeRequested != null) {
            val smokeRefusal = smokeRunner.validate(smokeRequested)
            if (smokeRefusal != null) {
                return refuseUnsafeSmoke(task, smokeRequested, smokeRefusal)
            }
        }
        hooks.checkpoint(AgentQueueCheckpoint.PREFLIGHT_PASSED, null, "smoke preflight passed")

        val startedAt = Instant.now()
        val baselineStatus = repoStatus.capture()
        val sourceEvidence = sourceResolver.resolveSourceEvidence(task)
        var job = jobStore.createJob(task = task, provider = activeProviderName)
        job = lifecycle.persist(job.copy(status = AgentJobStatus.PLANNING, updatedAt = startedAt))
        hooks.checkpoint(AgentQueueCheckpoint.CLAIMED, job, "job record created")

        try {
            hooks.beforeStage(AgentQueueCheckpoint.PLANNED, job)
            val planPrompt = prompts.planPrompt(task)
            val planResult = agentService.ask(activeProviderName, planPrompt)
            val planAt = Instant.now()
            job = lifecycle.persist(
                job.copy(
                    provider = planResult.providerName,
                    status = AgentJobStatus.PATCHING,
                    planAt = planAt,
                    updatedAt = planAt,
                    plan = planResult.render(),
                    result = "plan captured from ${planResult.providerName}"
                )
            )
            hooks.checkpoint(AgentQueueCheckpoint.PLANNED, job, "planning completed")

            hooks.beforeStage(AgentQueueCheckpoint.PATCH_GENERATED, job)
            val patchTask = prompts.patchTask(task, planResult.answerText)
            val providerPatchResult = agentService.patch(activeProviderName, patchTask)
            val patchResult = providerPatchResult.takeIf { it.patchId != null && it.checkResult?.passed != false }
                ?: localPatchSynthesizer.synthesize(task)
                ?: providerPatchResult
            val patchAt = Instant.now()
            job = lifecycle.persist(
                job.copy(
                    provider = patchResult.providerName,
                    status = AgentJobStatus.APPLYING,
                    patchId = patchResult.patchId,
                    patchAt = patchAt,
                    updatedAt = patchAt,
                    patchResult = patchResult.render()
                )
            )
            hooks.checkpoint(AgentQueueCheckpoint.PATCH_GENERATED, job, "patch generated")

            val patchId = patchResult.patchId
            if (patchId.isNullOrBlank()) {
                val failureReason = patchResult.failureSummary
                    ?: patchResult.rejectionReason
                    ?: patchResult.message
                    ?: "patch generation failed"
                job = lifecycle.fail(job, failureReason, "patch generation failed before apply")
                return finalizeRun(job, task, smokeCommand, null, baselineStatus, sourceEvidence)
            }

            hooks.beforeStage(AgentQueueCheckpoint.PATCH_APPLIED, job)
            val applyResult = agentService.applyPatch(patchId, checkOnly = false, verifyAfterApply = true)
            val applyAt = Instant.now()
            val initialVerificationId = applyResult.verificationResult?.verificationId
            job = lifecycle.persist(
                job.copy(
                    provider = patchResult.providerName,
                    patchId = patchId,
                    appliedPatchId = patchId,
                    verificationId = initialVerificationId,
                    status = if (applyResult.applied && applyResult.verificationResult?.passed == true) {
                        AgentJobStatus.COMPLETED
                    } else {
                        AgentJobStatus.REPAIRING
                    },
                    applyAt = applyAt,
                    verificationAt = if (initialVerificationId != null) applyAt else job.verificationAt,
                    updatedAt = applyAt,
                    applyResult = applyResult.render(),
                    result = if (applyResult.applied && applyResult.verificationResult?.passed == true) {
                        prompts.successResult(patchId, initialVerificationId, null)
                    } else {
                        "initial apply or verification failed"
                    }
                )
            )
            if (applyResult.applied) {
                hooks.checkpoint(AgentQueueCheckpoint.PATCH_APPLIED, job, "patch applied")
            }
            if (applyResult.verificationResult?.passed == true) {
                hooks.checkpoint(AgentQueueCheckpoint.VERIFIED, job, "verification passed")
            }

            if (applyResult.applied && applyResult.verificationResult?.passed == true) {
                job = lifecycle.complete(job)
            }

            if (job.status != AgentJobStatus.COMPLETED) {
                val applyFailure = applyResult.verificationResult?.refusalReason
                    ?: applyResult.refusalReason
                    ?: applyResult.checkResult?.output
                    ?: applyResult.applyOutput
                    ?: "verification failed"

                hooks.beforeStage(AgentQueueCheckpoint.REPAIR_GENERATED, job)
                val repairResult = agentService.repair(activeProviderName, patchId)
                val repairAt = Instant.now()
                job = lifecycle.persist(
                    job.copy(
                        provider = repairResult.providerName,
                        status = AgentJobStatus.REPAIRING,
                        repairId = repairResult.patchId,
                        repairAt = repairAt,
                        updatedAt = repairAt,
                        repairResult = repairResult.render()
                    )
                )
                hooks.checkpoint(AgentQueueCheckpoint.REPAIR_GENERATED, job, "repair generated")

                val repairPatchId = repairResult.patchId
                if (repairPatchId.isNullOrBlank()) {
                    val failureReason = repairResult.failureSummary
                        ?: repairResult.rejectionReason
                        ?: repairResult.message
                        ?: applyFailure
                    job = lifecycle.fail(job, failureReason, "repair generation failed after verification failure")
                    return finalizeRun(job, task, smokeCommand, null, baselineStatus, sourceEvidence)
                }

                hooks.beforeStage(AgentQueueCheckpoint.REPAIR_APPLIED, job)
                val repairedApply = agentService.applyPatch(repairPatchId, checkOnly = false, verifyAfterApply = true)
                val repairedAt = Instant.now()
                val repairedVerificationId = repairedApply.verificationResult?.verificationId
                job = lifecycle.persist(
                    job.copy(
                        provider = repairResult.providerName,
                        appliedPatchId = repairPatchId,
                        verificationId = repairedVerificationId,
                        status = if (repairedApply.applied && repairedApply.verificationResult?.passed == true) {
                            AgentJobStatus.COMPLETED
                        } else {
                            AgentJobStatus.FAILED
                        },
                        applyAt = repairedAt,
                        verificationAt = if (repairedVerificationId != null) repairedAt else job.verificationAt,
                        finishedAt = if (repairedApply.applied && repairedApply.verificationResult?.passed == true) repairedAt else null,
                        updatedAt = repairedAt,
                        applyResult = repairedApply.render(),
                        result = if (repairedApply.applied && repairedApply.verificationResult?.passed == true) {
                            prompts.successResult(patchId, repairedVerificationId, repairPatchId)
                        } else {
                            "repair apply or verification failed"
                        }
                    )
                )
                if (repairedApply.applied) {
                    hooks.checkpoint(AgentQueueCheckpoint.REPAIR_APPLIED, job, "repair patch applied")
                }
                if (repairedApply.verificationResult?.passed == true) {
                    hooks.checkpoint(AgentQueueCheckpoint.REVERIFIED, job, "repair verification passed")
                }

                if (job.status != AgentJobStatus.COMPLETED) {
                    val failureReason = repairedApply.verificationResult?.refusalReason
                        ?: repairedApply.refusalReason
                        ?: repairedApply.checkResult?.output
                        ?: repairedApply.applyOutput
                        ?: "verification failed after repair"
                    job = lifecycle.fail(job, failureReason, "repair patch did not verify")
                }
            }

            val smokeExecution = if (smokeCommand.isNullOrBlank()) {
                null
            } else if (job.status == AgentJobStatus.COMPLETED) {
                hooks.beforeStage(AgentQueueCheckpoint.SMOKE_PASSED, job)
                smokeRunner.run(smokeCommand)
            } else {
                null
            }

            if (smokeExecution != null) {
                val smokeAt = Instant.now()
                val smokePassed = smokeExecution.passed
                job = lifecycle.persist(
                    job.copy(
                        smokeCommand = smokeExecution.command,
                        smokeExitCode = smokeExecution.exitCode,
                        smokeDurationMillis = smokeExecution.durationMillis,
                        smokeStdout = smokeExecution.stdout.takeIf { it.isNotBlank() },
                        smokeStderr = smokeExecution.stderr.takeIf { it.isNotBlank() },
                        smokePassed = smokePassed,
                        smokeResult = smokeExecution.summary(),
                        status = if (smokePassed) job.status else AgentJobStatus.FAILED,
                        finishedAt = smokeAt,
                        updatedAt = smokeAt,
                        failureReason = if (smokePassed) job.failureReason else smokeExecution.summary(),
                        result = if (smokePassed) job.result else "smoke failed"
                    )
                )
                hooks.checkpoint(
                    if (smokePassed) AgentQueueCheckpoint.SMOKE_PASSED else AgentQueueCheckpoint.SMOKE_FAILED,
                    job,
                    smokeExecution.summary()
                )
            }

            val final = finalizeRun(job, task, smokeCommand, smokeExecution, baselineStatus, sourceEvidence)
            hooks.checkpoint(AgentQueueCheckpoint.FINALIZED, final, "final report persisted")
            return final
        } catch (cancelled: AgentRunCancelledException) {
            throw cancelled
        } catch (failure: Exception) {
            job = lifecycle.fail(job, failureSummary.compact(failure.message, AgentFailureSummary.RUN_FAILED), "agent run failed")
            val final = finalizeRun(job, task, smokeCommand, null, baselineStatus, sourceEvidence)
            hooks.checkpoint(AgentQueueCheckpoint.FINALIZED, final, "failed final report persisted")
            return final
        }
    }

    fun listJobs(limit: Int = 20): List<AgentJobRecord> = jobStore.listJobs(limit)

    fun resolveJob(reference: String): AgentJobRecord? = jobStore.resolve(reference)

    fun latestJob(): AgentJobRecord? = jobStore.latest()

    fun renderJobs(limit: Int = 20): String = jobStore.renderList(limit)

    private fun refuseUnsafeSmoke(task: String, smokeCommand: String, refusalReason: String): AgentJobRecord {
        val created = jobStore.createJob(task = task, provider = "none")
        val refusedAt = Instant.now()
        val smokeExecution = AgentSmokeExecutionResult(
            command = smokeCommand,
            passed = false,
            refusalReason = refusalReason
        )
        val refusedForReport = created.copy(
            status = AgentJobStatus.REFUSED,
            provider = "none"
        )
        val sourceEvidence = sourceResolver.resolveSourceEvidence(task)
        val refused = lifecycle.persist(
            created.copy(
                status = AgentJobStatus.REFUSED,
                provider = "none",
                finishedAt = refusedAt,
                updatedAt = refusedAt,
                result = "smoke preflight refused",
                failureReason = refusalReason,
                smokeCommand = smokeCommand,
                smokePassed = false,
                smokeResult = smokeExecution.summary(),
                sourceEvidence = sourceEvidence.provenanceOrNull,
                impactedSymbols = emptyList(),
                finalReport = reporter.buildFinalReport(refusedForReport, task, smokeCommand, smokeExecution, emptyList(), sourceEvidence, emptyList()),
                commitProposal = null,
                nextSuggestedCommand = reporter.buildSafeSmokeCommandSuggestion(task)
            )
        )
        outcomeMemory.rememberFinalOutcome(refused, emptyList(), sourceEvidence, emptyList())

        val contextPath = contextExporter.write(refused, emptyList())
        return lifecycle.persist(refused.copy(contextExportPath = contextPath.toString()))
    }

    private fun finalizeRun(
        job: AgentJobRecord,
        task: String,
        smokeCommand: String?,
        smokeExecution: AgentSmokeExecutionResult?,
        baselineStatus: Set<String>,
        sourceEvidence: SourceEvidence
    ): AgentJobRecord {
        val changedFiles = repoStatus.changedFilesSince(baselineStatus)
        val impactedSymbols = sourceResolver.impactedSymbolEvidence(changedFiles)
        val smokeRequested = smokeCommand?.trim()?.takeIf { it.isNotBlank() }
        val smokeSummary = when {
            smokeExecution != null -> smokeExecution.summary()
            smokeRequested != null -> "not run: ${job.status.name.lowercase()}"
            else -> null
        }
        val smokePassed = smokeExecution?.passed ?: if (smokeRequested != null) false else job.smokePassed
        val finalRecord = lifecycle.persist(
            job.copy(
                smokeCommand = smokeRequested ?: job.smokeCommand,
                smokeExitCode = smokeExecution?.exitCode ?: job.smokeExitCode,
                smokeDurationMillis = smokeExecution?.durationMillis ?: job.smokeDurationMillis,
                smokeStdout = smokeExecution?.stdout?.takeIf { it.isNotBlank() } ?: job.smokeStdout,
                smokeStderr = smokeExecution?.stderr?.takeIf { it.isNotBlank() } ?: job.smokeStderr,
                smokePassed = smokePassed,
                smokeResult = smokeSummary ?: job.smokeResult,
                sourceEvidence = sourceEvidence.provenanceOrNull,
                impactedSymbols = impactedSymbols,
                finalReport = reporter.buildFinalReport(job, task, smokeRequested, smokeExecution, changedFiles, sourceEvidence, impactedSymbols),
                commitProposal = reporter.buildCommitProposal(task, smokeRequested, changedFiles, smokeExecution),
                nextSuggestedCommand = reporter.buildNextSuggestedCommand(task, smokeRequested, changedFiles, job, smokeExecution)
            )
        )
        outcomeMemory.rememberFinalOutcome(finalRecord, changedFiles, sourceEvidence, impactedSymbols)

        val contextPath = contextExporter.write(finalRecord, changedFiles)
        return lifecycle.persist(finalRecord.copy(contextExportPath = contextPath.toString()))
    }
}
