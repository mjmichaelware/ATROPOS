/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import atropos.cli.ui.AnsiTerminalEngine
import atropos.cli.ui.AppFactoryPlanRenderer
import atropos.core.factory.FactoryClarificationRequired
import atropos.core.factory.FactoryClarificationRequest
import atropos.core.AtroposRepoRootLocator
import atropos.core.security.RedactionFilter
import java.nio.file.Path

class FactoryCommandHandler(
    private val uiEngine: AnsiTerminalEngine,
    private val renderer: AppFactoryPlanRenderer = AppFactoryPlanRenderer(),
    private val runFactory: (String) -> String = renderer::renderRun,
    private val redactionFilter: RedactionFilter = RedactionFilter(),
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val resumeFactory: (String, List<Boolean>) -> String = renderer::renderClarifiedRun
) {
    /**
     * @param original the command line as typed, before it was split into
     *   words. A `/factory run` prompt is usually a document -- an attached
     *   specification expanded in place by `@mention` -- and rebuilding it from
     *   `tokens` joined with single spaces destroyed every line break in it.
     *   SpecGraph segments on structure, so the whole document arrived as one
     *   paragraph and atomized to nothing: measured at 14 atoms with the line
     *   breaks and 0 without, reported as `SKIPPED_SOFT_FAIL:no_atoms_extracted`
     *   and silently fell back to the internal DAG.
     */
    fun execute(tokens: List<String>, original: String = ""): RouterOutcome {
        when (tokens.getOrNull(1)?.lowercase()) {
            null, "status" -> uiEngine.renderNotice(renderer.renderStatus())
            "plan" -> renderPlan(promptFrom(tokens, original))
            "run" -> renderRun(promptFrom(tokens, original))
            "answer" -> renderClarificationAnswer(tokens.drop(2))
            else -> uiEngine.renderError("usage: /factory [status|plan|run|answer] <prompt>")
        }
        return RouterOutcome.CONTINUE
    }

    /**
     * The prompt with its structure intact, or the joined words when the
     * original cannot be trusted to be the same text.
     *
     * The two are compared with whitespace collapsed: if an alias rewrite or a
     * different entry point means `tokens` no longer came from `original`, the
     * words win, because they are what the rest of the router acted on.
     */
    private fun promptFrom(tokens: List<String>, original: String): String {
        val joined = tokens.drop(2).joinToString(" ")
        if (original.isBlank()) return joined
        val remainder = afterWords(original, 2)
        return if (collapse(remainder) == collapse(joined)) remainder else joined
    }

    private fun afterWords(original: String, words: Int): String {
        val text = original.trimStart()
        var index = 0
        repeat(words) {
            while (index < text.length && !text[index].isWhitespace()) index++
            while (index < text.length && text[index].isWhitespace()) index++
        }
        return text.substring(index)
    }

    private fun collapse(text: String): String = text.split(Regex("\\s+")).filter { it.isNotEmpty() }.joinToString(" ")

    private fun renderPlan(prompt: String) {
        if (prompt.isBlank()) uiEngine.renderError("/factory plan requires a prompt")
        else uiEngine.renderNotice(renderer.renderPlan(prompt))
    }

    private fun renderRun(prompt: String) {
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
            uiEngine.renderNotice(result)
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
