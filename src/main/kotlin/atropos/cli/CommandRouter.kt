/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import atropos.cli.commands.VerifyCommand
import atropos.cli.commands.VerifyCommandHandler
import atropos.cli.commands.AgentCommand
import atropos.cli.commands.HierarchyCommand
import atropos.cli.commands.ProjectCommandHandler
import atropos.cli.commands.SelfHostNaturalLanguageRouter
import atropos.cli.session.QuotaSessionTracker
import atropos.cli.session.SessionTabs
import atropos.cli.shell.ShellCommandRunner
import atropos.core.integration.ShellCommandIntercept
import atropos.core.integration.PipedStreamRouter
import atropos.cli.ui.AnsiTerminalEngine
import atropos.cli.ui.CommandRegistryRenderer
import atropos.cli.ui.DialogOption
import atropos.cli.ui.DialogRenderer
import atropos.cli.input.CommandRisk
import atropos.cli.input.CommandRiskCatalog
import atropos.core.AIProvider
import atropos.core.AtroposConfig
import atropos.core.ProviderFactory
import atropos.core.AtroposRepoRootLocator
import atropos.core.nl.NlEntryPipeline
import atropos.core.nl.NlSource
import atropos.core.provider.ProviderOnboardingService
import atropos.core.integration.McpHostManager

enum class RouterOutcome { CONTINUE, EXIT }

class CommandRouter(
    private val config: AtroposConfig,
    private val uiEngine: AnsiTerminalEngine,
    private val sessionTracker: QuotaSessionTracker,
    private val providerResolver: (String) -> AIProvider = { ProviderFactory(config).getProvider(it) },
    private val rateResolver: (String) -> Double = { 0.0 },
    private val verifyCommand: VerifyCommandHandler = VerifyCommand(uiEngine),
    private val shellRunner: ShellCommandRunner = ShellCommandRunner(),
    private val factoryCommandOverride: FactoryCommandHandler? = null,
    private val providerOnboarding: ProviderOnboardingService = ProviderOnboardingService(),
    private val providerDiscoveryAlreadyRefreshed: Boolean = false,
    /** The only way this router reaches DLOI: failures arrive typed, not thrown. */
    private val higZeroGuard: atropos.dloi.HigZeroGuard = atropos.dloi.HigZeroGuard(atropos.dloi.DloiService())
) {
    /** A failing command renders an error; it must not end the session. */
    private val failureBoundary = CommandFailureBoundary(uiEngine)
    /** One MCP host serves both diagnostics and interactive MCP commands. */
    private val mcpHostManager = McpHostManager(
        AtroposRepoRootLocator.resolve(),
        localOnly = config.runtime.localOnly
    )
    private val backendDoctor = BackendDoctor(config, providerOnboarding, mcpHostManager)

    private var activeProvider = providerResolver(config.runtime.defaultProvider)

    var currentProviderName: String = activeProvider.name
        private set

    private val agentCommand = AgentCommand(
        ui = uiEngine,
        config = config,
        activeProviderName = { currentProviderName },
        providerOnboarding = providerOnboarding
    )

    private val hierarchyCommand = HierarchyCommand()

    private val theme =
        atropos.cli.ui.TerminalTheme(atropos.cli.config.ConfigurationManager())

    private val dashboardRenderer = atropos.cli.ui.DashboardRenderer(theme)

    private val dialogRenderer = DialogRenderer(theme)

    private val commandRegistryRenderer = CommandRegistryRenderer(theme)

    private val shortcutsRenderer = atropos.cli.ui.ShortcutsRenderer(theme)

    private val pipelineHelpRenderer = atropos.cli.ui.PipelineHelpRenderer(theme)
    private val scavengeRenderer = atropos.cli.ui.ScavengeRenderer(theme)
    private val firstRunGuide = atropos.cli.ui.FirstRunGuide(theme)

    /**
     * Which directories an `@mention` may read from.
     *
     * The launch directory always, plus whatever the operator has granted —
     * see [atropos.core.ingest.IngestTerritory]. Resolved once and shared with
     * the completer, so the paths `@` offers are exactly the paths `@` accepts.
     */
    private val ingestTerritory = atropos.core.ingest.IngestTerritory(
        java.nio.file.Path.of(shellRunner.currentDirectory())
    )

    /** The single front door for natural language. */
    private val nlEntryPipeline = atropos.core.nl.NlEntryPipeline(
        territoryRoots = ingestTerritory.paths(),
        mentions = atropos.core.ingest.MentionResolver(
            territoryRoots = ingestTerritory.paths(),
            describeTerritory = ingestTerritory::describe
        )
    )

    /** The granted ingest roots, for the completer and for `/help`. */
    val ingestRoots: List<java.nio.file.Path> get() = ingestTerritory.paths()

    private val homeStateProvider = atropos.cli.ui.HomeStateProvider()

    private val projectCommand = ProjectCommandHandler(uiEngine)

    private val selfHostNaturalLanguageRouter = SelfHostNaturalLanguageRouter()
    private val statusCommand = StatusCommandHandler(config, uiEngine, sessionTracker, providerOnboarding = providerOnboarding)
    private val providerCommand = ProviderCommandHandler(config, uiEngine, onboarding = providerOnboarding)
    private val githubCommand = GitHubCommandHandler(config, uiEngine)
    private val sentryCommand = SentryCommandHandler(uiEngine)
    private val mcpCommand = McpCommandHandler(uiEngine, mcpHostManager)
    private val dloiCommand = DloiCommandHandler(uiEngine, higZeroGuard)
    private val shellCommand = ShellCommandHandler(uiEngine, shellRunner)
    private val pipedStreamRouter = PipedStreamRouter(shellRunner)
    private val paidCommand = PaidCommandHandler(uiEngine)
    private val memoryCommand = MemoryCommandHandler(uiEngine)
    private val ciCommand = CiCommandHandler(uiEngine)
    private val assetCommand = AssetCommandHandler(uiEngine)
    private val factoryCommand = factoryCommandOverride ?: FactoryCommandHandler(uiEngine)
    private val naturalLanguageRiskGuard = NaturalLanguageRiskGuard()
    private val fuzzyExecutionGate = atropos.core.observability.FuzzyExecutionGate()
    private var pendingRiskyNaturalLanguage: String? = null
    private val securityCommand = SecurityCommandHandler(uiEngine)
    private val keysCommand = KeysCommandHandler(uiEngine)
    private val authCommand = AuthCommandHandler(uiEngine, config = config)
    private val storageCommand = StorageCommandHandler(uiEngine)
    private val interruptCommand = InterruptCommandHandler(uiEngine)
    private val exportCommand = ExportCommandHandler(uiEngine)
    private val thinkingCommand = ThinkingCommandHandler(uiEngine)
    private val themeCommand = ThemeCommandHandler(uiEngine)
    private val testsCommand = TestsCommandHandler(uiEngine)
    private val opsCommand = OpsCommandHandler(uiEngine)
    private val routeCommand = RouteCommandHandler(uiEngine, providerOnboarding)
    private val astCommand = AstCommandHandler(uiEngine)
    private val diffCommand = DiffCommandHandler(uiEngine)
    private val historyCommand = HistoryCommandHandler(uiEngine)
    private val providerChatDispatcher = ProviderChatDispatcher(
        config = config,
        uiEngine = uiEngine,
        sessionTracker = sessionTracker,
        providerResolver = providerResolver,
        rateResolver = rateResolver,
        cwd = shellCommand::currentDirectory,
        alignmentHistory = {
            val store = atropos.core.autonomy.RewardPenaltyStore(
                storageDir = java.io.File(shellCommand.currentDirectory(), ".atropos/autonomy")
            )
            atropos.core.dopamine.AlignmentTuner.historyFrom(store)
        },
        alignmentSignal = { successful ->
            val store = atropos.core.autonomy.RewardPenaltyStore(
                storageDir = java.io.File(shellCommand.currentDirectory(), ".atropos/autonomy")
            )
            if (successful) {
                store.recordReward("operator", "provider.chat", 1.0, "provider response returned")
            } else {
                store.recordPenalty("operator", "provider.chat", 1.0, "provider dispatch failed")
            }
        },
        onboarding = providerOnboarding
    )

    init {
        // Cheap, local-only discovery. It records labels and health, never key bytes,
        // and does not make a model/network call during launch.
        val discovered = if (providerDiscoveryAlreadyRefreshed) {
            providerOnboarding.list()
        } else {
            providerOnboarding.refresh()
        }
        // Keep launch useful with zero configured providers: routing will fail
        // actionably, while the operator gets one safe environment example.
        uiEngine.renderBlock(
            providerOnboarding.render().lines().plus(
                "cascade=${discovered.filter { it.health == atropos.core.provider.CheapProviderHealth.HEALTHY && !it.disabled }"
                    .joinToString(" -> ") { it.providerId }.ifBlank { "none" }}"
            )
        )
    }
    val tabs = SessionTabs(
        initialProvider = activeProvider.name,
        initialWorkingDirectory = shellCommand.currentDirectory()
    )

    private val tabCommand = TabCommandHandler(uiEngine, tabs) { currentProviderName }

    /**
     * `/ps` — a question answered beside the running work rather than behind it.
     *
     * Deliberately not routed through the command queue: the queue exists to
     * sequence writes, and a question is a read.
     */
    private val sideConversation = SideConversationService(
        uiEngine = uiEngine,
        cascade = atropos.core.ProviderCascadeRouter(
            ProviderFactory(config),
            healthyProviderIds = { providerOnboarding.healthyProviderIds() },
            preferredProviderIds = { providerOnboarding.preferredProviderIds() },
            localOnly = { config.runtime.localOnly }
        ),
        activeProvider = { currentProviderName }
    )

    /**
     * Whether this input ends the session.
     *
     * Asked rather than pattern-matched at the call site, so the exit
     * vocabulary has one owner. [BackgroundCommandRunner] needs it because
     * exit is the one command that cannot run off the input thread: the loop
     * is blocked reading a key, and a flag set by a worker would not be seen
     * until the operator pressed something else.
     */
    fun isExitCommand(input: String): Boolean =
        input.trim().lowercase() in EXIT_COMMANDS

    internal fun lex(input: String): LexResult = CommandLexer.lex(input)

    fun handleInput(input: String): RouterOutcome {
        if (input.isBlank()) return RouterOutcome.CONTINUE

        // A pasted block can hold more than one command. Handled here rather
        // than in the lexer because the lexer's job is one command's tokens,
        // and teaching it about command boundaries would make it depend on the
        // registry. See PastedInputSplitter for why a line only starts a new
        // command when its head is a registered family.
        val pasted = atropos.cli.input.PastedInputSplitter.split(input)
        if (pasted.size > 1) {
            uiEngine.renderNotice("Running ${pasted.size} pasted commands in order.")
            for (command in pasted) {
                if (handleSingleInput(command) == RouterOutcome.EXIT) return RouterOutcome.EXIT
            }
            return RouterOutcome.CONTINUE
        }
        return handleSingleInput(pasted.single())
    }

    private fun handleSingleInput(input: String): RouterOutcome {
        if (input.isBlank()) return RouterOutcome.CONTINUE
        pendingRiskyNaturalLanguage?.let { pending ->
            when (input.trim().lowercase()) {
                "y", "yes", "confirm", "confirmed" -> {
                    pendingRiskyNaturalLanguage = null
                    return handleInput(pending)
                }
                "n", "no", "cancel" -> {
                    pendingRiskyNaturalLanguage = null
                    uiEngine.renderNotice("request cancelled before risky execution")
                    return RouterOutcome.CONTINUE
                }
                else -> {
                    uiEngine.renderNotice("verification required: reply yes to continue or no to cancel")
                    return RouterOutcome.CONTINUE
                }
            }
        }
        // Mentions are expanded before lexing, for commands too.
        //
        // `accept()` runs only on the prose path, so `/factory run implement
        // @spec.md` reached the factory as the literal string `@spec.md`: the
        // generated app was named after a directory and the document was never
        // read. A prompt that *is* a document is the main way this command is
        // meant to be used.
        //
        // Only for slash input, and only when a mention is present, so the
        // ordinary typing path is untouched.
        val trimmedForMention = input.trimStart()
        if (trimmedForMention.startsWith("/") && trimmedForMention.contains('@')) {
            val expansion = nlEntryPipeline.expandMentions(input)
            if (expansion.changed) {
                expansion.notice()?.let(uiEngine::renderNotice)
                if (expansion.text != input) return handleSingleInput(expansion.text)
            }
        }

        // Bare native commands are normalized to the canonical slash command
        // before lexing, so one route owns policy, confirmation, and evidence.
        val intercepted = ShellCommandIntercept.intercept(input.trim())
        if (intercepted != input.trim() && intercepted.startsWith("/")) {
            return handleSingleInput(intercepted)
        }
        if (looksLikeShellPipeline(input)) {
            return handlePipedInput(input)
        }
        return when (val result = lex(input)) {
            is LexResult.Error -> {
                uiEngine.renderError("lex: ${result.message}")
                RouterOutcome.CONTINUE
            }
            is LexResult.Success -> failureBoundary.guard(result.tokens.firstOrNull() ?: "command") {
                // Normalize aliases once at the command boundary. This keeps
                // every downstream handler on the canonical verb vocabulary.
                route(input, atropos.core.intent.CommandConsolidator.consolidate(result.tokens))
            }
        }
    }

    /**
     * Whether this input is a shell pipeline rather than prose containing a bar.
     *
     * A single `|` anywhere used to be enough, which made every pasted document
     * a pipeline: markdown tables are built from bars, and so is any line like
     * `auth|list|get|mutate`. Pasting a specification produced "pipeline command
     * contains shell syntax" and nothing else — the document was rejected for
     * containing punctuation.
     *
     * A pipeline is a single line, because a shell pipeline is; a document is
     * usually many. Each stage must also read as a command rather than a
     * sentence — short, and with no sentence punctuation in it. Prose that
     * happens to hold a bar fails every one of those and is left alone, which is
     * the safe direction: a missed pipeline is retried with an explicit `!`,
     * where a document eaten as one is simply lost.
     */
    private fun looksLikeShellPipeline(input: String): Boolean {
        val trimmed = input.trim()
        if (trimmed.startsWith("/") || !trimmed.contains('|')) return false
        if (trimmed.contains('\n')) return false
        val stages = trimmed.split('|').map(String::trim)
        if (stages.size < 2 || stages.any(String::isBlank)) return false
        // Every stage must open with a bare command name.
        //
        // Punctuation alone was not enough to tell the two apart:
        // `IDs: B-MCP-<SYS>-auth|list|get|mutate` carries none of it and still
        // is not a pipeline. What a command has and prose does not is a
        // lowercase program name in first position — `git`, `grep`, `./verify`.
        // A capital letter, a colon or an angle bracket there means the line is
        // describing something rather than running it.
        return stages.all { stage ->
            stage.length <= MAX_PIPELINE_STAGE_CHARS &&
                PIPELINE_STAGE_HEAD.matches(stage.substringBefore(' '))
        }
    }

    private fun handlePipedInput(input: String): RouterOutcome {
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
                // Split rather than joined with \n: renderBlock lays out one
                // list element per row, so a single embedded newline would be
                // drawn inside one row and the output would run off the canvas.
                "${result.command.joinToString(" ")} [exit=${result.exitCode}]\n${result.output}"
                    .trimEnd()
                    .lines()
            )
        }
        return RouterOutcome.CONTINUE
    }

    private fun route(original: String, tokens: List<String>): RouterOutcome {
        if (tokens.isEmpty()) return RouterOutcome.CONTINUE
        if (original.trimStart().startsWith("!")) return shellCommand.bang(original)

        // Keep direct route callers on the canonical vocabulary too. The
        // lexer path already consolidates aliases; this boundary protects
        // embedded callers that invoke route-level handling.
        val alias = atropos.core.intent.AliasResolver.resolve(tokens.first())
        val routedTokens = if (alias == null || tokens.first() == alias.keyword) {
            tokens
        } else {
            listOf(alias.keyword) + tokens.drop(1)
        }

        val first = routedTokens.first().lowercase()
        if (first in setOf("?", "/?", "help", "/help", "usage", "/usage")) {
            renderHelpPage(routedTokens.drop(1).joinToString(" "))
            return RouterOutcome.CONTINUE
        }

        if (first in setOf("/self-host", "self-host")) {
            selfHostAlias(routedTokens)
            return RouterOutcome.CONTINUE
        }

        return when (routedTokens.first().lowercase()) {
            "/exit", "/quit", "exit" -> RouterOutcome.EXIT

            "/pwd" -> shellCommand.pwd()

            "/cd" -> shellCommand.cd(tokens)

            "/ls" -> shellCommand.ls(tokens)

            "/git" -> shellCommand.git(tokens)

            "/shell" -> shellCommand.shell(tokens.drop(1))

            "/dashboard", "/home" -> {
                tabs.goHome()
                // §0.1: Home answers the six continuous questions without the
                // operator searching for them.
                uiEngine.renderBlock(
                    dashboardRenderer.render(
                        homeStateProvider.capture(activeProvider.name),
                        uiEngine.viewportWidth
                    )
                )
                uiEngine.renderDashboard(
                    activeProvider = activeProvider.name,
                    activeTab = "tab ${tabs.active.id}",
                    activeScreen = tabs.active.title,
                    openTabCount = tabs.snapshot().tabs.size
                )
                RouterOutcome.CONTINUE
            }

            "/project", "/projects" -> {
                // §2.2: every meaningful activity belongs to a project, and
                // that boundary is reachable from the terminal, not only the web.
                projectCommand.execute(tokens.drop(1))
                RouterOutcome.CONTINUE
            }

            "/pipeline" -> {
                uiEngine.renderBlock(pipelineHelpRenderer.render(uiEngine.viewportWidth))
                RouterOutcome.CONTINUE
            }

            "/start", "/first-run" -> {
                uiEngine.renderBlock(
                    firstRunGuide.render(
                        atropos.cli.FirstRunProbe(config, onboarding = providerOnboarding).progress(),
                        uiEngine.viewportWidth
                    )
                )
                RouterOutcome.CONTINUE
            }

            "/scavenge" -> {
                if (config.runtime.localOnly) {
                    uiEngine.renderNotice("scavenge: blocked by local-only mode; unset ATROPOS_LOCAL_ONLY to enable remote research")
                    return RouterOutcome.CONTINUE
                }
                // Read-only by construction. The scavenger finds work and
                // reports it; nothing in this branch can write to a repository
                // the operator does not own, because nothing downstream of it
                // writes anywhere at all.
                val owner = tokens.drop(1).firstOrNull { !it.startsWith("-") }.orEmpty()
                val result = runCatching {
                    atropos.core.scavenge.GitHubScavenger().scavenge(
                        atropos.core.scavenge.GitHubScavenger.Query(
                            owner = owner,
                            includeOthersConflicts = tokens.contains("--anyone")
                        )
                    )
                }
                result.fold(
                    onSuccess = { uiEngine.renderBlock(scavengeRenderer.render(it, uiEngine.viewportWidth)) },
                    onFailure = { uiEngine.renderNotice("scavenge: ${it.message}") }
                )
                RouterOutcome.CONTINUE
            }

            "/ps" -> sideConversation.ask(original.trim().removePrefix("/ps").trim())

            "/shortcuts", "/keys-help" -> {
                uiEngine.renderBlock(shortcutsRenderer.render(uiEngine.viewportWidth))
                RouterOutcome.CONTINUE
            }

            "/tabs" -> tabCommand.list()

            "/tab" -> tabCommand.handle(tokens.drop(1))

            "/status" -> {
                statusCommand.execute(tokens, activeProvider.name)
                RouterOutcome.CONTINUE
            }

            "/providers" -> {
                providerCommand.execute(tokens, currentProviderName)
                RouterOutcome.CONTINUE
            }

            "/github" -> {
                githubCommand.execute(tokens)
                RouterOutcome.CONTINUE
            }

            "/sentry" -> {
                sentryCommand.execute(tokens)
                RouterOutcome.CONTINUE
            }

            "/mcp" -> {
                mcpCommand.execute(tokens)
                RouterOutcome.CONTINUE
            }

            "/doctor" -> {
                uiEngine.renderBlock(backendDoctor.render())
                RouterOutcome.CONTINUE
            }

            "/agent" -> {
                announce(agentCommand.execute(tokens))
                uiEngine.updateAgentPatchState(agentCommand.lastKnownPatchId)
                RouterOutcome.CONTINUE
            }

            "/paid" -> paidCommand.execute(tokens)

            "/memory" -> memoryCommand.execute(tokens)

            "/ci" -> ciCommand.execute(tokens)

            "/assets" -> assetCommand.execute(tokens)

            "/factory" -> factoryCommand.execute(tokens)

            "/verbose", "/debug" -> {
                uiEngine.toggleVerboseExecution()
                RouterOutcome.CONTINUE
            }

            "/security" -> securityCommand.execute(tokens)

            "/keys" -> keysCommand.execute(tokens)

            "/auth" -> authCommand.execute(tokens)

            "/storage", "/gc" -> storageCommand.execute(tokens)

            "/interrupt", "/pause", "/resume" -> interruptCommand.execute(tokens)

            "/export" -> exportCommand.execute(tokens)

            "/thinking" -> thinkingCommand.execute(tokens)

            "/theme" -> themeCommand.execute(tokens)

            "/tests" -> testsCommand.execute(tokens)

            "/ops" -> opsCommand.execute(tokens)

            "/route" -> routeCommand.execute(tokens)

            "/dloi" -> {
                dloiCommand.execute(tokens)
                RouterOutcome.CONTINUE
            }

            "/diff", "/changes" -> diffCommand.execute(tokens)

            "/history", "/timeline" -> historyCommand.execute(tokens)

            "/ast" -> astCommand.execute(tokens)

            "/use" -> {
                if (tokens.size == 2 && tokens[1].lowercase() == "auto") {
                    currentProviderName = "auto"
                    uiEngine.setProvider(currentProviderName)
                    uiEngine.renderNotice("provider routing switched to auto")
                } else {
                    switchProvider(tokens)
                }
                RouterOutcome.CONTINUE
            }

            "/verify" -> {
                verifyCommand.execute(tokens)
                RouterOutcome.CONTINUE
            }

            "/director", "/territory", "/hr", "/auditor", "/custodian", "/hierarchy", "/dag" -> {
                uiEngine.renderNotice(hierarchyCommand.execute(tokens))
                RouterOutcome.CONTINUE
            }

            "/snapshot", "/inspect", "/platform", "/artifact", "/autonomous" -> {
                uiEngine.renderNotice(hierarchyCommand.execute(tokens))
                RouterOutcome.CONTINUE
            }

            "/swarm" -> {
                uiEngine.renderError("swarm endpoint is not bound")
                RouterOutcome.CONTINUE
            }

            else -> {
                if (tokens.first().startsWith("/")) {
                    val guidance = atropos.core.intent.ArgumentGuidance.getGuidance(tokens.first())
                    uiEngine.renderError(
                        buildString {
                            append("unknown command: ${tokens.first()}")
                            guidance?.let { append("\n").append(it) }
                        }
                    )
                }
                else {
                    // SUP.NL.BYTE-CANONICAL-FORM asks for canonicalization "as
                    // first stage of any NL entry point", and this is the CLI's.
                    // It runs before the risk guard on purpose: the guard
                    // matches on text, and text that has not been canonicalized
                    // can be split by a zero-width character so that it matches
                    // nothing — which is precisely how a risky request gets past
                    // a classifier that reads the raw bytes.
                    val entry = nlEntryPipeline.accept(original, atropos.core.nl.NlSource.CLI_PROMPT)
                    entry.notice()?.let(uiEngine::renderNotice)
                    val canonical = entry.envelope.canonical

                    naturalLanguageRiskGuard.classify(canonical)?.let { risk ->
                        pendingRiskyNaturalLanguage = canonical
                        renderRiskConfirmation(risk.name.lowercase(), canonical)
                        return RouterOutcome.CONTINUE
                    }
                    val selfHostTokens = selfHostNaturalLanguageRouter.route(tokens)
                    when {
                        selfHostTokens != null -> {
                            if (selfHostTokens.firstOrNull() == "/factory") {
                                factoryCommand.execute(selfHostTokens)
                            } else {
                                announce(agentCommand.execute(selfHostTokens))
                                uiEngine.updateAgentPatchState(agentCommand.lastKnownPatchId)
                            }
                        }
                        tokens.size == 1 && tokens.first().equals("ATROPOS", ignoreCase = true) -> {
                            announce(agentCommand.execute(listOf("/agent", "ask", "ATROPOS")))
                            uiEngine.updateAgentPatchState(agentCommand.lastKnownPatchId)
                        }
                        // The canonical form travels onward, not the raw
                        // input: SUP.NL.ENVELOPE-WRAP requires it, and sending
                        // the raw bytes here would mean the text the guard
                        // cleared and the text the provider sees are different.
                        //
                        // promptText(), not envelope.canonical: the canonical
                        // form is the operator's own words and nothing else, so
                        // sending it alone asked the provider a question about a
                        // document it had never been given, right after the CLI
                        // said "attached: spec.txt". The risk guard still reads
                        // the canonical form above — it classifies what the
                        // operator asked for, and an attached document is
                        // evidence, not intent.
                        else -> providerChatDispatcher.dispatch(entry.promptText(), currentProviderName)
                    }
                }
                RouterOutcome.CONTINUE
            }
        }
    }

    private companion object {
        /** Every spelling that ends the session — see [isExitCommand]. */
        val EXIT_COMMANDS = setOf("/exit", "/quit", "exit", "quit")

        /** Longer than this, a bar-separated run is prose, not a command. */
        const val MAX_PIPELINE_STAGE_CHARS = 60

        /** A program name: lowercase, and nothing a sentence would put there. */
        val PIPELINE_STAGE_HEAD = Regex("^[a-z][a-z0-9._/-]*$")
    }

    private fun switchProvider(tokens: List<String>) {
        if (tokens.size != 2 || tokens[1].isBlank()) {
            uiEngine.renderError("/use requires exactly one provider")
            return
        }
        try {
            val resolved = providerResolver(tokens[1])
            activeProvider = resolved
            currentProviderName = resolved.name
            uiEngine.setProvider(resolved.name)
            uiEngine.renderNotice("provider switched to ${resolved.name}")
        } catch (failure: RuntimeException) {
            uiEngine.renderError(failure.message ?: "provider switch failed")
        }
    }

    /**
     * Shows a refusal the handler built but never printed.
     *
     * The single boundary where an unrendered [AgentCommandOutcome.Invalid]
     * becomes visible. Handlers that render their own richer output mark
     * themselves rendered and pass through here untouched.
     */
    private fun announce(outcome: atropos.cli.commands.AgentCommandOutcome) {
        if (outcome is atropos.cli.commands.AgentCommandOutcome.Invalid && !outcome.rendered) {
            uiEngine.renderError(outcome.message)
        }
    }

    private fun selfHostAlias(tokens: List<String>) {
        val remainder = tokens.drop(1)
        val subcommand = remainder.firstOrNull()?.lowercase()
        if (subcommand in setOf("help", "usage", "?", "/?", "/help", "/usage")) {
            renderHelpPage("self-host")
            return
        }
        val translated = SelfHostAliasTranslator.translate(tokens)
        if (translated == null) {
            // Used to `return` silently. A command the operator typed that
            // produces no output at all is indistinguishable from a broken
            // terminal, and they retype it rather than reading the answer that
            // was never printed.
            uiEngine.renderError(
                "'${tokens.joinToString(" ")}' is not a self-host command. " +
                    "Try '/self-host status' or '/help self-host'."
            )
            return
        }
        /* Bare shorthand is the operator's self-build command, not a read-only status query. */
        announce(agentCommand.execute(translated))
        uiEngine.updateAgentPatchState(agentCommand.lastKnownPatchId)
    }

    /**
     * `/help [summary|full|expert] [query]`.
     *
     * `SUP.UX.HELP-GENERATOR`: "Generate help text and tab-completion from
     * registry only. No hard-coded help strings remain."
     *
     * The list of commands that used to be typed out here is gone. It had
     * already drifted -- it named five families and the registry holds many
     * more -- which is precisely the failure the atom describes: a hand-written
     * help page is correct on the day it is written and wrong from the next
     * commit onward, and nothing ever tells you which lines went stale.
     */
    private fun renderHelpPage(query: String = "") {
        val words = query.trim().split(' ').filter { it.isNotBlank() }
        val level = atropos.cli.help.HelpLevel.fromCanonical(words.firstOrNull())
        val remainder = if (level.canonical.equals(words.firstOrNull(), ignoreCase = true)) {
            words.drop(1).joinToString(" ")
        } else {
            words.joinToString(" ")
        }

        // A search keeps the search renderer, which highlights matches; only
        // the browse path is generated.
        if (remainder.isNotBlank()) {
            uiEngine.renderHelp(remainder)
            return
        }

        // `/help commands` is the whole registry, categorised, with aliases —
        // the one question the levelled help pages cannot answer, because they
        // are curated and the registry is exhaustive. An operator looking for
        // "the command that does X" needs the exhaustive list, not the guided
        // one.
        if (words.firstOrNull()?.lowercase() in setOf("commands", "palette", "all")) {
            uiEngine.renderBlock(commandRegistryRenderer.renderSlashCommands(uiEngine.viewportWidth))
            return
        }

        atropos.cli.help.HelpGenerator().render(level).forEach(uiEngine::renderNotice)
    }

    /**
     * The confirmation dialog for a risky natural-language request.
     *
     * Rendered as a modal panel rather than a notice line. Source Doc 3
     * Section A requires confirm-destructive surfaces to be opaque and
     * high-contrast for exactly this case, and a one-line notice is neither —
     * it scrolls with the transcript and reads like every other message, which
     * is how a destructive confirmation gets answered without being read.
     */
    private fun renderRiskConfirmation(risk: String, request: String) {
        uiEngine.renderBlock(
            dialogRenderer.render(
                title = "Verification required — $risk",
                options = listOf(
                    DialogOption("yes", "run: ${request.take(60)}"),
                    DialogOption("no", "cancel without running")
                ),
                // Nothing is preselected on a destructive confirmation: a
                // highlighted "yes" is an answer the operator did not give.
                selectedIndex = 1,
                terminalWidth = uiEngine.viewportWidth,
                footerHint = "type yes to continue · no to cancel"
            )
        )
    }

    /**
     * Fuzzy command rewrites are harmless for ordinary commands, but a risky
     * rewrite must use the same explicit confirmation contract as risky NL.
     */
    internal fun allowFuzzyExecution(input: String, resolved: String): Boolean {
        if (fuzzyExecutionGate.requestConfirmation(input, resolved)) return true
        if (CommandRiskCatalog.forCommand(resolved) != CommandRisk.RISKY) return true
        pendingRiskyNaturalLanguage = resolved
        renderRiskConfirmation("fuzzy command", resolved)
        return false
    }
}
