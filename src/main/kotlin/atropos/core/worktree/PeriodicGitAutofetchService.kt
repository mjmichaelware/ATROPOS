/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.worktree

import java.nio.file.Path
import java.time.Instant

/**
 * ATOM VC1: PERIODIC BACKGROUND GIT AUTOFETCH
 * Path: src/main/kotlin/atropos/core/worktree/PeriodicGitAutofetchService.kt
 *
 * Periodically queries remote Git repositories in the background to fetch updated
 * commit references. Updates incoming and outgoing commit counters in the status bar
 * without locking the active working index.
 */
enum class GitAutofetchMode(val configValue: String) {
    ALL("all"),
    CURRENT("current"),
    OFF("off");

    companion object {
        fun fromConfig(value: String?): GitAutofetchMode =
            values().firstOrNull { it.configValue.equals(value?.trim(), ignoreCase = true) } ?: CURRENT
    }
}

data class RemoteSyncStats(
    val incomingCommits: Int,
    val outgoingCommits: Int,
    val lastFetchedTime: Instant?,
    val isAhead: Boolean = outgoingCommits > 0,
    val isBehind: Boolean = incomingCommits > 0
)

class PeriodicGitAutofetchService(
    private val repoRoot: Path,
    private val mode: GitAutofetchMode = GitAutofetchMode.CURRENT,
    private val intervalSeconds: Long = 180L,
    private val clock: () -> Instant = { Instant.now() }
) {
    private var lastRun: Instant? = null
    private var cachedStats = RemoteSyncStats(0, 0, null)

    fun shouldRunAutofetch(): Boolean {
        if (mode == GitAutofetchMode.OFF) return false
        val now = clock()
        val prev = lastRun ?: return true
        return now.epochSecond - prev.epochSecond >= intervalSeconds
    }

    fun buildFetchCommand(): List<String>? {
        return when (mode) {
            GitAutofetchMode.ALL -> listOf("git", "fetch", "--all", "--prune")
            GitAutofetchMode.CURRENT -> listOf("git", "fetch", "--prune")
            GitAutofetchMode.OFF -> null
        }
    }

    fun updateSyncCounters(incoming: Int, outgoing: Int): RemoteSyncStats {
        lastRun = clock()
        cachedStats = RemoteSyncStats(
            incomingCommits = incoming,
            outgoingCommits = outgoing,
            lastFetchedTime = lastRun
        )
        return cachedStats
    }

    fun getLatestStats(): RemoteSyncStats = cachedStats

    companion object {
        fun defaultService(repoRoot: Path): PeriodicGitAutofetchService =
            PeriodicGitAutofetchService(repoRoot)
    }
}
