/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.observability

import atropos.core.journal.EventCategory

/**
 * What to look for in run history.
 *
 * Source Doc 3 §5.3 names the axes exactly: "filter by agent, provider, task,
 * file, test, error, or event type". Each is a field here, and the naming
 * follows the document rather than the storage — `agent` is what the operator
 * calls it, [ExecutionRole] is what the engine calls it, and this is where the
 * two meet.
 *
 * Every field is optional and they combine with AND. That is the shape a person
 * actually queries with: *what did the auditor do to this file*, not a boolean
 * expression. Anything richer belongs in a later layer; the cost of a query
 * language here is that every surface has to build one.
 *
 * The point of a query object rather than a lambda is that it can be matched
 * against an index entry, without the payload being read. See [HistoryIndex] —
 * that is what keeps §5.3's "without loading the entire trace into memory"
 * true rather than aspirational.
 */
data class HistoryQuery(
    /** Doc's "agent". */
    val roles: Set<ExecutionRole>? = null,
    val provider: String? = null,
    val task: String? = null,
    /** Doc's "file" — matched against the event's source. */
    val file: String? = null,
    val requirement: String? = null,
    val categories: Set<EventCategory>? = null,
    /** Doc's "error" — failures only, whatever category carried them. */
    val failuresOnly: Boolean = false,
    /** Doc's "test" — test events only. */
    val testsOnly: Boolean = false,
    val runId: String? = null,
    val sinceSequence: Long? = null,
    val untilSequence: Long? = null,
    val limit: Int = DEFAULT_LIMIT
) {
    /**
     * True when [entry] could satisfy this query.
     *
     * Deliberately evaluated against the index rather than the event: an index
     * entry is a few dozen bytes and an event may be four kilobytes, and the
     * whole point of the index is to decide without paying for the payload.
     *
     * Substring rather than equality for [provider], [task] and [file], because
     * an operator types `EventPublisher` and means
     * `src/main/kotlin/.../EventPublisher.kt`. Case-insensitive for the same
     * reason: nobody types a path's case correctly from memory.
     */
    fun matches(entry: HistoryIndexEntry): Boolean {
        if (runId != null && entry.runId != runId) return false
        if (sinceSequence != null && entry.sequence < sinceSequence) return false
        if (untilSequence != null && entry.sequence > untilSequence) return false
        if (roles != null && entry.role !in roles) return false
        if (categories != null && entry.category !in categories) return false
        if (failuresOnly && !entry.failed) return false
        if (testsOnly && entry.category != EventCategory.TEST) return false
        if (provider != null && !contains(entry.provider, provider)) return false
        if (task != null && !contains(entry.task, task)) return false
        if (file != null && !contains(entry.source, file)) return false
        if (requirement != null && !contains(entry.requirement, requirement)) return false
        return true
    }

    /** True when nothing is constrained — a query that means "everything". */
    val unconstrained: Boolean
        get() = roles == null && provider == null && task == null && file == null &&
            requirement == null && categories == null && !failuresOnly && !testsOnly &&
            sinceSequence == null && untilSequence == null

    /** A human-readable statement of what was asked, for a "why this matched" line. */
    fun describe(): String {
        if (unconstrained) return "all events"
        return buildList {
            roles?.let { add("agent in [" + it.joinToString(", ") { role -> role.canonical } + "]") }
            provider?.let { add("provider~$it") }
            task?.let { add("task~$it") }
            file?.let { add("file~$it") }
            requirement?.let { add("requirement~$it") }
            categories?.let { add("type in [" + it.joinToString(", ") { c -> c.name } + "]") }
            if (failuresOnly) add("failures only")
            if (testsOnly) add("tests only")
            sinceSequence?.let { add("from #$it") }
            untilSequence?.let { add("to #$it") }
        }.joinToString(" and ")
    }

    private fun contains(field: String?, needle: String): Boolean =
        field != null && field.contains(needle, ignoreCase = true)

    companion object {
        const val DEFAULT_LIMIT = 200

        /** Doc's "error" axis. */
        fun failures() = HistoryQuery(failuresOnly = true)

        /** Doc's "test" axis. */
        fun tests() = HistoryQuery(testsOnly = true)

        /** Doc's "agent" axis. */
        fun byAgent(role: ExecutionRole) = HistoryQuery(roles = setOf(role))

        /** Doc's "file" axis. */
        fun touching(file: String) = HistoryQuery(file = file)

        /** Everything a requirement produced, which is what an evidence view needs. */
        fun forRequirement(requirement: String) = HistoryQuery(requirement = requirement)
    }
}
