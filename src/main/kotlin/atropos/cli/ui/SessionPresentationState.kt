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
    val activeTab: String = "tab 1"
)
