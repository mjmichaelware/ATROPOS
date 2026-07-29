/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import atropos.cli.session.ScreenId
import atropos.cli.session.SessionTabs
import atropos.cli.ui.AnsiTerminalEngine

class TabCommandHandler(
    private val uiEngine: AnsiTerminalEngine,
    private val tabs: SessionTabs,
    private val currentProviderName: () -> String
) {
    fun list(): RouterOutcome {
        uiEngine.renderNotice(renderTabsList())
        return RouterOutcome.CONTINUE
    }

    fun handle(args: List<String>): RouterOutcome {
        if (args.isEmpty()) {
            uiEngine.renderError("usage: /tab [new <name>|<n>|rename <n> <name>|close <n>|next|prev]")
            return RouterOutcome.CONTINUE
        }

        when (val action = args[0].lowercase()) {
            "new" -> open(args.drop(1))
            "rename" -> rename(args)
            "close" -> close(args)
            "next" -> {
                tabs.switchNext()
                uiEngine.renderNotice("tab ${tabs.active.id}: ${tabs.active.title}")
            }
            "prev" -> {
                tabs.switchPrev()
                uiEngine.renderNotice("tab ${tabs.active.id}: ${tabs.active.title}")
            }
            else -> switch(action)
        }
        return RouterOutcome.CONTINUE
    }

    private fun open(nameTokens: List<String>) {
        val name = nameTokens.joinToString(" ").trim()
        if (name.isBlank()) {
            uiEngine.renderError("usage: /tab new <name>")
            return
        }
        val tab = tabs.openTab(
            screen = ScreenId.CHAT,
            provider = currentProviderName(),
            workingDirectory = tabs.active.workingDirectory,
            title = name
        )
        uiEngine.renderNotice("opened tab ${tab.id}: ${tab.title}")
    }

    private fun rename(args: List<String>) {
        val id = args.getOrNull(1)?.toIntOrNull()
        val name = args.drop(2).joinToString(" ").trim()
        if (id == null || name.isBlank()) {
            uiEngine.renderError("usage: /tab rename <n> <name>")
            return
        }
        if (tabs.renameTab(id, name)) {
            uiEngine.renderNotice("tab $id renamed to $name")
        } else {
            uiEngine.renderError("no such tab: $id")
        }
    }

    private fun close(args: List<String>) {
        val id = args.getOrNull(1)?.toIntOrNull()
        if (id == null) {
            uiEngine.renderError("usage: /tab close <n>")
            return
        }
        if (tabs.closeTab(id)) {
            uiEngine.renderNotice("closed tab $id - active tab ${tabs.active.id}: ${tabs.active.title}")
        } else {
            uiEngine.renderError("cannot close tab $id (not found, or it is the last remaining tab)")
        }
    }

    private fun switch(action: String) {
        val id = action.toIntOrNull()
        if (id == null) {
            uiEngine.renderError("usage: /tab [new <name>|<n>|rename <n> <name>|close <n>|next|prev]")
            return
        }
        val switched = tabs.switchToId(id)
        if (switched == null) {
            uiEngine.renderError("no such tab: $id")
        } else {
            uiEngine.renderNotice("tab ${switched.id}: ${switched.title}")
        }
    }

    private fun renderTabsList(): String = buildString {
        appendLine("open tabs (${tabs.snapshot().tabs.size}):")
        tabs.snapshot().tabs.forEach { tab ->
            val marker = if (tab.id == tabs.active.id) "*" else " "
            appendLine("  $marker ${tab.id}: ${tab.title}  [${tab.screen.title}]  provider=${tab.provider}")
        }
        append("commands: /tab new <name> | /tab <n> | /tab rename <n> <name> | /tab close <n> | /tab next | /tab prev | /home")
    }
}
