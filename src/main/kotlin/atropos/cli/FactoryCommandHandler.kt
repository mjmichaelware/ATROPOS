/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import atropos.cli.ui.AnsiTerminalEngine
import atropos.cli.ui.AppFactoryPlanRenderer
import atropos.core.factory.FactoryClarificationRequired
import atropos.core.factory.FactoryClarificationRequest
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
    private val resumeRun: (String) -> String = { runId ->
        val context = AppFactoryRouter(repoRoot = repoRoot).resume(runId)
        "resume attested: run=$runId dag=${context.handoff.dagId} prompt=${context.promptFingerprint} " +
            "freeze=${context.acceptanceFreeze.sha256} open_work=${context.handoff.openWork} " +
            "next=${context.handoff.nextRunnableAtomIds.joinToString(",").ifBlank { "none" }}"
    }
) {
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
        val runId = parts.singleOrNull()?.trim()
        if (runId.isNullOrBlank()) {
            uiEngine.renderError("usage: /factory resume <run-id>")
            return
        }
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
                val questions = failure.questions.joinToString(" | ") { "YES/NO: $it" }
                uiEngine.renderError(
                    "factory clarification required: $questions; artifact=${failure.request.path}"
                )
                return
            } catch (failure: RuntimeException) {
                live.stop()
                val detail = redactionFilter.compact(failure.message ?: "unknown failure")
                uiEngine.renderError("factory run failed: ${detail.ifBlank { "unknown failure" }}")
                return
            }
            live.stop()
            uiEngine.renderNotice("factory run verified repository output:")
            uiEngine.renderNotice(redactionFilter.redact(result))
        }
    }

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

    private companion object {
        val PROJECT_ID = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
    }
}
