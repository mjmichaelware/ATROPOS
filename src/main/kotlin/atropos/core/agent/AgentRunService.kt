package atropos.core.agent

import atropos.core.AtroposConfig
import java.time.Instant

class AgentRunService(
    private val config: AtroposConfig = AtroposConfig.load(),
    private val collector: AgentContextCollector = AgentContextCollector(),
    private val agentService: AgentService = AgentService(config, collector),
    private val patchStore: AgentPatchStore = AgentPatchStore(collector.repoRoot),
    private val jobStore: AgentJobStore = AgentJobStore(collector.repoRoot)
) {
    fun run(activeProviderName: String, task: String): AgentJobRecord {
        val startedAt = Instant.now()
        var job = jobStore.createJob(task = task, provider = activeProviderName)
        job = persist(job.copy(status = AgentJobStatus.PLANNING, updatedAt = startedAt))

        try {
            val planPrompt = buildPlanPrompt(task)
            val planResult = agentService.ask(activeProviderName, planPrompt)
            val planAt = Instant.now()
            job = persist(
                job.copy(
                    provider = planResult.providerName,
                    status = AgentJobStatus.PATCHING,
                    planAt = planAt,
                    updatedAt = planAt,
                    plan = planResult.render(),
                    result = "plan captured from ${planResult.providerName}"
                )
            )

            val patchTask = buildPatchTask(task, planResult.answerText)
            val providerPatchResult = agentService.patch(activeProviderName, patchTask)
            val patchResult = providerPatchResult.takeIf { it.patchId != null && it.checkResult?.passed != false }
                ?: synthesizeLocalPatch(task)
                ?: providerPatchResult
            val patchAt = Instant.now()
            job = persist(
                job.copy(
                    provider = patchResult.providerName,
                    status = AgentJobStatus.APPLYING,
                    patchId = patchResult.patchId,
                    patchAt = patchAt,
                    updatedAt = patchAt,
                    patchResult = patchResult.render()
                )
            )

            val patchId = patchResult.patchId
            if (patchId.isNullOrBlank()) {
                val failureReason = patchResult.failureSummary
                    ?: patchResult.rejectionReason
                    ?: patchResult.message
                    ?: "patch generation failed"
                return fail(job, failureReason, "patch generation failed before apply")
            }

            val applyResult = agentService.applyPatch(patchId, checkOnly = false, verifyAfterApply = true)
            val applyAt = Instant.now()
            val initialVerificationId = applyResult.verificationResult?.verificationId
            job = persist(
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
                        buildSuccessResult(patchId, initialVerificationId, null)
                    } else {
                        "initial apply or verification failed"
                    }
                )
            )

            if (applyResult.applied && applyResult.verificationResult?.passed == true) {
                return complete(job)
            }

            val applyFailure = applyResult.verificationResult?.refusalReason
                ?: applyResult.refusalReason
                ?: applyResult.checkResult?.output
                ?: applyResult.applyOutput
                ?: "verification failed"

            val repairResult = agentService.repair(activeProviderName, patchId)
            val repairAt = Instant.now()
            job = persist(
                job.copy(
                    provider = repairResult.providerName,
                    status = AgentJobStatus.REPAIRING,
                    repairId = repairResult.patchId,
                    repairAt = repairAt,
                    updatedAt = repairAt,
                    repairResult = repairResult.render()
                )
            )

            val repairPatchId = repairResult.patchId
            if (repairPatchId.isNullOrBlank()) {
                val failureReason = repairResult.failureSummary
                    ?: repairResult.rejectionReason
                    ?: repairResult.message
                    ?: applyFailure
                return fail(job, failureReason, "repair generation failed after verification failure")
            }

            val repairedApply = agentService.applyPatch(repairPatchId, checkOnly = false, verifyAfterApply = true)
            val repairedAt = Instant.now()
            val repairedVerificationId = repairedApply.verificationResult?.verificationId
            job = persist(
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
                        buildSuccessResult(patchId, repairedVerificationId, repairPatchId)
                    } else {
                        "repair apply or verification failed"
                    }
                )
            )

            return if (job.status == AgentJobStatus.COMPLETED) {
                complete(job)
            } else {
                val failureReason = repairedApply.verificationResult?.refusalReason
                    ?: repairedApply.refusalReason
                    ?: repairedApply.checkResult?.output
                    ?: repairedApply.applyOutput
                    ?: "verification failed after repair"
                fail(job, failureReason, "repair patch did not verify")
            }
        } catch (failure: Exception) {
            return fail(job, compactFailureSummary(failure.message), "agent run failed")
        }
    }

    fun listJobs(limit: Int = 20): List<AgentJobRecord> = jobStore.listJobs(limit)

    fun resolveJob(reference: String): AgentJobRecord? = jobStore.resolve(reference)

    fun latestJob(): AgentJobRecord? = jobStore.latest()

    fun renderJobs(limit: Int = 20): String = jobStore.renderList(limit)

    private fun complete(job: AgentJobRecord): AgentJobRecord {
        val finishedAt = job.finishedAt ?: Instant.now()
        return persist(
            job.copy(
                status = AgentJobStatus.COMPLETED,
                finishedAt = finishedAt,
                updatedAt = finishedAt,
                failureReason = null,
                result = job.result ?: "job completed"
            )
        )
    }

    private fun fail(job: AgentJobRecord, failureReason: String, result: String): AgentJobRecord {
        val finishedAt = Instant.now()
        return persist(
            job.copy(
                status = AgentJobStatus.FAILED,
                finishedAt = finishedAt,
                updatedAt = finishedAt,
                failureReason = failureReason.trim().ifBlank { "agent run failed" },
                result = result.trim().ifBlank { "agent run failed" }
            )
        )
    }

    private fun persist(record: AgentJobRecord): AgentJobRecord =
        jobStore.update(record)

    private fun buildPlanPrompt(task: String): String = buildString {
        appendLine("Create a short implementation plan for this ATROPOS job.")
        appendLine("Return reasoning only, no diff.")
        appendLine("Task:")
        appendLine(task.trim())
    }.trimEnd()

    private fun buildPatchTask(task: String, plan: String): String = buildString {
        appendLine(task.trim())
        val compactPlan = plan.trim().take(2000)
        if (compactPlan.isNotBlank()) {
            appendLine()
            appendLine("Plan context:")
            appendLine(compactPlan)
        }
    }.trimEnd()

    private fun buildSuccessResult(initialPatchId: String, verificationId: String?, repairPatchId: String?): String = buildString {
        append("completed")
        append(" patch=$initialPatchId")
        if (repairPatchId != null) append(" repair=$repairPatchId")
        verificationId?.let { append(" verification=$it") }
    }

    private data class LocalPatchRequest(
        val path: String,
        val content: String
    )

    private fun synthesizeLocalPatch(task: String): AgentPatchRunResult? {
        val request = parseLocalCreateTask(task) ?: return null
        val diff = buildCreateFileDiff(request.path, request.content)
        val normalizedDiff = patchStore.normalizeProviderDiff(diff)
        val record = patchStore.createRecord(
            provider = "local_fallback",
            task = task,
            contextBytes = 0,
            diff = normalizedDiff
        )
        val check = patchStore.runGitApplyCheck(record.diffFile)
        patchStore.writeMeta(record, check)

        return AgentPatchRunResult(
            providerName = "local_fallback",
            contextByteCount = 0,
            diffByteCount = record.diffBytes,
            patchId = record.id,
            patchPath = record.diffFile,
            checkResult = check,
            failureSummary = if (check.passed) null else "local fallback patch did not pass git apply --check",
            message = if (check.passed) null else "local fallback patch failed validation"
        )
    }

    private fun parseLocalCreateTask(task: String): LocalPatchRequest? {
        val line = task.lineSequence().firstOrNull()?.trim().orEmpty()
        if (line.isBlank()) return null

        val match = Regex(
            """(?i)^create\s+(.+?)\s+containing\s+exactly\s+one\s+line:\s*(.+)$"""
        ).find(line) ?: return null
        val path = match.groupValues.getOrNull(1)?.trim().orEmpty()
        val content = match.groupValues.getOrNull(2)?.trim().orEmpty()
        if (path.isBlank() || content.isBlank()) return null
        if (path.contains("..") || path.startsWith("/") || path.startsWith("\\")) return null
        return LocalPatchRequest(path = path, content = content)
    }

    private fun buildCreateFileDiff(path: String, content: String): String {
        val lines = content.replace("\r\n", "\n").replace('\r', '\n').lineSequence().toList()
        val safeLines = if (lines.isEmpty()) listOf("") else lines
        return buildString {
            appendLine("--- /dev/null")
            appendLine("+++ b/$path")
            appendLine("@@ -0,0 +1,${safeLines.size} @@")
            safeLines.forEach { line -> appendLine("+$line") }
        }.trimEnd() + "\n"
    }

    private fun compactFailureSummary(message: String?): String =
        message?.trim().takeUnless { it.isNullOrBlank() } ?: "agent run failed"
}
