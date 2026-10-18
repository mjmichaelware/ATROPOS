/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import atropos.cli.config.ConfigurationManager
import atropos.cli.input.CommandCompleter
import atropos.cli.input.CommandHistoryStore
import atropos.cli.input.KeyEvent
import atropos.cli.input.PromptEffect
import atropos.cli.input.PromptState
import atropos.cli.input.RawKeyReader
import atropos.cli.input.TerminalModeManager
import atropos.cli.session.QuotaSessionTracker
import atropos.cli.session.ScreenId
import atropos.cli.ui.AnsiTerminalEngine
import atropos.cli.ui.ViewportLayout
import atropos.core.AtroposConfig
import java.io.FileInputStream

object InteractiveCliRunner {

    fun runInteractive(
        capabilities: ConfigurationManager,
        config: AtroposConfig,
        ui: AnsiTerminalEngine,
        tracker: QuotaSessionTracker,
        router: CommandRouter
    ) {
        val prompt = PromptState(historyStore = CommandHistoryStore(java.nio.file.Path.of(".atropos/command-history.tsv")))
        val completer = CommandCompleter(router.ingestRoots)
        val tabs = router.tabs
        val clipboardService = atropos.cli.ui.ClipboardService()

        val commands = BackgroundCommandRunner(router::handleInput, ui) { ui.renderPrompt() }

        ui.initializeReactive()
        ui.renderWelcome(config, router.currentProviderName)

        commands.use {
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

                    fun resolvePromptSubmission(): Boolean {
                        val resolved = completer.resolveSubmission(
                            prompt.text,
                            prompt.cursor,
                            prompt.suggestionSelection()
                        )

                        if (resolved != null && resolved != prompt.text) {
                            if (completer.lastResolutionWasFuzzy &&
                                !router.allowFuzzyExecution(prompt.text, resolved)
                            ) return false
                            prompt.clear()
                            prompt.insert(resolved)
                        }
                        return true
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

                    fun paintPrompt() {
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
                        atropos.core.observability.BoundedRenderingController.updateActiveInputBuffer(prompt.text)

                        ui.setTabs(
                            tabs.snapshot().tabs.map { tab ->
                                ViewportLayout.TabState(
                                    id = tab.id.toString(),
                                    name = tab.title,
                                    isActive = tab.id == tabs.active.id,
                                    trustLevel = ViewportLayout.TrustIndicator.UNKNOWN
                                )
                            }
                        )

                        ui.redrawPrompt(
                            buffer = prompt.text,
                            cursor = prompt.cursor,
                            suggestion = selected.preview,
                            inputMode = prompt.mode.name,
                            provider = tabs.active.provider,
                            tracker = tracker,
                            paletteSelection = prompt.suggestionSelection(),
                            paletteLevel = prompt.paletteLevel(),
                            paletteGroup = prompt.paletteGroup(),
                            paletteCommand = prompt.paletteCommand(),
                            activeScreen = tabs.active.title,
                            activeTab = "tab ${tabs.active.id}",
                            openTabCount = tabs.snapshot().tabs.size,
                            mentionOptions = selected.options
                        )
                    }

                    fun redraw() {
                        try {
                            paintPrompt()
                        } catch (failure: Exception) {
                            ui.renderError("prompt redraw failed (${failure.javaClass.simpleName})")
                        }
                    }

                    redraw()

                    inputLoop@ while (true) {
                        if (commands.hasExited()) break@inputLoop
                        val key = keys.readKey() ?: break

                        try {
                            if (key == KeyEvent.Enter && !prompt.isPaletteGroupLevel()) {
                                if (!resolvePromptSubmission()) {
                                    redraw()
                                    continue@inputLoop
                                }
                            }

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

                                KeyEvent.CtrlV -> {
                                    val clip = clipboardService.paste()
                                    if (!clip.isNullOrBlank()) {
                                        prompt.insert(clip)
                                        redraw()
                                    }
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
                                    ui.commitPrompt(effect.text, effect.mode.name)
                                    if (effect.text.isNotBlank()) {
                                        if (router.isExitCommand(effect.text)) {
                                            router.handleInput(effect.text)
                                            break@inputLoop
                                        }
                                        commands.submit(effect.text)
                                    }
                                    redraw()
                                }

                                effect is PromptEffect.Scroll -> {
                                    ui.scrollTranscript(effect.lines)
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
                        } catch (failure: Exception) {
                            ui.renderError(
                                "input handling failed (${failure.javaClass.simpleName}): " +
                                    (failure.message ?: "unknown failure")
                            )
                            redraw()
                        }
                    }
                }
            }
        }

        ui.renderNotice("session closed")
    }

    fun runHeadless(
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
}
