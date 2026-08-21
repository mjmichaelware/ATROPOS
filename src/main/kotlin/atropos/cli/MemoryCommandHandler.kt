/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import atropos.cli.ui.AnsiTerminalEngine
import atropos.cli.ui.StatusMemoryRenderer
import atropos.core.memory.LocalMemoryStore
import atropos.core.memory.MemoryKind

class MemoryCommandHandler(
    private val uiEngine: AnsiTerminalEngine,
    private val memoryStore: LocalMemoryStore = LocalMemoryStore(),
    private val renderer: StatusMemoryRenderer = StatusMemoryRenderer()
) {
    fun execute(tokens: List<String>): RouterOutcome {
        when (tokens.getOrNull(1)?.lowercase()) {
            "remember" -> remember(tokens)
            "search" -> uiEngine.renderBlock(renderer.renderSearch(memoryStore.search(tokens.drop(2).joinToString(" ")), uiEngine.viewportWidth))
            else -> uiEngine.renderBlock(renderer.render(uiEngine.viewportWidth))
        }
        return RouterOutcome.CONTINUE
    }

    private fun remember(tokens: List<String>) {
        val title = tokens.getOrNull(2) ?: "note"
        val body = tokens.drop(3).joinToString(" ").ifBlank { title }
        val record = memoryStore.remember(MemoryKind.NOTE, title, body, listOf("cli"))
        uiEngine.renderNotice("memory remembered: ${record.id}")
    }
}
