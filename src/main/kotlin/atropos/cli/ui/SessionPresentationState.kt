package atropos.cli.ui

import atropos.core.visual.VisualComparison

sealed interface MetricValue {
    data class Known(val display: String) : MetricValue
    object Unknown : MetricValue

    fun text(): String = when (this) {
        is Known -> display
        Unknown -> "--"
    }
}

data class SessionPresentationState(
    val provider: String,
    val mode: String,
    val workspace: String,
    val commands: List<String>,
    val tokens: MetricValue,
    val cost: MetricValue,
    val activeOperation: String?,
    val repository: RepositoryState = RepositoryState.unknown(),
    val activeScreen: String = "Dashboard",
    val activeTab: String = "tab 1",
    val openTabCount: Int = 1,
    val activePatchId: String? = null,

    /**
     * The graph, when there is one.
     *
     * Empty means no run has produced a graph in this session, which is a
     * different thing from a graph with no nodes. The home screen draws cloth
     * for the first and the graph itself for the second, so the two must not
     * collapse into one value.
     */
    val dagNodeStates: List<DagWallpaper.NodeState> = emptyList(),

    /**
     * How settled the work is, 0.0 to 1.0, for the weave.
     *
     * Defaults to settled: an idle screen with nothing to report must not draw
     * the loose cloth that means "nothing here is researched yet".
     */
    val confidence: Double = 1.0,

    /**
     * Cost per request over the session, oldest first, for the footer's shape.
     *
     * Empty draws nothing rather than a flat line, because a flat line is a
     * claim that the cost has been steady and no readings is not that claim.
     */
    val costHistory: List<Double> = emptyList(),

    /**
     * What this install has and has not done yet, or null once it is past
     * needing to be told.
     */
    val firstRun: FirstRunGuide.Progress? = null,

    /**
     * SD7-T3-007: Zero-dependency standalone virtual pager.
     */
    val pager: VirtualPagerState = VirtualPagerState()
)

/**
 * SD7-T3-007: Standalone zero-dependency terminal pager with forward/backward
 * half-page scroll, regex search, unified-diff syntax highlighting, and non-blocking escape.
 */
data class VirtualPagerState(
    val lines: List<String> = emptyList(),
    val topIndex: Int = 0,
    val pageSize: Int = 24,
    val searchRegex: Regex? = null,
    val searchMatchIndices: List<Int> = emptyList(),
    val currentMatchPointer: Int = 0,
    val active: Boolean = false
) {
    fun scrollForwardHalfPage(): VirtualPagerState {
        val half = maxOf(1, pageSize / 2)
        val maxTop = maxOf(0, lines.size - pageSize)
        return copy(topIndex = minOf(maxTop, topIndex + half))
    }

    fun scrollBackwardHalfPage(): VirtualPagerState {
        val half = maxOf(1, pageSize / 2)
        return copy(topIndex = maxOf(0, topIndex - half))
    }

    fun search(pattern: String): VirtualPagerState {
        val regex = runCatching { Regex(pattern, RegexOption.IGNORE_CASE) }.getOrNull()
        val matches = if (regex != null) {
            lines.indices.filter { regex.containsMatchIn(lines[it]) }
        } else emptyList()
        val firstTop = matches.firstOrNull() ?: topIndex
        return copy(
            searchRegex = regex,
            searchMatchIndices = matches,
            currentMatchPointer = 0,
            topIndex = firstTop.coerceIn(0, maxOf(0, lines.size - pageSize))
        )
    }

    fun highlightDiffLine(line: String): String {
        return when {
            line.startsWith("+++") || line.startsWith("---") -> "\u001B[1m$line\u001B[0m"
            line.startsWith("+") -> "\u001B[32m$line\u001B[0m"
            line.startsWith("-") -> "\u001B[31m$line\u001B[0m"
            line.startsWith("@@") -> "\u001B[36m$line\u001B[0m"
            else -> line
        }
    }

    fun escape(): VirtualPagerState = copy(active = false)
}
