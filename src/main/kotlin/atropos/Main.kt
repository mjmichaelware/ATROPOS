/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos

import atropos.cli.CommandRouter
import atropos.cli.RouterOutcome
import atropos.cli.config.ConfigurationManager
import atropos.cli.input.CommandCompleter
import atropos.cli.input.KeyEvent
import atropos.cli.input.PromptEffect
import atropos.cli.input.PromptState
import atropos.cli.input.RawKeyReader
import atropos.cli.input.TerminalModeManager
import atropos.cli.session.QuotaSessionTracker
import atropos.cli.session.ScreenId
import atropos.cli.ui.AnsiTerminalEngine
import atropos.core.AtroposConfig
import atropos.core.agent.SelfHostStartupContinuationService
import atropos.core.agent.AgentDaemonService
import atropos.core.recovery.RuntimeContinuitySupervisor
import atropos.core.recovery.ContinuityOutcome
import atropos.core.security.SecretEnrollment
import atropos.core.security.EnvironmentSecretSource
import atropos.core.security.RedactionFilter
import java.io.FileInputStream

fun main(args: Array<String>) {
    SecretEnrollment(listOf(EnvironmentSecretSource())).enrollInto(RedactionFilter.defaultRegistry)

    if (args.firstOrNull() == "--agent-daemon-foreground") {
        val config = AtroposConfig.load()
        val result = AgentDaemonService(config).foreground(config.runtime.defaultProvider)
        println(result.render())
        return
    }

    val capabilities = ConfigurationManager()
    val ui = AnsiTerminalEngine(capabilities)
    val tracker = QuotaSessionTracker()

    try {
        val config = AtroposConfig.load()

        // Long-horizon continuity: durable state left behind by a previous
        // process is repaired before the runtime serves anything. This used to
        // require an operator to type `/agent recover`, which meant stale
        // leases and interrupted runs survived indefinitely if nobody thought
        // to ask.
        val continuity = RuntimeContinuitySupervisor()
        val continuityOutcome = continuity.ensureRecovered()
        continuity.startupNotice(continuityOutcome)?.let(ui::renderNotice)
        SelfHostStartupContinuationService()
            .continueOnce(continuityOutcome.safeForSelfHostContinuation)
            .takeIf { it.attempted }
            ?.let { result ->
                if (result.ok) ui.renderNotice(result.message ?: "self-host continuation completed")
                else ui.renderError(result.message ?: "self-host continuation stopped")
            }

        val router = CommandRouter(
            config = config,
            uiEngine = ui,
            sessionTracker = tracker,
            rateResolver = capabilities::inputUsdPerToken
        )

        if (capabilities.isInteractiveTerminal) {
            runInteractive(capabilities, config, ui, tracker, router)
        } else {
            runHeadless(config, ui, router)
        }
    } catch (failure: Exception) {
        ui.renderError(
            "startup failed (${failure.javaClass.simpleName}): " +
                (failure.message ?: "unknown failure")
        )
    } finally {
        ui.cleanup()
    }
}

private fun runInteractive(
    capabilities: ConfigurationManager,
    config: AtroposConfig,
    ui: AnsiTerminalEngine,
    tracker: QuotaSessionTracker,
    router: CommandRouter
) {
    val prompt = PromptState()
    val completer = CommandCompleter(
        java.nio.file.Path.of(capabilities.workspace)
    )
    val tabs = router.tabs

    ui.initializeReactive()
    ui.renderWelcome(config, router.currentProviderName)

    TerminalModeManager().use { terminalMode ->
        terminalMode.enableRawMode()

        FileInputStream("/dev/tty").use { input ->
            val keys = RawKeyReader(input)

            fun completionForPrompt() =
                completer.complete(
                    prompt.text,
                    prompt.cursor,
                    prompt.suggestionSelection()
                )

            fun preserveTabState() {
                tabs.preservePrompt(
                    buffer = prompt.text,
                    cursor = prompt.cursor,
                    selectedSuggestion = prompt.suggestionSelection()
                )
            }

            fun resolvePromptSubmission() {
                val resolved = completer.resolveSubmission(
                    prompt.text,
                    prompt.cursor,
                    prompt.suggestionSelection()
                )

                if (resolved != null && resolved != prompt.text) {
                    prompt.clear()
                    prompt.insert(resolved)
                }
            }

            fun applyPromptCompletion() {
                val completion = completionForPrompt()
                prompt.clampSuggestionSelection(
                    if (completion.options.isEmpty()) 0
                    else completion.options.lastIndex
                )

                val selected = completer.complete(
                    prompt.text,
                    prompt.cursor,
                    prompt.suggestionSelection()
                )

                if (selected.insertion.isNotEmpty()) {
                    prompt.insert(selected.insertion)
                } else if (selected.options.isNotEmpty()) {
                    val resolved = selected.options[
                        selected.selectedIndex.coerceIn(0, selected.options.lastIndex)
                    ]
                    prompt.clear()
                    prompt.insert(resolved)
                }
            }

            fun redraw() {
                val completion = completionForPrompt()
                prompt.clampSuggestionSelection(
                    if (completion.options.isEmpty()) 0
                    else completion.options.lastIndex
                )

                val selected = completer.complete(
                    prompt.text,
                    prompt.cursor,
                    prompt.suggestionSelection()
                )

                preserveTabState()

                ui.redrawPrompt(
                    buffer = prompt.text,
                    cursor = prompt.cursor,
                    suggestion = selected.preview,
                    inputMode = prompt.mode.name,
                    provider = tabs.active.provider,
                    tracker = tracker,
                    paletteSelection = selected.selectedIndex,
                    activeScreen = tabs.active.title,
                    activeTab = "tab ${tabs.active.id}",
                    openTabCount = tabs.snapshot().tabs.size
                )
            }

            redraw()

            inputLoop@ while (true) {
                val key = keys.readKey() ?: break
                val submittedMode = prompt.mode.name

                if (key == KeyEvent.Enter) {
                    resolvePromptSubmission()
                }

                val submitted = prompt.text

                when (key) {
                    KeyEvent.CtrlT -> {
                        preserveTabState()
                        tabs.openTab(
                            screen = ScreenId.CHAT,
                            provider = router.currentProviderName,
                            workingDirectory = tabs.active.workingDirectory
                        )
                        ui.renderNotice(
                            "tab ${tabs.active.id}: ${tabs.active.title}"
                        )
                        redraw()
                        continue@inputLoop
                    }

                    KeyEvent.CtrlTab -> {
                        preserveTabState()
                        tabs.switchNext()
                        ui.renderNotice(
                            "tab ${tabs.active.id}: ${tabs.active.title}"
                        )
                        redraw()
                        continue@inputLoop
                    }

                    else -> Unit
                }

                val effect = prompt.apply(key)

                when {
                    effect is PromptEffect.Complete -> {
                        applyPromptCompletion()
                        redraw()
                    }

                    effect is PromptEffect.Submit -> {
                        ui.commitPrompt(submitted, submittedMode)
                        if (submitted.isNotBlank()) {
                            if (router.handleInput(submitted) == RouterOutcome.EXIT) {
                                break@inputLoop
                            }
                        }
                        redraw()
                    }

                    effect is PromptEffect.EndOfInput -> break@inputLoop

                    effect is PromptEffect.Cancel -> {
                        ui.cancelPrompt()
                        redraw()
                    }

                    effect is PromptEffect.InputError -> {
                        ui.renderError("invalid terminal input")
                        redraw()
                    }

                    else -> redraw()
                }
            }
        }
    }

    ui.renderNotice("session closed")
}

private fun runHeadless(
    config: AtroposConfig,
    ui: AnsiTerminalEngine,
    router: CommandRouter
) {
    ui.renderWelcome(config, router.currentProviderName)

    val reader = System.`in`.bufferedReader()
    while (true) {
        val line = reader.readLine() ?: break
        if (router.handleInput(line) == RouterOutcome.EXIT) break
    }
}
