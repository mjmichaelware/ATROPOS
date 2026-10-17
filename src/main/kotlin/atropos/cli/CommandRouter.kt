/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import atropos.cli.commands.AgentCommand
import atropos.cli.commands.AssetCommandHandler
import atropos.cli.commands.CiCommandHandler
import atropos.cli.commands.FactoryCommandHandler
import atropos.cli.commands.GitHubCommandHandler
import atropos.cli.commands.McpCommandHandler
import atropos.cli.commands.MemoryCommandHandler
import atropos.cli.commands.PaidCommandHandler
import atropos.cli.commands.ProjectCommandHandler
import atropos.cli.commands.ProviderCommandHandler
import atropos.cli.commands.SentryCommandHandler
import atropos.cli.commands.ShellCommandHandler
import atropos.cli.commands.StatusCommandHandler
import atropos.cli.commands.TabCommandHandler
import atropos.cli.commands.VerifyCommand
import atropos.cli.commands.VerifyCommandHandler
import atropos.cli.session.QuotaSessionTracker
import atropos.cli.session.SessionTabs
import atropos.cli.shell.ShellCommandRunner
import atropos.cli.ui.AnsiTerminalEngine
import atropos.cli.ui.ClipboardService
import atropos.cli.ui.CommandRegistryRenderer
import atropos.cli.ui.DashboardRenderer
import atropos.cli.ui.DialogRenderer
import atropos.cli.ui.HomeStateProvider
import atropos.cli.ui.TerminalTheme
import atropos.cli.ui.TranscriptBuffer
import atropos.core.AIProvider
import atropos.core.AtroposConfig
import atropos.core.AtroposRepoRootLocator
import atropos.core.ProviderFactory
import atropos.core.integration.McpHostManager
import atropos.core.integration.PipedStreamRouter
import atropos.core.integration.ShellCommandIntercept
import atropos.core.observability.FuzzyExecutionGate
import atropos.core.provider.ProviderOnboardingService
import java.nio.file.Path

enum class RouterOutcome { CONTINUE, EXIT }

class CommandRouter(
    private val config: AtroposConfig,
    private val uiEngine: AnsiTerminalEngine,
    private val sessionTracker: QuotaSessionTracker,
    private val providerResolver: (String) -> AIProvider = { ProviderFactory({ config }).getProvider(it) },
    private val rateResolver: (String) -> Double = { 0.0 },
    private val verifyCommand: VerifyCommandHandler = VerifyCommand(uiEngine),
    private val shellRunner: ShellCommandRunner = ShellCommandRunner(),
    private val factoryCommandOverride: FactoryCommandHandler? = null,
    private val providerOnboarding: ProviderOnboardingService = ProviderOnboardingService(),
    private val providerDiscoveryAlreadyRefreshed: Boolean = false,
    sharedMcpHostManager: McpHostManager? = null,
    /** The only way this router reaches DLOI: failures arrive typed, not thrown. */
    private val higZeroGuard: atropos.dloi.HigZeroGuard = atropos.dloi.HigZeroGuard(atropos.dloi.DloiService())
) {
    /** A failing command renders an error; it must not end the session. */
    private val failureBoundary = CommandFailureBoundary(uiEngine)

    /** One MCP host serves both diagnostics and interactive MCP commands. */
    private val mcpHostManager = sharedMcpHostManager ?: McpHostManager(
        AtroposRepoRootLocator.resolve(),
        localOnly = config.runtime.localOnly
    )
    private val backendDoctor = BackendDoctor(config, providerOnboarding, mcpHostManager)
    val preferredProviderIds = { providerOnboarding.preferredProviderIds() }

    private var activeProvider = providerResolver(config.runtime.defaultProvider)

    var currentProviderName: String = activeProvider.name
        private set

    private val agentCommand = AgentCommand(
        ui = uiEngine,
        config = config,
        activeProviderName = { currentProviderName },
        providerOnboarding = providerOnboarding
    )

    private val theme = TerminalTheme(atropos.cli.config.ConfigurationManager())
    private val dashboardRenderer = DashboardRenderer(theme)
    private val dialogRenderer = DialogRenderer(theme)
    private val commandRegistryRenderer = CommandRegistryRenderer(theme)
    private val transcriptBuffer = TranscriptBuffer()
    private val clipboardService = ClipboardService()
    private val pipedStreamRouter = PipedStreamRouter(shellRunner)
    private val fuzzyExecutionGate = FuzzyExecutionGate()

    private val promptSupport = CommandPromptSupport(
        uiEngine = uiEngine,
        dialogRenderer = dialogRenderer,
        commandRegistryRenderer = commandRegistryRenderer,
        fuzzyExecutionGate = fuzzyExecutionGate,
        transcriptBuffer = transcriptBuffer,
        clipboardService = clipboardService,
        pipedStreamRouter = pipedStreamRouter,
        agentCommand = agentCommand,
        onPatchStateUpdated = { uiEngine.updateAgentPatchState(it) }
    )

    private val firstRunProbe = FirstRunProbe(config, onboarding = providerOnboarding)
    private val authCommand = atropos.cli.commands.AuthCommandHandler(uiEngine, config = config)

    private val secondaryDispatcher = CommandSecondaryDispatcher(
        uiEngine = uiEngine,
        config = config,
        providerOnboarding = providerOnboarding,
        higZeroGuard = higZeroGuard,
        theme = theme,
        currentProviderName = { currentProviderName },
        copyLastResponse = promptSupport::copyLastResponse,
        handleInput = ::handleInput,
        handleSingleInput = ::handleSingleInput
    )

    private val ingestTerritory = atropos.core.ingest.IngestTerritory(
        Path.of(shellRunner.currentDirectory())
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
    val ingestRoots: List<Path> get() = ingestTerritory.paths()

    private val homeStateProvider = HomeStateProvider()
    private val projectCommand = ProjectCommandHandler(uiEngine)
    private val statusCommand = StatusCommandHandler(config, uiEngine, sessionTracker, providerOnboarding = providerOnboarding)
    private val providerCommand = ProviderCommandHandler(config, uiEngine, onboarding = providerOnboarding)
    private val githubCommand = GitHubCommandHandler(config, uiEngine)
    private val sentryCommand = SentryCommandHandler(uiEngine)
    private val mcpCommand = McpCommandHandler(uiEngine, mcpHostManager)
    private val shellCommand = ShellCommandHandler(uiEngine, shellRunner)
    private val paidCommand = PaidCommandHandler(uiEngine)
    private val memoryCommand = MemoryCommandHandler(uiEngine)
    private val ciCommand = CiCommandHandler(uiEngine)
    private val assetCommand = AssetCommandHandler(uiEngine)
    private val factoryCommand = factoryCommandOverride ?: FactoryCommandHandler(uiEngine)
    private val naturalLanguageRiskGuard = NaturalLanguageRiskGuard()

    private val providerChatDispatcher = ProviderChatDispatcher(
        config = config,
        uiEngine = uiEngine,
        sessionTracker = sessionTracker,
        rateResolver = rateResolver,
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
        val discovered = if (providerDiscoveryAlreadyRefreshed) {
            providerOnboarding.list()
        } else {
            providerOnboarding.refresh()
        }
        uiEngine.renderBlock(
            providerOnboarding.render().lines().plus(
                "cascade=${discovered.filter { it.health == atropos.core.provider.CheapProviderHealth.HEALTHY && !it.disabled }
                    .joinToString(" -> ") { it.providerId }.ifBlank { "none" }}"
            )
        )
    }

    val tabs = SessionTabs(
        initialProvider = activeProvider.name,
        initialWorkingDirectory = shellCommand.currentDirectory()
    )

    private val tabCommand = TabCommandHandler(uiEngine, tabs) { currentProviderName }

    fun isExitCommand(input: String): Boolean =
        input.trim().lowercase() in EXIT_COMMANDS

    internal fun lex(input: String): LexResult = CommandLexer.lex(input)

    fun handleInput(input: String): RouterOutcome {
        if (input.isBlank()) return RouterOutcome.CONTINUE

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
        promptSupport.pendingRiskyNaturalLanguage?.let { pending ->
            when (input.trim().lowercase()) {
                "y", "yes", "confirm", "confirmed" -> {
                    promptSupport.pendingRiskyNaturalLanguage = null
                    return handleInput(pending)
                }
                "n", "no", "cancel" -> {
                    promptSupport.pendingRiskyNaturalLanguage = null
                    uiEngine.renderNotice("request cancelled before risky execution")
                    return RouterOutcome.CONTINUE
                }
                else -> {
                    uiEngine.renderNotice("verification required: reply yes to continue or no to cancel")
                    return RouterOutcome.CONTINUE
                }
            }
        }

        val trimmedForMention = input.trimStart()
        if (trimmedForMention.startsWith("/") && trimmedForMention.contains('@')) {
            val expansion = nlEntryPipeline.expandMentions(input)
            if (expansion.changed) {
                expansion.notice()?.let(uiEngine::renderNotice)
                if (expansion.text != input) return handleSingleInput(expansion.text)
            }
        }

        val intercepted = ShellCommandIntercept.intercept(input.trim())
        if (intercepted != input.trim() && intercepted.startsWith("/")) {
            return handleSingleInput(intercepted)
        }
        if (promptSupport.looksLikeShellPipeline(input)) {
            return promptSupport.handlePipedInput(input)
        }
        return when (val result = lex(input)) {
            is LexResult.Error -> {
                uiEngine.renderError("lex: ${result.message}")
                RouterOutcome.CONTINUE
            }
            is LexResult.Success -> failureBoundary.guard(result.tokens.firstOrNull() ?: "command") {
                route(input, atropos.core.intent.CommandConsolidator.consolidate(result.tokens))
            }
        }
    }

    private fun route(original: String, tokens: List<String>): RouterOutcome {
        if (tokens.isEmpty()) return RouterOutcome.CONTINUE
        if (original.trimStart().startsWith("!")) return shellCommand.bang(original)

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
            promptSupport.selfHostAlias(routedTokens)
            return RouterOutcome.CONTINUE
        }

        secondaryDispatcher.dispatch(first, routedTokens, original)?.let { return it }

        return when (first) {
            "/exit", "/quit", "exit" -> RouterOutcome.EXIT

            "/pwd" -> shellCommand.pwd()

            "/cd" -> shellCommand.cd(tokens)

            "/ls" -> shellCommand.ls(tokens)

            "/git" -> shellCommand.git(tokens)

            "/shell" -> shellCommand.shell(tokens.drop(1))

            "/dashboard", "/home" -> {
                tabs.goHome()
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
                projectCommand.execute(tokens.drop(1))
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
                promptSupport.announce(agentCommand.execute(tokens))
                uiEngine.updateAgentPatchState(agentCommand.lastKnownPatchId)
                RouterOutcome.CONTINUE
            }

            "/paid" -> paidCommand.execute(tokens)

            "/memory" -> memoryCommand.execute(tokens)

            "/ci" -> ciCommand.execute(tokens)

            "/assets" -> assetCommand.execute(tokens)

            "/factory" -> factoryCommand.execute(tokens)

            "/app" -> {
                val sub = tokens.getOrNull(1)?.lowercase()
                if (sub == "factory") {
                    factoryCommand.execute(listOf("/factory") + tokens.drop(2))
                } else {
                    factoryCommand.execute(listOf("/factory") + tokens.drop(1))
                }
            }

            "/verbose", "/debug" -> {
                uiEngine.toggleVerboseExecution()
                RouterOutcome.CONTINUE
            }

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

            else -> {
                if (tokens.first().startsWith("/")) {
                    val guidance = atropos.core.intent.ArgumentGuidance.getGuidance(tokens.first())
                    uiEngine.renderError(
                        buildString {
                            append("unknown command: ${tokens.first()}")
                            guidance?.let { append("\n").append(it) }
                        }
                    )
                } else {
                    val trimmed = original.trimStart()
                    val isMentionContext = trimmed.startsWith("@")
                    if (!isMentionContext) {
                        naturalLanguageRiskGuard.classify(trimmed)?.let { risk ->
                            promptSupport.pendingRiskyNaturalLanguage = trimmed
                            promptSupport.renderRiskConfirmation(risk.name.lowercase(), trimmed)
                            return RouterOutcome.CONTINUE
                        }
                        if (tokens.size == 1 && tokens.first().equals("ATROPOS", ignoreCase = true)) {
                            promptSupport.announce(agentCommand.execute(listOf("/agent", "ask", "ATROPOS")))
                            uiEngine.updateAgentPatchState(agentCommand.lastKnownPatchId)
                        } else {
                            providerChatDispatcher.dispatch(trimmed, currentProviderName)
                        }
                        return RouterOutcome.CONTINUE
                    }
                    val entry = nlEntryPipeline.accept(original, atropos.core.nl.NlSource.CLI_PROMPT)
                    entry.notice()?.let(uiEngine::renderNotice)
                    val canonical = entry.envelope.canonical

                    naturalLanguageRiskGuard.classify(canonical)?.let { risk ->
                        promptSupport.pendingRiskyNaturalLanguage = canonical
                        promptSupport.renderRiskConfirmation(risk.name.lowercase(), canonical)
                        return RouterOutcome.CONTINUE
                    }
                    providerChatDispatcher.dispatch(entry.promptText(), currentProviderName)
                }
                RouterOutcome.CONTINUE
            }
        }
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

    fun renderHelpPage(query: String = "") {
        val words = query.trim().split(' ').filter { it.isNotBlank() }
        val level = atropos.cli.help.HelpLevel.fromCanonical(words.firstOrNull())
        promptSupport.renderHelpPage(query)
        if (words.isEmpty()) atropos.cli.help.HelpGenerator().render(level)
    }

    internal fun allowFuzzyExecution(input: String, resolved: String): Boolean =
        promptSupport.allowFuzzyExecution(input, resolved)

    private companion object {
        val EXIT_COMMANDS = setOf("/exit", "/quit", "exit", "quit")
    }
}
