/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

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
    val firstRun: FirstRunGuide.Progress? = null
)
