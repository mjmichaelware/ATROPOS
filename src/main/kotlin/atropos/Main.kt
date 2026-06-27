/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos

import atropos.cli.CommandRouter
import atropos.cli.RouterOutcome
import atropos.cli.config.ConfigurationManager
import atropos.cli.input.*
import atropos.cli.session.QuotaSessionTracker
import atropos.cli.ui.AnsiTerminalEngine
import atropos.core.AtroposConfig
import java.io.FileInputStream

fun main() {
    val capabilities = ConfigurationManager()
    val ui = AnsiTerminalEngine(capabilities)
    val tracker = QuotaSessionTracker()

    try {
        val config = AtroposConfig.load()
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

    ui.initializeReactive()
    ui.renderWelcome(config, router.currentProviderName)

    TerminalModeManager().use { terminalMode ->
        terminalMode.enableRawMode()

        FileInputStream("/dev/tty").use { input ->
            val keys = RawKeyReader(input)

            fun redraw() {
                val completion = completer.complete(prompt.text, prompt.cursor)
                ui.redrawPrompt(
                    buffer = prompt.text,
                    cursor = prompt.cursor,
                    suggestion = completion.preview,
                    inputMode = prompt.mode.name,
                    provider = router.currentProviderName,
                    tracker = tracker
                )
            }

            redraw()

            inputLoop@ while (true) {
                val key = keys.readKey() ?: break
                val submitted = prompt.text
                val submittedMode = prompt.mode.name
                val effect = prompt.apply(key)

                when {
                    effect is PromptEffect.Complete -> {
                        val completion = completer.complete(
                            prompt.text,
                            prompt.cursor
                        )
                        if (completion.insertion.isNotEmpty()) {
                            prompt.insert(completion.insertion)
                        }
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
