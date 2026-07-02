/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.session

enum class ScreenId(
    val title: String
) {
    DASHBOARD("Dashboard"),
    CHAT("Chat"),
    PROVIDERS("Providers"),
    FACTORY("Factory"),
    LOGS("Logs"),
    KEYS("Keys"),
    SHELL("Shell")
}

data class SessionTab(
    val id: Int,
    val title: String,
    val screen: ScreenId,
    val provider: String,
    val workingDirectory: String,
    val scrollback: List<String> = emptyList(),
    val promptBuffer: String = "",
    val promptCursor: Int = 0,
    val selectedSuggestion: Int = 0,
    val lastRouteDecision: String? = null
)

data class SessionTabsSnapshot(
    val tabs: List<SessionTab>,
    val activeTab: SessionTab
)

class SessionTabs(
    initialProvider: String,
    initialWorkingDirectory: String
) {
    private val tabs = mutableListOf(
        SessionTab(
            id = 1,
            title = ScreenId.DASHBOARD.title,
            screen = ScreenId.DASHBOARD,
            provider = initialProvider,
            workingDirectory = initialWorkingDirectory
        )
    )

    private var nextId = 2
    private var activeIndex = 0

    val active: SessionTab
        get() = tabs[activeIndex]

    fun snapshot(): SessionTabsSnapshot =
        SessionTabsSnapshot(
            tabs = tabs.toList(),
            activeTab = active
        )

    fun openTab(
        screen: ScreenId = active.screen,
        provider: String = active.provider,
        workingDirectory: String = active.workingDirectory,
        title: String = screen.title
    ): SessionTab {
        val tab = SessionTab(
            id = nextId++,
            title = title.trim().ifBlank { screen.title },
            screen = screen,
            provider = provider,
            workingDirectory = workingDirectory
        )

        tabs += tab
        activeIndex = tabs.lastIndex
        return tab
    }

    fun switchNext(): SessionTab {
        activeIndex = (activeIndex + 1) % tabs.size
        return active
    }

    fun switchPrev(): SessionTab {
        activeIndex = (activeIndex - 1 + tabs.size) % tabs.size
        return active
    }

    fun switchToId(id: Int): SessionTab? {
        val index = tabs.indexOfFirst { it.id == id }
        if (index < 0) return null
        activeIndex = index
        return active
    }

    fun renameTab(id: Int, title: String): Boolean {
        val cleaned = title.trim()
        if (cleaned.isBlank()) return false
        val index = tabs.indexOfFirst { it.id == id }
        if (index < 0) return false
        tabs[index] = tabs[index].copy(title = cleaned)
        return true
    }

    fun closeTab(id: Int): Boolean {
        if (tabs.size <= 1) return false
        val index = tabs.indexOfFirst { it.id == id }
        if (index < 0) return false

        tabs.removeAt(index)
        activeIndex = when {
            activeIndex > index -> activeIndex - 1
            activeIndex >= tabs.size -> tabs.lastIndex
            else -> activeIndex
        }
        return true
    }

    fun goHome(): SessionTab {
        val dashboardIndex = tabs.indexOfFirst { it.screen == ScreenId.DASHBOARD }
        if (dashboardIndex >= 0) {
            activeIndex = dashboardIndex
        } else {
            replaceActive(
                active.copy(
                    title = ScreenId.DASHBOARD.title,
                    screen = ScreenId.DASHBOARD
                )
            )
        }
        return active
    }

    fun switchTo(screen: ScreenId): SessionTab {
        replaceActive(
            active.copy(
                title = screen.title,
                screen = screen
            )
        )
        return active
    }

    fun preservePrompt(
        buffer: String,
        cursor: Int,
        selectedSuggestion: Int
    ) {
        replaceActive(
            active.copy(
                promptBuffer = buffer,
                promptCursor = cursor.coerceAtLeast(0),
                selectedSuggestion = selectedSuggestion.coerceAtLeast(0)
            )
        )
    }

    fun recordRouteDecision(summary: String?) {
        replaceActive(
            active.copy(
                lastRouteDecision = summary?.takeIf { it.isNotBlank() }
            )
        )
    }

    fun appendScrollback(line: String) {
        if (line.isBlank()) return

        val next = (active.scrollback + line).takeLast(MAX_SCROLLBACK_LINES)
        replaceActive(active.copy(scrollback = next))
    }

    private fun replaceActive(tab: SessionTab) {
        tabs[activeIndex] = tab
    }

    private companion object {
        const val MAX_SCROLLBACK_LINES = 200
    }
}
