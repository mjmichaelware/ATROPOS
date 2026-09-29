/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

/**
 * F-CLI-003: Multi-level thinking filter (L1/L2/L3).
 *
 * The engine stores full thinking depth; the UI filter controls what the
 * operator sees. L1 = outline only, L2 = outline + key steps, L3 = full depth.
 * Independent of Web/Android verbosity channels.
 */
enum class ThinkingLevel(val label: String, val depth: Int) {
    L1("L1", 1),
    L2("L2", 2),
    L3("L3", 3)
}

class ThinkingFilter(
    private var currentLevel: ThinkingLevel = ThinkingLevel.L1
) {
    var onLevelChange: (ThinkingLevel) -> Unit = {}

    val current: ThinkingLevel
        get() = currentLevel

    fun setLevel(level: ThinkingLevel) {
        if (level != currentLevel) {
            currentLevel = level
            onLevelChange(level)
        }
    }

    fun cycle(): ThinkingLevel {
        val levels = ThinkingLevel.values()
        val nextIndex = (currentLevel.ordinal + 1) % levels.size
        setLevel(levels[nextIndex])
        return currentLevel
    }

    fun filter(lines: List<String>, depthMarkers: List<Int>): List<String> {
        return lines.filterIndexed { index, _ ->
            val depth = depthMarkers.getOrElse(index) { 1 }
            depth <= currentLevel.depth
        }
    }

    fun currentLabel(): String = currentLevel.label
}

sealed class ThinkingEvent {
    data class Enter(val content: String, val depth: Int = 1) : ThinkingEvent()
    data class Exit : ThinkingEvent()
    data class Thought(val content: String, val depth: Int) : ThinkingEvent()
}