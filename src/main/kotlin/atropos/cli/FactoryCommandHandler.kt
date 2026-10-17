/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import atropos.cli.ui.AnsiTerminalEngine
import atropos.cli.ui.AppFactoryPlanRenderer
import atropos.core.factory.FactoryClarificationRequired
import atropos.core.factory.FactoryClarificationRequest
import atropos.core.factory.FactoryEvidenceWaveExecutor
import atropos.core.AtroposRepoRootLocator
import atropos.core.security.RedactionFilter
import atropos.core.factory.AppFactoryRouter
import java.nio.file.Path

class FactoryCommandHandler(
    private val uiEngine: AnsiTerminalEngine,
    private val renderer: AppFactoryPlanRenderer = AppFactoryPlanRenderer(),
    private val runFactory: (String) -> String = renderer::renderRun,
    private val redactionFilter: RedactionFilter = RedactionFilter(),
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val resumeFactory: (String, List<Boolean>) -> String = renderer::renderClarifiedRun,
    private val resumeRunOverride: ((String) -> String)? = null
) {
    private fun resumeRun(runId: String): String = resumeRunOverride?.invoke(runId) ?: executeResumeRun(runId)

    private fun executeResumeRun(runId: String): String {
        val router = AppFactoryRouter(repoRoot = repoRoot)
        val context = router.resume(runId)
        val evidencePath = context.handoff.evidencePath
        return if (evidencePath != null && java.nio.file.Files.exists(evidencePath)) {
            val loop = router.resume(runId, context.acceptanceFreeze) { ready ->
                FactoryEvidenceWaveExecutor(evidencePath, context.acceptanceFreeze).execute(ready)
            }
            "resume executed: run=$runId dag=${context.handoff.dagId} prompt=${context.promptFingerprint} " +
                "freeze=${context.acceptanceFreeze.sha256} open_work=${loop.snapshot.openWork} " +
                "termination=${loop.terminationReason} waves=${loop.wavesExecuted}"
        } else {
            val dagStore = atropos.core.dag.DagStore(repoRoot)
            val dag = dagStore.readDag(context.handoff.dagId)
            requireNotNull(dag) { "factory resume failed: DAG '${context.handoff.dagId}' not found in DagStore" }

            val runRoot = repoRoot.resolve(".atropos/research/factory/$runId").normalize()
            val prompt = if (java.nio.file.Files.exists(runRoot.resolve("user-prompt.md"))) {
                runCatching { FactoryClarificationRequest.loadPrompt(runRoot) }.getOrNull()
            } else null
            val rawPrompt = prompt ?: runCatching {
                java.nio.file.Files.readAllLines(runRoot.resolve("user-prompt.md"))
                    .firstOrNull { it.startsWith("raw_text_redacted=") || it.startsWith("prompt_redacted=") }
                    ?.substringAfter('=')
            }.getOrNull() ?: "Resumed factory run $runId"

            val waveRequest = router.planWave(rawPrompt)
            val orchestrator = atropos.core.orchestration.AutonomousWaveOrchestrator.createDefault(repoRoot)
            val waveResult = orchestrator.resume(waveRequest, context.handoff.dagId, context.acceptanceFreeze)

            if (!waveResult.success) {
                handlePipelineHalt(runId, rawPrompt, waveResult.message, null, context.handoff.dagId)
            }

            "resume executed: run=$runId dag=${context.handoff.dagId} prompt=${context.promptFingerprint} " +
                "completed=${waveResult.completedAtoms.size} failed=${waveResult.failedAtoms.size} " +
                "blocked=${waveResult.blockedAtoms.size} status=${if (waveResult.success) "SUCCESS" else "HALTED"}"
        }
    }
    fun execute(tokens: List<String>): RouterOutcome {
        when (tokens.getOrNull(1)?.lowercase()) {
            null, "status" -> uiEngine.renderBlock(renderer.renderStatusList(uiEngine.viewportWidth))
            "plan" -> renderPlan(tokens.drop(2))
            "run" -> renderRun(tokens.drop(2))
            "resume" -> renderResume(tokens.drop(2))
            "answer" -> renderClarificationAnswer(tokens.drop(2))
            else -> uiEngine.renderError("usage: /factory [status|plan|run|resume|answer] <prompt|run-id>")
        }
        return RouterOutcome.CONTINUE
    }

    private fun renderResume(parts: List<String>) {
        val runId = parts.singleOrNull()?.trim() ?: findLatestFactoryRunId()
        if (runId.isNullOrBlank()) {
            uiEngine.renderError("usage: /factory resume <run-id> (no past factory runs located)")
            return
        }
        uiEngine.renderNotice("resuming factory run: $runId")
        runCatching { resumeRun(runId) }
            .onSuccess(uiEngine::renderNotice)
            .onFailure { uiEngine.renderError(redactionFilter.compact(it.message ?: "factory resume failed")) }
    }

    private fun renderPlan(parts: List<String>) {
        val prompt = parts.joinToString(" ")
        if (prompt.isBlank()) uiEngine.renderError("/factory plan requires a prompt")
        else uiEngine.renderNotice(renderer.renderPlan(prompt))
    }

    private fun renderRun(parts: List<String>) {
        val prompt = parts.joinToString(" ")
        if (prompt.isBlank()) {
            uiEngine.renderError("/factory run requires a prompt")
        } else {
            // Subscribed for the duration, so /thinking shows the run working.
            // Narration without a subscriber is the engine talking to itself,
            // which is exactly what a factory run did before this.
            val live = atropos.cli.ui.LiveThinkingRenderer(uiEngine)
            live.start("Factory run — /thinking 3 for the full trace")
            val result = try {
                runFactory(prompt)
            } catch (failure: FactoryClarificationRequired) {
                live.stop()
                val artifact = dumpComprehensiveFailureArtifact(failure.request.runId, prompt, "Clarification Required", failure, null)
                val questions = failure.questions.joinToString(" | ") { "YES/NO: $it" }
                uiEngine.renderError(
                    "factory clarification required: $questions; artifact=${failure.request.path}"
                )
                uiEngine.renderNotice("Diagnostic artifact with complete run trace saved: ${artifact ?: failure.request.path}")
                uiEngine.renderNotice("To answer and resume, run: /factory answer ${failure.request.runId} <YES|NO>")
                return
            } catch (failure: Throwable) {
                live.stop()
                val runId = "factory-${atropos.core.factory.FactoryLineage.sha256(prompt.trim()).take(16)}"
                val reason = redactionFilter.compact(failure.message ?: failure.javaClass.simpleName).ifBlank { "unknown failure" }
                handlePipelineHalt(runId, prompt, reason, failure)
                return
            }
            live.stop()
            uiEngine.renderNotice("factory run verified repository output:")
            uiEngine.renderNotice(redactionFilter.redact(result))
        }
    }

    private fun handlePipelineHalt(
        runId: String,
        prompt: String,
        exactReason: String,
        failure: Throwable?,
        dagId: String? = null
    ) {
        uiEngine.renderError("Pipeline halted: $exactReason")
        val question = "Pipeline halted: $exactReason. Generate comprehensive failure artifact? (y/n)"
        uiEngine.renderNotice(question)
        val shouldDump = promptConfirmation(question)
        if (shouldDump) {
            val artifact = dumpComprehensiveFailureArtifact(runId, prompt, exactReason, failure, dagId)
            if (artifact != null) {
                val byteCount = runCatching { java.nio.file.Files.size(artifact) }.getOrDefault(0L)
                uiEngine.renderNotice("Comprehensive failure artifact saved: $artifact ($byteCount bytes)")
            }
        }
        uiEngine.renderNotice("To attempt resuming this run, run: /factory resume $runId")
    }

    private fun promptConfirmation(promptText: String): Boolean {
        return runCatching {
            val console = System.console()
            if (console != null) {
                print("$promptText ")
                System.out.flush()
                val line = console.readLine()?.trim()?.lowercase()
                line == null || line.isEmpty() || line == "y" || line == "yes"
            } else {
                true
            }
        }.getOrDefault(true)
    }

    private fun findLatestFactoryRunId(): String? = findLatestFactoryRunId(repoRoot)

    private fun dumpComprehensiveFailureArtifact(
        runId: String?,
        prompt: String,
        reason: String,
        failure: Throwable?,
        dagIdOverride: String? = null
    ): Path? = runCatching {
        val targetDir = if (!runId.isNullOrBlank()) {
            repoRoot.resolve(".atropos/research/factory").resolve(runId)
        } else {
            repoRoot.resolve(".atropos/research/factory/latest")
        }
        java.nio.file.Files.createDirectories(targetDir)
        val artifactPath = targetDir.resolve("failure-artifact.md")

        val effectiveDagId = dagIdOverride ?: runId?.let {
            runCatching { FactoryRunHandoff.read(repoRoot, it).dagId }.getOrNull()
        }
        val dag = effectiveDagId?.let { atropos.core.dag.DagStore(repoRoot).readDag(it) }

        val traceLines = atropos.core.thinking.Thinking.stream.replay(atropos.core.thinking.ThinkingDepth.L3)
            .joinToString("\n") { "  [${it.at}] [${it.depth}] ${it.category.padEnd(8)}: ${it.text}" }

        val activeKeys = atropos.core.provider.FallbackChainRegistry.getAllActiveKeys()
            .map { (p, k) -> "  - $p: ${k.take(6)}...${k.takeLast(4)}" }
            .joinToString("\n")

        val stackTrace = failure?.let { err ->
            val sw = java.io.StringWriter()
            val pw = java.io.PrintWriter(sw)
            err.printStackTrace(pw)
            pw.flush()
            sw.toString()
        } ?: "none"

        val content = buildString {
            appendLine("# ATROPOS Factory Run Comprehensive Failure Artifact")
            appendLine("timestamp: ${java.time.Instant.now()}")
            appendLine("run_id: ${runId ?: "unknown"}")
            appendLine("planning_dag_id: ${effectiveDagId ?: "none"}")
            appendLine("halt_reason: $reason")
            appendLine("exception_class: ${failure?.javaClass?.name ?: "none"}")
            appendLine("exception_message: ${failure?.message ?: "none"}")
            appendLine()
            appendLine("## Active Provider Keys")
            appendLine(activeKeys.ifBlank { "  none recorded" })
            appendLine()
            appendLine("## User Prompt (RunState)")
            appendLine("```")
            appendLine(prompt)
            appendLine("```")
            appendLine()
            appendLine("## DAG Status")
            if (dag == null) {
                appendLine("  no DAG record located for dagId=${effectiveDagId ?: "null"}")
            } else {
                appendLine("- total_nodes: ${dag.nodes.size}")
                appendLine("- ready_nodes: ${dag.nodes.count { it.state == atropos.core.dag.DagNodeState.READY }}")
                appendLine("- complete_nodes: ${dag.nodes.count { it.state == atropos.core.dag.DagNodeState.COMPLETE }}")
                appendLine("- failed_nodes: ${dag.nodes.count { it.state == atropos.core.dag.DagNodeState.FAILED }}")
                appendLine("- blocked_nodes: ${dag.nodes.count { it.state == atropos.core.dag.DagNodeState.BLOCKED }}")
                appendLine()
                appendLine("### DAG Nodes Detail")
                for (node in dag.nodes) {
                    appendLine("  - Node [${node.id}] '${node.label}' state=${node.state} attempts=${node.attempts} result=${node.result ?: "none"}")
                    if (node.territory.isNotEmpty()) {
                        appendLine("    territory: ${node.territory.joinToString(", ")}")
                    }
                }
            }
            appendLine()
            appendLine("## Exception Stack Trace")
            appendLine("```")
            appendLine(stackTrace)
            appendLine("```")
            appendLine()
            appendLine("## Full L3 Thinking & Execution Trace")
            appendLine("```")
            appendLine(traceLines.ifBlank { "  no thinking trace recorded" })
            appendLine("```")
            appendLine()
            appendLine("## Resumption")
            appendLine("To resume this run from durable lineage and DAG state:")
            appendLine("`/factory resume ${runId ?: ""}`")
        }
        java.nio.file.Files.writeString(artifactPath, content, Charsets.UTF_8)
        artifactPath
    }.getOrNull()

    private fun renderClarificationAnswer(parts: List<String>) {
        val projectId = parts.firstOrNull()?.trim().orEmpty()
        val rawAnswers = parts.drop(1)
        if (!projectId.matches(PROJECT_ID) || rawAnswers.isEmpty()) {
            uiEngine.renderError("usage: /factory answer <project-id> <YES|NO> [YES|NO ...]")
            return
        }
        val answers = rawAnswers.map { token ->
            when (token.lowercase()) {
                "yes", "y" -> true
                "no", "n" -> false
                else -> null
            }
        }
        if (answers.any { it == null }) {
            uiEngine.renderError("factory clarification answers must be YES or NO")
            return
        }
        val runRoot = repoRoot.resolve(".atropos/research/factory").resolve(projectId).normalize()
        try {
            require(runRoot.startsWith(repoRoot.toAbsolutePath().normalize())) {
                "factory clarification path escaped repository root"
            }
            val request = FactoryClarificationRequest.load(runRoot)
            val answerHash = FactoryClarificationRequest.persistAnswers(
                runRoot = runRoot,
                request = request,
                answers = answers.filterNotNull()
            )
            uiEngine.renderNotice(
                "factory clarification answers persisted: project=$projectId " +
                    "prompt=${request.promptFingerprint} answers_sha256=$answerHash"
            )
            uiEngine.renderNotice(resumeFactory(projectId, answers.filterNotNull()))
        } catch (failure: RuntimeException) {
            val detail = redactionFilter.compact(failure.message ?: "unknown clarification failure")
            uiEngine.renderError("factory clarification failed: ${detail.ifBlank { "unknown failure" }}")
        }
    }

    companion object {
        val PROJECT_ID = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")

        fun findLatestFactoryRunId(repoRoot: Path = Path.of(".")): String? {
            val factoryDir = repoRoot.resolve(".atropos/research/factory")
            if (!java.nio.file.Files.isDirectory(factoryDir)) return null
            return runCatching {
                java.nio.file.Files.list(factoryDir)
                    .filter { java.nio.file.Files.isDirectory(it) }
                    .sorted { a, b -> java.nio.file.Files.getLastModifiedTime(b).compareTo(java.nio.file.Files.getLastModifiedTime(a)) }
                    .map { it.fileName.toString() }
                    .findFirst()
                    .orElse(null)
            }.getOrNull()
        }
    }
}
