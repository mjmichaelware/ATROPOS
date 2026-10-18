/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import atropos.cli.input.NumberSignMacroEngine
import atropos.cli.session.QuotaSessionTracker
import atropos.cli.ui.AnsiTerminalEngine
import atropos.cli.ui.CostPeekModalRenderer
import atropos.cli.ui.InterruptModalSurface
import atropos.cli.ui.TerminalTheme
import atropos.core.ingest.FileMentionExpander
import java.nio.file.Path

/**
 * Strict Byte-0 Prefix & Modifier Interceptor.
 *
 * Enforces at byte 0 that any input starting with /, ?, @, #, +, $, or !
 * is intercepted locally and NEVER sent to an LLM provider as natural language chat.
 */
object ByteZeroPrefixInterceptor {

    fun isInterceptableByteZero(input: String): Boolean {
        val trimmed = input.trimStart()
        if (trimmed.isEmpty()) return false
        val firstChar = trimmed[0]
        return firstChar in setOf('/', '?', '@', '#', '+', '$', '!')
    }

    fun intercept(
        input: String,
        uiEngine: AnsiTerminalEngine,
        theme: TerminalTheme,
        repoRoot: Path,
        currentProviderName: () -> String,
        tracker: QuotaSessionTracker,
        shellCommand: ShellCommandHandler
    ): RouterOutcome? {
        val trimmed = input.trimStart()
        if (trimmed.isEmpty()) return null

        when (trimmed[0]) {
            '$' -> {
                val metrics = CostPeekModalRenderer.CostMetrics(
                    totalTokensUsed = tracker.sessionTokens(),
                    promptTokens = tracker.sessionPromptTokens(),
                    completionTokens = tracker.sessionCompletionTokens(),
                    providerName = currentProviderName(),
                    requestsRemainingMinute = 60,
                    tokensRemainingMinute = 100_000,
                    costClass = "FREE_LOCAL",
                    estimatedCostUsd = tracker.sessionTokens() * 0.000002
                )
                uiEngine.renderBlock(CostPeekModalRenderer(theme).render(metrics, uiEngine.viewportWidth))
                return RouterOutcome.CONTINUE
            }

            '+' -> {
                val surface = InterruptModalSurface(theme)
                val remainder = trimmed.removePrefix("+").trim()
                return when {
                    remainder.startsWith("1") -> {
                        val payload = remainder.removePrefix("1").trim()
                        val res = surface.execute(InterruptModalSurface.ActionLevel.LEVEL1_QUEUE, payload)
                        uiEngine.renderNotice(res.statusMessage)
                        RouterOutcome.CONTINUE
                    }
                    remainder.startsWith("2") -> {
                        val res = surface.execute(InterruptModalSurface.ActionLevel.LEVEL2_INTERRUPT, "")
                        uiEngine.renderNotice(res.statusMessage)
                        RouterOutcome.CONTINUE
                    }
                    remainder.startsWith("3") -> {
                        val payload = remainder.removePrefix("3").trim()
                        val res = surface.execute(InterruptModalSurface.ActionLevel.LEVEL3_CONTEXT_SHIFT, payload)
                        uiEngine.renderNotice(res.statusMessage)
                        RouterOutcome.CONTINUE
                    }
                    else -> {
                        uiEngine.renderBlock(surface.render(0, true, uiEngine.viewportWidth))
                        RouterOutcome.CONTINUE
                    }
                }
            }

            '!' -> {
                return shellCommand.bang(trimmed)
            }

            '@' -> {
                val query = trimmed.removePrefix("@").trim()
                val expander = FileMentionExpander(repoRoot)
                val candidates = expander.listCandidates(query, 16)
                if (candidates.isEmpty()) {
                    uiEngine.renderNotice("@mentions: no files matching '@$query' in active workspace root")
                } else {
                    val lines = mutableListOf<String>()
                    lines.add("╭─ ATROPOS Workspace File Mentions (@) ──────────────────────────────╮")
                    lines.add("│ Keyboard: ↑/↓ Select  → Horizontally Expand  enter Insert  esc Close│")
                    lines.add("├────────────────────────────────────────────────────────────────────┤")
                    candidates.forEachIndexed { idx, c ->
                        val cursor = if (idx == 0) "❯ " else "  "
                        val type = if (c.isDirectory) "[DIR] " else "[FILE]"
                        val display = c.displayPath
                        val row = "│ $cursor$type ${display.padEnd(58)}│"
                        lines.add(row)
                    }
                    lines.add("╰────────────────────────────────────────────────────────────────────╯")
                    uiEngine.renderBlock(lines)
                }
                return RouterOutcome.CONTINUE
            }

            '#' -> {
                val macroEngine = NumberSignMacroEngine(repoRoot)
                val token = trimmed.split(Regex("\\s+")).first()
                val resolved = macroEngine.resolveMacro(token)
                if (resolved != null) {
                    uiEngine.renderNotice("Executing macro $token -> $resolved")
                    return null
                } else {
                    uiEngine.renderNotice("macro $token not registered; use /keybinding bind $token <action>")
                    return RouterOutcome.CONTINUE
                }
            }

            else -> return null
        }
    }
}
