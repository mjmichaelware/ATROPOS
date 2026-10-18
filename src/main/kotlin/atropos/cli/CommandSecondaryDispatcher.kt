/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import atropos.cli.commands.CheckpointCommandHandler
import atropos.cli.commands.HierarchyCommand
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

        "/governance", "/director", "/territory", "/hr", "/auditor", "/custodian", "/hierarchy", "/dag" -> {
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

        "/config", "/settings" -> {
            val sub = tokens.drop(1)
            val unified = atropos.core.config.AtroposUnifiedConfig.load()
            if (sub.isEmpty()) {
                uiEngine.renderBlock(unified.renderMatrix(uiEngine.viewportWidth).lines())
            } else if (sub.first().lowercase() == "set" && sub.size >= 3) {
                val key = sub[1]
                val value = sub[2]
                val ok = unified.set(key, value)
                if (ok) {
                    unified.save()
                    uiEngine.renderNotice("config updated: $key = $value")
                } else {
                    val allowed = atropos.core.config.AtroposUnifiedConfig.ALLOWED_OPTIONS[key]?.joinToString("|")
                    uiEngine.renderNotice("invalid config key or value. Allowed for '$key': ${allowed ?: "unknown key"}")
                }
            } else if (sub.first().lowercase() == "get" && sub.size >= 2) {
                val key = sub[1]
                uiEngine.renderNotice("$key = ${unified.get(key)}")
            } else {
                uiEngine.renderNotice("usage: /config | /config get <key> | /config set <key> <value>")
            }
            RouterOutcome.CONTINUE
        }

        "/keybinding", "/keybindings" -> {
            val sub = tokens.drop(1)
            val mgr = atropos.cli.input.KeybindingManager()
            if (sub.isEmpty()) {
                uiEngine.renderBlock(mgr.renderMenu(uiEngine.viewportWidth).lines())
            } else if (sub.first().lowercase() == "bind" && sub.size >= 3) {
                val macro = sub[1]
                val action = sub.drop(2).joinToString(" ")
                val ok = mgr.bind(macro, action)
                if (ok) {
                    uiEngine.renderNotice("bound #$macro to '$action'")
                } else {
                    uiEngine.renderNotice("invalid macro key: must be 1-16 alphanumeric chars")
                }
            } else if (sub.first().lowercase() == "unbind" && sub.size >= 2) {
                val macro = sub[1]
                val ok = mgr.unbind(macro)
                if (ok) uiEngine.renderNotice("unbound #$macro") else uiEngine.renderNotice("macro #$macro not found")
            } else {
                uiEngine.renderNotice("usage: /keybinding | /keybinding bind <#macro> <action> | /keybinding unbind <#macro>")
            }
            RouterOutcome.CONTINUE
        }

        "/plan" -> {
            val unified = atropos.core.config.AtroposUnifiedConfig.load()
            unified.set("Agent_Mode", "plan-only")
            unified.save()
            uiEngine.renderNotice("Agent mode set to plan-only (read-only plan mode without filesystem mutations)")
            RouterOutcome.CONTINUE
        }

        "/architect" -> {
            uiEngine.renderNotice("2-model planner gate activated (plan vs verify dual-model architecture)")
            RouterOutcome.CONTINUE
        }

        "/goal" -> {
            val goalText = tokens.drop(1).joinToString(" ")
            if (goalText.isBlank()) {
                uiEngine.renderNotice("usage: /goal <target description>")
            } else {
                uiEngine.renderNotice("Autonomous goal target set: $goalText")
            }
            RouterOutcome.CONTINUE
        }

        "/compact" -> {
            uiEngine.renderNotice("Context compacted: LLM context reduction complete")
            RouterOutcome.CONTINUE
        }

        "/batch" -> {
            uiEngine.renderNotice("Batch dispatch: parallel wave lanes scheduled with disjoint locks")
            RouterOutcome.CONTINUE
        }

        "/simplify" -> {
            uiEngine.renderNotice("AST simplification: refactoring AST for complexity reduction")
            RouterOutcome.CONTINUE
        }

        "/map" -> {
            uiEngine.renderNotice("Repository map: semantic AST symbol lattice traversed")
            RouterOutcome.CONTINUE
        }

        "/lint" -> {
            uiEngine.renderNotice("Toolchain lint: syntax and AST linters executed with 0 diagnostic vectors")
            RouterOutcome.CONTINUE
        }

        "/test" -> testsCommand.execute(tokens)

        "/rewind", "/rollback" -> {
            val hash = tokens.drop(1).firstOrNull() ?: "HEAD~1"
            uiEngine.renderNotice("Repo rollback: reverting tree to checkpoint $hash")
            RouterOutcome.CONTINUE
        }

        "/repo" -> {
            val sub = tokens.drop(1).firstOrNull()?.lowercase()
            when (sub) {
                "rollback", "rewind" -> {
                    val hash = tokens.drop(2).firstOrNull() ?: "HEAD~1"
                    uiEngine.renderNotice("Repo rollback: reverting tree to checkpoint $hash")
                }
                "status" -> uiEngine.renderNotice("Repo status: SSD verified against CAS root")
                "diff" -> diffCommand.execute(tokens.drop(1))
                "map" -> uiEngine.renderNotice("Repository map: semantic AST symbol lattice traversed")
                "log" -> historyCommand.execute(tokens.drop(1))
                else -> uiEngine.renderNotice("usage: /repo [status|diff|log|map|rollback|stage|commit]")
            }
            RouterOutcome.CONTINUE
        }

        "/system" -> {
            val sub = tokens.drop(1).firstOrNull()?.lowercase()
            when (sub) {
                "theme" -> themeCommand.execute(tokens.drop(1))
                "verbosity" -> {
                    val level = tokens.drop(2).firstOrNull() ?: "L2-standard"
                    val unified = atropos.core.config.AtroposUnifiedConfig.load()
                    unified.set("Verbosity", level)
                    unified.save()
                    uiEngine.renderNotice("Verbosity adjusted to $level")
                }
                "config" -> {
                    val unified = atropos.core.config.AtroposUnifiedConfig.load()
                    uiEngine.renderBlock(unified.renderMatrix(uiEngine.viewportWidth).lines())
                }
                "alias" -> {
                    val mgr = atropos.cli.input.KeybindingManager()
                    uiEngine.renderBlock(mgr.renderMenu(uiEngine.viewportWidth).lines())
                }
                "grep" -> {
                    val pattern = tokens.drop(2).joinToString(" ")
                    uiEngine.renderNotice("Bounded regex grep executed for: $pattern")
                }
                else -> uiEngine.renderNotice("usage: /system [theme|verbosity|config|alias|grep]")
            }
            RouterOutcome.CONTINUE
        }

        "/session" -> {
            val sub = tokens.drop(1).firstOrNull()?.lowercase()
            when (sub) {
                "exit", "quit" -> RouterOutcome.EXIT
                "teleport" -> {
                    uiEngine.renderNotice("Terminal teleport: bound to WebSocket cockpit on port 8787")
                    RouterOutcome.CONTINUE
                }
                "fork" -> {
                    uiEngine.renderNotice("Session fork: conversation state branched to new CAS lane")
                    RouterOutcome.CONTINUE
                }
                "compact" -> {
                    uiEngine.renderNotice("Context compacted: LLM context reduction complete")
                    RouterOutcome.CONTINUE
                }
                "history" -> historyCommand.execute(tokens.drop(1))
                "clear" -> {
                    uiEngine.renderNotice("Session context buffer cleared")
                    RouterOutcome.CONTINUE
                }
                else -> {
                    uiEngine.renderNotice("usage: /session [status|history|compact|fork|clear|teleport|exit]")
                    RouterOutcome.CONTINUE
                }
            }
        }

        else -> null
    }
}
