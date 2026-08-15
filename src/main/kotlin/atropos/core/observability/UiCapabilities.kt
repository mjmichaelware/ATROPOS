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
        val vocab = listOf("/goal", "/schedule", "/plan", "/grill-me", "/verify", "/status")
        return vocab.filter { it.startsWith(input) }
    }
}

class FuzzyExecutionGate {
    fun requestConfirmation(inputCommand: String, matchCommand: String): Boolean {
        // Confirmation gate for fuzzy match commands
        return inputCommand == matchCommand
    }
}

data class AccessibilitySettings(
    val screenReaderLabels: Map<String, String> = emptyMap(),
    val isReducedMotionEnabled: Boolean = false,
    val isHighContrastEnabled: Boolean = false,
    val hasFocusVisibility: Boolean = true
)

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
