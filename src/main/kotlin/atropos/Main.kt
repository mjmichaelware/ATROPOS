/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos

import kotlin.system.exitProcess
import atropos.bridge.LocalEngineBridge
import atropos.cli.BackgroundCommandRunner
import atropos.cli.CommandRouter
import atropos.cli.RouterOutcome
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
import atropos.core.agent.SelfHostStartupContinuationService
import atropos.core.agent.AgentDaemonService
import atropos.core.auth.AuthorityBootGate
import atropos.core.recovery.RuntimeContinuitySupervisor
import atropos.core.recovery.StartupContinuationDecider
import atropos.core.recovery.ContinuityOutcome
import atropos.core.security.SecretEnrollment
import atropos.core.security.EnvironmentSecretSource
import atropos.core.security.LocalVaultSecretSource
import atropos.core.security.RedactionFilter
import java.io.FileInputStream

const val ATROPOS_HEALTH_MARKER = "ATROPOS_HEALTHY"

fun main(args: Array<String>) {
    val enrollment = SecretEnrollment(listOf(EnvironmentSecretSource(), LocalVaultSecretSource()))
        .enrollInto(RedactionFilter.defaultRegistry)
    if (enrollment.failures.isNotEmpty()) {
        println("${enrollment.evidenceLine()} status=DEGRADED")
    }

    // Answered before anything else boots.
    //
    // A version flag that needed a working config, a provider registry and an
    // authority gate to answer would be unable to report the one thing an
    // operator asks it for when something is wrong.
    if (args.firstOrNull() == "--version" || args.firstOrNull() == "-v") {
        println(atropos.core.BuildStamp.line())
        return
    }

    if (args.firstOrNull() == "--help" || args.firstOrNull() == "-h" || args.firstOrNull() == "help") {
        println(atropos.cli.help.HelpGenerator().render(atropos.cli.help.HelpLevel.SUMMARY).joinToString("\n"))
        return
    }

    if (args.firstOrNull() == "doctor" && args.drop(1).any { it == "--version" || it == "-v" }) {
        println(atropos.core.BuildStamp.line())
        return
    }

    // Answered before the config boots, like --version.
    //
    // An install too broken to start is exactly the install that needs
    // replacing, so the updater must not depend on anything the update might
    // be fixing.
    if (args.firstOrNull() == "update" || args.firstOrNull() == "--update") {
        val outcome = atropos.core.SelfUpdate().update()
        println(atropos.core.SelfUpdateText.render(outcome))
        return
    }

    if (args.firstOrNull() == "--health") {
        println(ATROPOS_HEALTH_MARKER)
        return
    }

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

        // Discovery is cheap and metadata-only. Construct one owner and pass
        // that same instance to the router; otherwise Main and CommandRouter
        // would each scan the environment during one launch.
        val providerOnboarding = atropos.core.provider.ProviderOnboardingService()
        providerOnboarding.refresh()
        ui.renderNotice(providerOnboarding.renderLaunchSummary(refresh = false))

        if (args.firstOrNull() == "--doctor" || args.firstOrNull() == "doctor") {
            atropos.cli.FirstRunDoctorRenderer(
                backendDoctor = atropos.cli.BackendDoctor(config, providerOnboarding),
                homeState = HomeStateProvider()
            ).render(config.runtime.defaultProvider).forEach(::println)
            return
        }

        // Repair is automatic; resuming is not.
        //
        // Durable state left by a previous process is still repaired before the
        // runtime serves anything — a stale lease or an unmarked crashed run is
        // worse left alone. But continuing that work used to happen here too,
        // which meant opening ATROPOS could find it already acting on something
        // from a previous session that nobody asked for now. Startup reports
        // what is resumable and stops; `/agent self-host recover` resumes it.
        // ATROPOS_AUTO_CONTINUE=1 restores the old behaviour for unattended
        // runners, where no one is present to type the command.
        // Authority documents are attested before anything can be dispatched.
        // A tampered AGENTS.md is an instruction set nobody authorised, and it
        // has to be caught here rather than noticed later in output that looks
        // subtly wrong.
        val authority = AuthorityBootGate().evaluate()
        authority.notice?.let(ui::renderNotice)
        authority.error?.let(ui::renderError)

        val continuity = RuntimeContinuitySupervisor()
        val continuityOutcome = continuity.ensureRecovered()
        continuity.startupNotice(continuityOutcome)?.let(ui::renderNotice)

        val continuation = StartupContinuationDecider()
            .decide(continuityOutcome.safeForSelfHostContinuation)
        continuation.message?.let(ui::renderNotice)
        if (continuation.continued) {
            SelfHostStartupContinuationService()
                .continueOnce(true)
                .takeIf { it.attempted }
                ?.let { result ->
                    if (result.ok) ui.renderNotice(result.message ?: "self-host continuation completed")
                    else ui.renderError(result.message ?: "self-host continuation stopped")
                }
        }

        val router = CommandRouter(
            config = config,
            uiEngine = ui,
            sessionTracker = tracker,
            rateResolver = capabilities::inputUsdPerToken,
            providerOnboarding = providerOnboarding,
            providerDiscoveryAlreadyRefreshed = true
        )

        // The engine's client-facing listener. Off unless the operator asks for
        // it: Source Doc 4 makes Web and Android clients of this engine, but a
        // port that opens on every start is a surface nobody chose to expose.
        // Loopback-bound and read-only regardless — see BridgeRoutes.
        val bridge = LocalEngineBridge.fromEnvironment { config.runtime.defaultProvider }
        bridge?.let { server ->
            if (server.start()) {
                ui.renderNotice("bridge listening on 127.0.0.1:${server.boundPort()} (read-only)")
            } else {
                ui.renderError("bridge failed to start: ${server.lastError() ?: "unknown"}")
            }
        }

        // `atropos auth accept AGENTS.md` -- argv as one command, then exit.
        //
        // This did not exist. `main` read argv for exactly two flags and threw
        // the rest away, so every `atropos <something>` booted the CLI and
        // ignored what it was asked to do. The visible consequence was a
        // deadlock in the authority gate: a changed AGENTS.md holds dispatch
        // and prints "accept it with 'atropos auth accept AGENTS.md'", and that
        // command did nothing at all. The only remedy the engine offered was
        // one it did not implement.
        //
        // Reads and writes both go through the same router the interactive
        // session uses, so a one-shot cannot do anything a typed command
        // could not, and there is no second dispatch path to keep in step.
        val oneShot = args.filterNot { it.startsWith("--") }
        if (oneShot.isNotEmpty()) {
            // Boot noise (the authority error, the continuity notice) has
            // already printed and is not this command's failure. Counting it
            // would make `atropos auth accept AGENTS.md` exit non-zero on the
            // very run that fixed the thing it was complaining about.
            ui.resetErrorCount()
            val line = oneShot.joinToString(" ").let { if (it.startsWith("/")) it else "/$it" }
            router.handleInput(line)
            exitProcess(if (ui.errorCount > 0) 1 else 0)
        }

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
    val prompt = PromptState(historyStore = CommandHistoryStore(java.nio.file.Path.of(".atropos/command-history.tsv")))
    // The router's granted roots, not the workspace: the completer has to
    // offer exactly the paths the resolver would accept, or `@` suggests
    // nothing for a file it would have read.
    val completer = CommandCompleter(router.ingestRoots)
    val tabs = router.tabs

    // Commands run here, not on the key loop.
    //
    // `router.handleInput()` used to be called straight from the loop, so the
    // loop sat inside a self-host run for minutes and read no keys at all —
    // the composer drew its border and its caret and swallowed everything
    // typed into it. onIdle repaints when a run finishes, so the prompt is
    // current again without waiting for the next keystroke.
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

                // The tab bar's contents, every repaint. ViewportLayout has
                // always accepted a tab list and always been handed the empty
                // default, so the bar rendered zero rows: tabs existed, `/tabs`
                // switched between them, and none of it was visible.
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
                    // The completer already walked the granted roots for this
                    // keystroke; the panel shows what it found rather than
                    // walking them a second time.
                    mentionOptions = selected.options
                )
            }

            // The prompt is repainted on every keystroke, so a render failure
            // used to end the session: the exception unwound past the input
            // loop into main's catch, whose finally runs cleanup and lets the
            // process exit. Pasting a long line is what exposed it, because
            // buffer-width and completion arithmetic only get interesting once
            // the line is larger than the canvas. A repaint that cannot draw is
            // a cosmetic failure, never a reason to drop the operator back to
            // the shell. Error is rethrown: OutOfMemory and StackOverflow mean
            // the runtime is no longer trustworthy and must not be swallowed.
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

                // Only two things may end this session: end of input, and an
                // explicit exit. Everything else that can go wrong while
                // handling a keystroke — completion, prompt state, rendering,
                // command dispatch — is reported and the loop continues. Before
                // this, any one of them unwound into main's catch and the
                // process exited, which reads to an operator as the tool
                // quitting on its own.
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
                            // Exit runs here, everything else goes to the
                            // worker. The loop is blocked reading a key, so a
                            // worker that merely set an "exited" flag would
                            // not be noticed until the operator pressed
                            // something else — quitting would look like a hang.
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
