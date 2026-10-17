/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import atropos.cli.commands.AstCommandHandler
import atropos.cli.commands.AuthCommandHandler
import atropos.cli.commands.CheckpointCommandHandler
import atropos.cli.commands.DiffCommandHandler
import atropos.cli.commands.DloiCommandHandler
import atropos.cli.commands.ExportCommandHandler
import atropos.cli.commands.HierarchyCommand
import atropos.cli.commands.HistoryCommandHandler
import atropos.cli.commands.InterruptCommandHandler
import atropos.cli.commands.KeysCommandHandler
import atropos.cli.commands.OpsCommandHandler
import atropos.cli.commands.RouteCommandHandler
import atropos.cli.commands.SecurityCommandHandler
import atropos.cli.commands.StorageCommandHandler
import atropos.cli.commands.TestsCommandHandler
import atropos.cli.commands.ThemeCommandHandler
import atropos.cli.commands.ThinkingCommandHandler
import atropos.cli.ui.AnsiTerminalEngine
import atropos.cli.ui.ClipboardService
import atropos.cli.ui.FirstRunGuide
import atropos.cli.ui.PipelineHelpRenderer
import atropos.cli.ui.ScavengeRenderer
import atropos.cli.ui.ShortcutsRenderer
import atropos.cli.ui.TerminalTheme
import atropos.core.AtroposConfig
import atropos.core.ProviderFactory
import atropos.core.provider.ProviderCascadeRouter
import atropos.core.provider.ProviderOnboardingService
import atropos.core.security.TokenIsolationVault
import atropos.dloi.HigZeroGuard

/**
 * Secondary and diagnostic command dispatcher for [CommandRouter].
 * Keeps secondary command wiring decoupled from the primary interactive loop.
 */
class CommandSecondaryDispatcher(
    private val uiEngine: AnsiTerminalEngine,
    private val config: AtroposConfig,
    private val providerOnboarding: ProviderOnboardingService,
    higZeroGuard: HigZeroGuard,
    theme: TerminalTheme,
    private val currentProviderName: () -> String,
    private val copyLastResponse: () -> String,
    private val handleInput: (String) -> RouterOutcome,
    private val handleSingleInput: (String) -> RouterOutcome
) {
    private val securityCommand = SecurityCommandHandler(uiEngine)
    private val keysCommand = KeysCommandHandler(uiEngine)
    private val authCommand = AuthCommandHandler(uiEngine, config = config)
    private val storageCommand = StorageCommandHandler(uiEngine)
    private val interruptCommand = InterruptCommandHandler(uiEngine)
    private val exportCommand = ExportCommandHandler(uiEngine)
    private val thinkingCommand = ThinkingCommandHandler(uiEngine)
    private val checkpointCommand = CheckpointCommandHandler(uiEngine)
    private val themeCommand = ThemeCommandHandler(uiEngine)
    private val testsCommand = TestsCommandHandler(uiEngine)
    private val opsCommand = OpsCommandHandler(uiEngine)
    private val routeCommand = RouteCommandHandler(uiEngine, providerOnboarding)
    private val dloiCommand = DloiCommandHandler(uiEngine, higZeroGuard)
    private val diffCommand = DiffCommandHandler(uiEngine)
    private val historyCommand = HistoryCommandHandler(uiEngine)
    private val astCommand = AstCommandHandler(uiEngine)
    private val hierarchyCommand = HierarchyCommand()
    private val clipboardService = ClipboardService()
    private val pipelineHelpRenderer = PipelineHelpRenderer(theme)
    private val scavengeRenderer = ScavengeRenderer(theme)
    private val firstRunGuide = FirstRunGuide(theme)
    private val shortcutsRenderer = ShortcutsRenderer(theme)

    private val sideConversation = SideConversationService(
        uiEngine = uiEngine,
        cascade = ProviderCascadeRouter(
            ProviderFactory({ config }),
            healthyProviderIds = { providerOnboarding.healthyProviderIds() },
            preferredProviderIds = { providerOnboarding.preferredProviderIds() },
            localOnly = { config.runtime.localOnly },
            secretReader = ProviderCascadeRouter.createSecretReader(TokenIsolationVault())
        ),
        activeProvider = currentProviderName
    )

    fun dispatch(verb: String, tokens: List<String>, original: String): RouterOutcome? = when (verb) {
        "/pipeline" -> {
            uiEngine.renderBlock(pipelineHelpRenderer.render(uiEngine.viewportWidth))
            RouterOutcome.CONTINUE
        }

        "/start", "/first-run" -> {
            uiEngine.renderBlock(
                firstRunGuide.render(
                    FirstRunProbe(config, onboarding = providerOnboarding).progress(),
                    uiEngine.viewportWidth
                )
            )
            RouterOutcome.CONTINUE
        }

        "/scavenge" -> {
            if (config.runtime.localOnly) {
                uiEngine.renderNotice("scavenge: blocked by local-only mode; unset ATROPOS_LOCAL_ONLY to enable remote research")
                RouterOutcome.CONTINUE
            } else {
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
        }

        "/ps" -> sideConversation.ask(original.trim().removePrefix("/ps").trim())

        "/shortcuts", "/keys-help" -> {
            uiEngine.renderBlock(shortcutsRenderer.render(uiEngine.viewportWidth))
            RouterOutcome.CONTINUE
        }

        "/security" -> securityCommand.execute(tokens)

        "/keys" -> keysCommand.execute(tokens)

        "/auth" -> authCommand.execute(tokens)

        "/storage", "/gc" -> storageCommand.execute(tokens)

        "/interrupt", "/pause", "/resume" -> interruptCommand.execute(tokens)

        "/export" -> exportCommand.execute(tokens)

        "/thinking" -> thinkingCommand.execute(tokens)

        "/checkpoint" -> {
            checkpointCommand.execute(tokens)
            RouterOutcome.CONTINUE
        }

        "/copy" -> {
            uiEngine.renderNotice(copyLastResponse())
            RouterOutcome.CONTINUE
        }

        "/paste" -> {
            val clip = clipboardService.paste()
            if (clip.isNullOrBlank()) {
                uiEngine.renderNotice("clipboard is empty or unavailable")
            } else {
                uiEngine.renderNotice("pasting ${clip.lines().size} line(s) from clipboard...")
                handleInput(clip)
            }
            RouterOutcome.CONTINUE
        }

        "/voice", "/speech", "/dictate" -> {
            uiEngine.renderNotice("listening for speech input (Termux:API)...")
            val spoken = clipboardService.speechToText()
            if (spoken.isNullOrBlank()) {
                uiEngine.renderNotice("speech recognition cancelled or no text received")
            } else {
                uiEngine.renderNotice("voice input: $spoken")
                handleSingleInput(spoken)
            }
            RouterOutcome.CONTINUE
        }

        "/swipe", "/compose", "/input" -> {
            uiEngine.renderNotice("opening native Android composer dialog (swipe/voice enabled)...")
            val hint = tokens.drop(1).joinToString(" ").ifBlank { "Type, swipe, or use keyboard mic..." }
            val composed = clipboardService.composeDialog("Atropos Composer (Swipe & Voice)", hint)
            if (composed.isNullOrBlank()) {
                uiEngine.renderNotice("composer dialog cancelled")
            } else {
                uiEngine.renderNotice("input received (${composed.length} chars)")
                handleInput(composed)
            }
            RouterOutcome.CONTINUE
        }

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

        else -> null
    }
}
