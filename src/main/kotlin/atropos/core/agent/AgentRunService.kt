package atropos.core.agent

import atropos.core.AtroposConfig
import java.time.Instant

class AgentRunService(
    private val config: AtroposConfig = AtroposConfig.load(),
    private val collector: AgentContextCollector = AgentContextCollector(),
    private val agentService: AgentService = AgentService(config, collector),
    private val patchStore: AgentPatchStore = AgentPatchStore(collector.repoRoot),
    private val jobStore: AgentJobStore = AgentJobStore(collector.repoRoot),
    private val smokeRunner: AgentSmokeRunner = AgentSmokeRunner(collector.repoRoot),
    private val contextExporter: AgentContextExportStore = AgentContextExportStore(collector.repoRoot)
) {
    fun run(activeProviderName: String, task: String, smokeCommand: String? = null): AgentJobRecord {
        val smokeRequested = smokeCommand?.trim()?.takeIf { it.isNotBlank() }
        if (smokeRequested != null) {
            val smokeRefusal = smokeRunner.validate(smokeRequested)
            if (smokeRefusal != null) {
                return refuseUnsafeSmoke(task, smokeRequested, smokeRefusal)
            }
        }

        val startedAt = Instant.now()
        val baselineStatus = captureRepoStatus()
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
                job = fail(job, failureReason, "patch generation failed before apply")
                return finalizeRun(job, task, smokeCommand, null, baselineStatus)
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
                job = complete(job)
            }

            if (job.status != AgentJobStatus.COMPLETED) {
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
                    job = fail(job, failureReason, "repair generation failed after verification failure")
                    return finalizeRun(job, task, smokeCommand, null, baselineStatus)
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

                if (job.status != AgentJobStatus.COMPLETED) {
                    val failureReason = repairedApply.verificationResult?.refusalReason
                        ?: repairedApply.refusalReason
                        ?: repairedApply.checkResult?.output
                        ?: repairedApply.applyOutput
                        ?: "verification failed after repair"
                    job = fail(job, failureReason, "repair patch did not verify")
                }
            }

            val smokeExecution = if (smokeCommand.isNullOrBlank()) {
                null
            } else if (job.status == AgentJobStatus.COMPLETED) {
                smokeRunner.run(smokeCommand)
            } else {
                null
            }

            if (smokeExecution != null) {
                val smokeAt = Instant.now()
                val smokePassed = smokeExecution.passed
                job = persist(
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
            }

            return finalizeRun(job, task, smokeCommand, smokeExecution, baselineStatus)
        } catch (failure: Exception) {
            job = fail(job, compactFailureSummary(failure.message), "agent run failed")
            return finalizeRun(job, task, smokeCommand, null, baselineStatus)
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
        val refused = persist(
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
                finalReport = buildFinalReport(refusedForReport, task, smokeCommand, smokeExecution, emptyList()),
                commitProposal = null,
                nextSuggestedCommand = buildSafeSmokeCommandSuggestion(task)
            )
        )

        val contextPath = contextExporter.write(refused, emptyList())
        return persist(refused.copy(contextExportPath = contextPath.toString()))
    }

    private fun finalizeRun(
        job: AgentJobRecord,
        task: String,
        smokeCommand: String?,
        smokeExecution: AgentSmokeExecutionResult?,
        baselineStatus: Set<String>
    ): AgentJobRecord {
        val changedFiles = changedFilesSince(baselineStatus)
        val smokeRequested = smokeCommand?.trim()?.takeIf { it.isNotBlank() }
        val smokeSummary = when {
            smokeExecution != null -> smokeExecution.summary()
            smokeRequested != null -> "not run: ${job.status.name.lowercase()}"
            else -> null
        }
        val smokePassed = smokeExecution?.passed ?: if (smokeRequested != null) false else job.smokePassed
        val finalRecord = persist(
            job.copy(
                smokeCommand = smokeRequested ?: job.smokeCommand,
                smokeExitCode = smokeExecution?.exitCode ?: job.smokeExitCode,
                smokeDurationMillis = smokeExecution?.durationMillis ?: job.smokeDurationMillis,
                smokeStdout = smokeExecution?.stdout?.takeIf { it.isNotBlank() } ?: job.smokeStdout,
                smokeStderr = smokeExecution?.stderr?.takeIf { it.isNotBlank() } ?: job.smokeStderr,
                smokePassed = smokePassed,
                smokeResult = smokeSummary ?: job.smokeResult,
                finalReport = buildFinalReport(job, task, smokeRequested, smokeExecution, changedFiles),
                commitProposal = buildCommitProposal(task, smokeRequested, changedFiles, smokeExecution),
                nextSuggestedCommand = buildNextSuggestedCommand(task, smokeRequested, changedFiles, job, smokeExecution)
            )
        )

        val contextPath = contextExporter.write(finalRecord, changedFiles)
        return persist(finalRecord.copy(contextExportPath = contextPath.toString()))
    }

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

    private fun buildFinalReport(
        job: AgentJobRecord,
        task: String,
        smokeCommand: String?,
        smokeExecution: AgentSmokeExecutionResult?,
        changedFiles: List<String>
    ): String = buildString {
        appendLine("status: ${renderFinalStatus(job.status)}")
        appendLine("task: ${compactTask(task)}")
        appendLine("provider: ${job.provider}")
        appendLine("patch: ${job.appliedPatchId ?: job.patchId ?: "none"}")
        appendLine("verification: ${job.verificationId ?: "none"}")
        appendLine("smoke: ${smokeExecution?.summary() ?: smokeCommand?.let { "not run" } ?: "not requested"}")
        appendLine("changed files: ${changedFiles.joinToString(", ").ifBlank { "none" }}")
    }.trimEnd()

    private fun buildCommitProposal(
        task: String,
        smokeCommand: String?,
        changedFiles: List<String>,
        smokeExecution: AgentSmokeExecutionResult?
    ): String = buildString {
        appendLine("files to stage:")
        if (changedFiles.isEmpty()) {
            appendLine("  none")
        } else {
            changedFiles.forEach { path -> appendLine("  - $path") }
        }
        appendLine("suggested commit message:")
        appendLine("  ${buildCommitMessage(task, smokeCommand, smokeExecution)}")
    }.trimEnd()

    private fun buildCommitMessage(
        task: String,
        smokeCommand: String?,
        smokeExecution: AgentSmokeExecutionResult?
    ): String {
        val core = compactTask(task, 60)
        val smokeSuffix = when {
            smokeExecution?.passed == true -> " smoke"
            smokeExecution != null -> " smoke-failed"
            smokeCommand != null -> " smoke-pending"
            else -> ""
        }
        return "ATROPOS pass 11: $core$smokeSuffix".trim()
    }

    private fun buildNextSuggestedCommand(
        task: String,
        smokeCommand: String?,
        changedFiles: List<String>,
        job: AgentJobRecord,
        smokeExecution: AgentSmokeExecutionResult?
    ): String {
        return when {
            smokeExecution != null && !smokeExecution.passed ->
                smokeCommand?.takeIf { it.isNotBlank() }?.let { "review smoke failure, then rerun /agent run --smoke \"${escapeQuotes(it)}\" ${compactTask(task, 48)}" }
                    ?: "review smoke failure, then rerun /agent run"
            job.status == AgentJobStatus.COMPLETED && changedFiles.isNotEmpty() -> {
                val commitMessage = buildCommitMessage(task, smokeCommand, smokeExecution)
                "git add ${changedFiles.joinToString(" ")} && git commit -m \"${escapeQuotes(commitMessage)}\""
            }
            job.status == AgentJobStatus.COMPLETED -> "git status --short"
            job.status == AgentJobStatus.FAILED -> "/agent repair ${job.patchId ?: "latest"}"
            else -> "/agent job ${job.id}"
        }
    }

    private fun changedFilesSince(baseline: Set<String>): List<String> {
        val current = captureRepoStatus()
        return (current - baseline)
            .filter { isStageableChange(it) }
            .sorted()
    }

    private fun captureRepoStatus(): Set<String> {
        val process = ProcessBuilder("git", "status", "--porcelain", "--untracked-files=all")
            .directory(collector.repoRoot.toFile())
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().readText().trimEnd()
        process.waitFor()
        return output.lineSequence()
            .mapNotNull { parsePorcelainPath(it) }
            .toSet()
    }

    private fun parsePorcelainPath(line: String): String? {
        if (line.length < 4) return null
        val path = line.substring(3).trim()
        if (path.isBlank()) return null
        return path.substringAfter(" -> ", path)
    }

    private fun isStageableChange(path: String): Boolean {
        val normalized = path.replace('\\', '/')
        val name = normalized.substringAfterLast('/')
        if (normalized.startsWith(".atropos/") || normalized == ".atropos") return false
        if (normalized.startsWith(".gradle/") || normalized == ".gradle") return false
        if (normalized.startsWith("build/") || normalized == "build") return false
        if (name.endsWith(".jar") || name.endsWith(".class")) return false
        if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".gif") || name.endsWith(".zip")) return false
        if (normalized == ".env" || normalized.startsWith(".env.")) return false
        if (name.contains("token", ignoreCase = true)) return false
        if (name.contains("secret", ignoreCase = true)) return false
        if (name.contains("credential", ignoreCase = true)) return false
        return true
    }

    private fun compactTask(task: String, maxChars: Int = 80): String {
        val collapsed = task.replace(Regex("\\s+"), " ").trim()
        if (collapsed.length <= maxChars) return collapsed
        return collapsed.take(maxChars - 3) + "..."
    }

    private fun escapeQuotes(text: String): String = text.replace("\"", "\\\"")

    private fun renderFinalStatus(status: AgentJobStatus): String = when (status) {
        AgentJobStatus.COMPLETED -> "passed"
        AgentJobStatus.FAILED -> "failed"
        AgentJobStatus.REFUSED -> "refused"
        AgentJobStatus.PLANNING,
        AgentJobStatus.PATCHING,
        AgentJobStatus.APPLYING,
        AgentJobStatus.REPAIRING -> status.name.lowercase()
    }

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

    private fun buildSafeSmokeCommandSuggestion(task: String): String =
        "choose a safe smoke command, then rerun /agent run --smoke \"<safe smoke command>\" ${compactTask(task, 48)}"
}
