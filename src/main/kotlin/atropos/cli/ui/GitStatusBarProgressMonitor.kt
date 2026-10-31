/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

/**
 * ATOM VC10: STATUS BAR BACKGROUND GIT PROGRESS MONITOR
 * Path: src/main/kotlin/atropos/cli/ui/GitStatusBarProgressMonitor.kt
 *
 * Renders background Git operation indicators (fetching, indexing, rebasing, committing)
 * directly in the bottom status bar with spinning activity glyphs and sync counters,
 * keeping developers informed without modal popups.
 */
data class GitStatusMetrics(
    val branchName: String,
    val isDirty: Boolean,
    val incomingCommits: Int,
    val outgoingCommits: Int,
    val inProgressOperation: String? = null
)

class GitStatusBarProgressMonitor(
    private val showProgress: Boolean = true
) {
    private val spinnerFrames = listOf("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏")
    private var frameIndex = 0

    fun renderStatusBarWidget(metrics: GitStatusMetrics): String {
        if (!showProgress) {
            val dirty = if (metrics.isDirty) "*" else ""
            return "git:(${metrics.branchName}$dirty)"
        }

        val dirtyGlyph = if (metrics.isDirty) " ●" else ""
        val syncCounters = buildString {
            if (metrics.incomingCommits > 0) append(" ↓${metrics.incomingCommits}")
            if (metrics.outgoingCommits > 0) append(" ↑${metrics.outgoingCommits}")
        }

        val spinner = if (metrics.inProgressOperation != null) {
            val frame = spinnerFrames[frameIndex % spinnerFrames.size]
            frameIndex++
            " $frame [${metrics.inProgressOperation}]"
        } else {
            ""
        }

        return "⎇ ${metrics.branchName}$dirtyGlyph$syncCounters$spinner"
    }

    companion object {
        val defaultMonitor = GitStatusBarProgressMonitor()
    }
}
