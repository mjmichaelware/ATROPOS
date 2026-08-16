/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.observability

import java.io.File

data class TabState(val activeTab: String, val scrollPosition: Int)

object TabRestorationService {
    private val store = mutableMapOf<String, TabState>()

    fun saveState(projectId: String, state: TabState) {
        store[projectId] = state
    }

    fun restoreState(projectId: String): TabState? {
        return store[projectId]
    }
}

object ResponsiveBranding {
    fun renderBrandingLogo(screenWidth: Int): String {
        return if (screenWidth < 320) "ATRO" else "ATROPOS"
    }
}

object ProgressGuard {
    private var persistedProgress: Int = 0

    fun updateProgress(value: Int) {
        persistedProgress = value
    }

    fun getProgress(): Int = persistedProgress
}

class BackgroundProcessPanel {
    private val processes = mutableListOf<String>()

    fun registerProcess(name: String) {
        processes.add(name)
    }

    fun getProcesses(): List<String> = processes.toList()
}

class EngineStateTracker {
    private var isStalled = false

    fun markStalled() { isStalled = true }
    fun clearStalled() { isStalled = false }
    fun checkStalled(): Boolean = isStalled
}

object TouchAutocomplete {
    fun getSuggestions(input: String): List<String> {
        // Prefix, not the registry's fuzzy search. On a touch keyboard the
        // operator is completing what they have already typed; offering
        // commands that merely resemble it puts the wrong one under a thumb.
        // The leading slash is optional on both sides. The prompt offers
        // suggestions for a bare token too — an operator typing `self-host`
        // means `/self-host` — so comparing the raw strings would silently
        // stop completing every command typed without it.
        val needle = input.trim().removePrefix("/")
        if (needle.isEmpty()) return emptyList()
        return atropos.cli.input.CommandRegistry.search(input)
            .map { it.command }
            .filter { it.removePrefix("/").startsWith(needle, ignoreCase = true) }
            .distinct()
            .sorted()
            .take(24)
    }
}

class FuzzyExecutionGate {
    fun requestConfirmation(inputCommand: String, matchCommand: String): Boolean {
        // Exact input is already operator-selected. A rewritten fuzzy match
        // must be confirmed by the command boundary before it executes.
        return inputCommand.trim().equals(matchCommand.trim(), ignoreCase = true)
    }
}

data class AccessibilitySettings(
    val screenReaderLabels: Map<String, String> = emptyMap(),
    val isReducedMotionEnabled: Boolean = false,
    val isHighContrastEnabled: Boolean = false,
    val hasFocusVisibility: Boolean = true
) {
    companion object {
        fun fromEnvironment(get: (String) -> String? = System::getenv): AccessibilitySettings =
            AccessibilitySettings(
                isReducedMotionEnabled = get("ATROPOS_REDUCED_MOTION") == "1",
                isHighContrastEnabled = get("ATROPOS_HIGH_CONTRAST") == "1",
                hasFocusVisibility = get("ATROPOS_FOCUS_VISIBILITY") != "0"
            )
    }

    fun label(key: String, fallback: String): String =
        screenReaderLabels[key]?.takeIf(String::isNotBlank) ?: fallback

    fun focus(text: String): String =
        if (hasFocusVisibility) "[focus] $text" else text
}

object VirtualizedLogEngine {
    fun getLogWindow(logs: List<String>, offset: Int, limit: Int): List<String> {
        if (offset >= logs.size) return emptyList()
        return logs.drop(offset).take(limit)
    }
}

object BoundedRenderingController {
    fun controlBackgroundUpdate(isActiveScreen: Boolean, updateLambda: () -> Unit): Boolean {
        if (isActiveScreen) {
            updateLambda()
            return true
        }
        return false // Ignore rendering updates for background/inactive screens
    }
}
