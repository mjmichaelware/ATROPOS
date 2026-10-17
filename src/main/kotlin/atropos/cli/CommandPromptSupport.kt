/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import atropos.cli.commands.AgentCommand
import atropos.cli.commands.AgentCommandOutcome
import atropos.cli.input.CommandRisk
import atropos.cli.input.CommandRiskCatalog
import atropos.cli.ui.AnsiTerminalEngine
import atropos.cli.ui.ClipboardService
import atropos.cli.ui.CommandRegistryRenderer
import atropos.cli.ui.CopyDownloadResponse
import atropos.cli.ui.DialogOption
import atropos.cli.ui.DialogRenderer
import atropos.cli.ui.TranscriptBuffer
import atropos.cli.ui.TranscriptEntry
import atropos.core.integration.PipedStreamRouter
import atropos.core.observability.FuzzyExecutionGate

/**
 * Handles terminal prompts, confirmation dialogs, piped stream execution,
 * help page rendering, and clipboard synchronization for [CommandRouter].
 */
class CommandPromptSupport(
    private val uiEngine: AnsiTerminalEngine,
    private val dialogRenderer: DialogRenderer,
    private val commandRegistryRenderer: CommandRegistryRenderer,
    private val fuzzyExecutionGate: FuzzyExecutionGate,
    private val transcriptBuffer: TranscriptBuffer,
    private val clipboardService: ClipboardService,
    private val pipedStreamRouter: PipedStreamRouter,
    private val agentCommand: AgentCommand,
    private val onPatchStateUpdated: (String?) -> Unit
) {
    var pendingRiskyNaturalLanguage: String? = null

    companion object {
        const val MAX_PIPELINE_STAGE_CHARS = 60
        val PIPELINE_STAGE_HEAD = Regex("^[a-z][a-z0-9._/-]*$")
    }

    fun looksLikeShellPipeline(input: String): Boolean {
        val trimmed = input.trim()
        if (trimmed.startsWith("/") || !trimmed.contains('|')) return false
        if (trimmed.contains('\n')) return false
        val stages = trimmed.split('|').map(String::trim)
        if (stages.size < 2 || stages.any(String::isBlank)) return false
        return stages.all { stage ->
            stage.length <= MAX_PIPELINE_STAGE_CHARS &&
                PIPELINE_STAGE_HEAD.matches(stage.substringBefore(' '))
        }
    }

    fun handlePipedInput(input: String): RouterOutcome {
        val stages = input.split('|').map(String::trim)
        if (stages.any(String::isBlank)) {
            uiEngine.renderError("pipe: empty pipeline stage")
            return RouterOutcome.CONTINUE
        }
        val results = runCatching {
            pipedStreamRouter.routePipedCommands("", stages)
        }.getOrElse { failure ->
            uiEngine.renderError("pipe: ${failure.message ?: failure.javaClass.simpleName}")
            return RouterOutcome.CONTINUE
        }
        results.forEach { result ->
            uiEngine.renderBlock(
                "${result.command.joinToString(" ")} [exit=${result.exitCode}]\n${result.output}"
                    .trimEnd()
                    .lines()
            )
        }
        return RouterOutcome.CONTINUE
    }

    fun renderHelpPage(query: String = "") {
        val words = query.trim().split(' ').filter { it.isNotBlank() }
        val level = atropos.cli.help.HelpLevel.fromCanonical(words.firstOrNull())
        val remainder = if (level.canonical.equals(words.firstOrNull(), ignoreCase = true)) {
            words.drop(1).joinToString(" ")
        } else {
            words.joinToString(" ")
        }

        if (remainder.isNotBlank()) {
            uiEngine.renderHelp(remainder)
            return
        }

        if (words.firstOrNull()?.lowercase() in setOf("commands", "palette", "all")) {
            uiEngine.renderBlock(commandRegistryRenderer.renderSlashCommands(uiEngine.viewportWidth))
            return
        }

        atropos.cli.help.HelpGenerator().render(level).forEach(uiEngine::renderNotice)
    }

    fun renderRiskConfirmation(risk: String, request: String) {
        uiEngine.renderBlock(
            dialogRenderer.render(
                title = "Verification required — $risk",
                options = listOf(
                    DialogOption("yes", "run: ${request.take(60)}"),
                    DialogOption("no", "cancel without running")
                ),
                selectedIndex = 1,
                terminalWidth = uiEngine.viewportWidth,
                footerHint = "type yes to continue · no to cancel"
            )
        )
    }

    fun allowFuzzyExecution(input: String, resolved: String): Boolean {
        if (fuzzyExecutionGate.requestConfirmation(input, resolved)) return true
        if (CommandRiskCatalog.forCommand(resolved) != CommandRisk.RISKY) return true
        pendingRiskyNaturalLanguage = resolved
        renderRiskConfirmation("fuzzy command", resolved)
        return false
    }

    fun copyLastResponse(): String {
        val lastResponse = transcriptBuffer.entries()
            .filterIsInstance<TranscriptEntry.Text>()
            .lastOrNull()
            ?.value
        if (lastResponse == null || lastResponse.isBlank()) {
            return "no response to copy"
        }
        val artifact = CopyDownloadResponse().copy(lastResponse)
        if (clipboardService.copy(artifact.text)) {
            return "copied to clipboard (${artifact.bytes} bytes, redacted)"
        }
        return "clipboard unavailable; copied text: ${artifact.text}"
    }

    fun selfHostAlias(tokens: List<String>) {
        val remainder = tokens.drop(1)
        val subcommand = remainder.firstOrNull()?.lowercase()
        if (subcommand in setOf("help", "usage", "?", "/?", "/help", "/usage")) {
            renderHelpPage("self-host")
            return
        }
        val translated = SelfHostAliasTranslator.translate(tokens)
        if (translated == null) {
            uiEngine.renderError(
                "'${tokens.joinToString(" ")}' is not a self-host command. " +
                    "Try '/self-host status' or '/help self-host'."
            )
            return
        }
        announce(agentCommand.execute(translated))
        onPatchStateUpdated(agentCommand.lastKnownPatchId)
    }

    fun announce(outcome: AgentCommandOutcome) {
        if (outcome is AgentCommandOutcome.Invalid && !outcome.rendered) {
            uiEngine.renderError(outcome.message)
        }
    }
}
