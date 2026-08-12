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
import atropos.cli.ui.AnsiTerminalEngine
import atropos.core.AIProvider
import atropos.core.AtroposConfig
import atropos.core.ProviderFactory

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
    /** The only way this router reaches DLOI: failures arrive typed, not thrown. */
    private val higZeroGuard: atropos.dloi.HigZeroGuard = atropos.dloi.HigZeroGuard(atropos.dloi.DloiService())
) {
    /** A failing command renders an error; it must not end the session. */
    private val failureBoundary = CommandFailureBoundary(uiEngine)

    private var activeProvider = providerResolver(config.runtime.defaultProvider)

    var currentProviderName: String = activeProvider.name
        private set

    private val agentCommand = AgentCommand(
        ui = uiEngine,
        config = config,
        activeProviderName = { currentProviderName }
    )

    private val hierarchyCommand = HierarchyCommand()

    private val theme =
        atropos.cli.ui.TerminalTheme(atropos.cli.config.ConfigurationManager())

    private val dashboardRenderer = atropos.cli.ui.DashboardRenderer(theme)

    private val homeStateProvider = atropos.cli.ui.HomeStateProvider()

    private val projectCommand = ProjectCommandHandler(uiEngine)

    private val selfHostNaturalLanguageRouter = SelfHostNaturalLanguageRouter()
    private val statusCommand = StatusCommandHandler(config, uiEngine, sessionTracker)
    private val providerCommand = ProviderCommandHandler(config, uiEngine)
    private val dloiCommand = DloiCommandHandler(uiEngine, higZeroGuard)
    private val shellCommand = ShellCommandHandler(uiEngine, shellRunner)
    private val paidCommand = PaidCommandHandler(uiEngine)
    private val memoryCommand = MemoryCommandHandler(uiEngine)
    private val ciCommand = CiCommandHandler(uiEngine)
    private val assetCommand = AssetCommandHandler(uiEngine)
    private val factoryCommand = factoryCommandOverride ?: FactoryCommandHandler(uiEngine)
    private val naturalLanguageRiskGuard = NaturalLanguageRiskGuard()
    private var pendingRiskyNaturalLanguage: String? = null
    private val securityCommand = SecurityCommandHandler(uiEngine)
    private val keysCommand = KeysCommandHandler(uiEngine)
    private val authCommand = AuthCommandHandler(uiEngine)
    private val testsCommand = TestsCommandHandler(uiEngine)
    private val opsCommand = OpsCommandHandler(uiEngine)
    private val routeCommand = RouteCommandHandler(uiEngine)
    private val astCommand = AstCommandHandler(uiEngine)
    private val providerChatDispatcher = ProviderChatDispatcher(
        config = config,
        uiEngine = uiEngine,
        sessionTracker = sessionTracker,
        providerResolver = providerResolver,
        rateResolver = rateResolver,
        cwd = shellCommand::currentDirectory
    )

    val tabs = SessionTabs(
        initialProvider = activeProvider.name,
        initialWorkingDirectory = shellCommand.currentDirectory()
    )

    private val tabCommand = TabCommandHandler(uiEngine, tabs) { currentProviderName }

    internal fun lex(input: String): LexResult = CommandLexer.lex(input)

    fun handleInput(input: String): RouterOutcome {
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
        return when (val result = lex(input)) {
            is LexResult.Error -> {
                uiEngine.renderError("lex: ${result.message}")
                RouterOutcome.CONTINUE
            }
            is LexResult.Success -> failureBoundary.guard(result.tokens.firstOrNull() ?: "command") {
                route(input, result.tokens)
            }
        }
    }

    private fun route(original: String, tokens: List<String>): RouterOutcome {
        if (tokens.isEmpty()) return RouterOutcome.CONTINUE
        if (original.trimStart().startsWith("!")) return shellCommand.bang(original)

        val first = tokens.first().lowercase()
        if (first in setOf("?", "/?", "help", "/help", "usage", "/usage")) {
            renderHelpPage(tokens.drop(1).joinToString(" "))
            return RouterOutcome.CONTINUE
        }

        if (first in setOf("/self-host", "self-host")) {
            selfHostAlias(tokens)
            return RouterOutcome.CONTINUE
        }

        return when (tokens.first().lowercase()) {
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

            "/agent" -> {
                agentCommand.execute(tokens)
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

            "/tests" -> testsCommand.execute(tokens)

            "/ops" -> opsCommand.execute(tokens)

            "/route" -> routeCommand.execute(tokens)

            "/dloi" -> {
                dloiCommand.execute(tokens)
                RouterOutcome.CONTINUE
            }

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
                if (tokens.first().startsWith("/")) uiEngine.renderError("unknown command: ${tokens.first()}")
                else {
                    naturalLanguageRiskGuard.classify(original)?.let { risk ->
                        pendingRiskyNaturalLanguage = original
                        uiEngine.renderNotice(
                            "verification required before risky NL action (${risk.name.lowercase()}); " +
                                "reply yes to continue or no to cancel"
                        )
                        return RouterOutcome.CONTINUE
                    }
                    val selfHostTokens = selfHostNaturalLanguageRouter.route(tokens)
                    when {
                        selfHostTokens != null -> {
                            if (selfHostTokens.firstOrNull() == "/factory") {
                                factoryCommand.execute(selfHostTokens)
                            } else {
                                agentCommand.execute(selfHostTokens)
                                uiEngine.updateAgentPatchState(agentCommand.lastKnownPatchId)
                            }
                        }
                        tokens.size == 1 && tokens.first().equals("ATROPOS", ignoreCase = true) -> {
                            agentCommand.execute(listOf("/agent", "ask", "ATROPOS"))
                            uiEngine.updateAgentPatchState(agentCommand.lastKnownPatchId)
                        }
                        else -> providerChatDispatcher.dispatch(original, currentProviderName)
                    }
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

    private fun selfHostAlias(tokens: List<String>) {
        val remainder = tokens.drop(1)
        val subcommand = remainder.firstOrNull()?.lowercase()
        if (subcommand in setOf("help", "usage", "?", "/?", "/help", "/usage")) {
            renderHelpPage("self-host")
            return
        }
        val translated = SelfHostAliasTranslator.translate(tokens) ?: return
        /* Bare shorthand is the operator's self-build command, not a read-only status query. */
        agentCommand.execute(translated)
        uiEngine.updateAgentPatchState(agentCommand.lastKnownPatchId)
    }

    private fun renderHelpPage(query: String = "") {
        uiEngine.renderHelp(query)
        if (query.isBlank()) {
            uiEngine.renderNotice("  /verify <narrow|wide>")
            uiEngine.renderNotice("  !<command> | /shell <command>")
            uiEngine.renderNotice("  /pwd | /cd [dir] | /ls [args] | /git status")
            uiEngine.renderNotice("  /project [list|new|show|status|objective|history]")
            uiEngine.renderNotice("  /home | /dashboard | /tabs | /tab [new <name>|<n>|rename|close|next|prev]")
        }
    }

}
