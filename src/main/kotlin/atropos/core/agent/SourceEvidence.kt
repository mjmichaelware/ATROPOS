/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.agent

/**
 * Whether a task was traced to an authoritative source section, and if not, why.
 *
 * This used to be a `String?`, where `null` meant "unresolved" and the reason
 * was discarded at the point of failure. That made an unresolved source
 * indistinguishable from a source that was never needed, which is precisely the
 * ambiguity HIG=0 exists to remove: a failed exact lookup must be an explicit,
 * explained failure rather than a silent absence.
 */
sealed interface SourceEvidence {
    data class Resolved(val provenance: String) : SourceEvidence {
        init {
            require(provenance.isNotBlank()) { "resolved source evidence requires provenance" }
        }
    }

    data class Unresolved(val reason: String) : SourceEvidence {
        init {
            require(reason.isNotBlank()) { "unresolved source evidence requires a reason" }
        }
    }

    /** The provenance when resolved, `null` otherwise. For record storage. */
    val provenanceOrNull: String?
        get() = (this as? Resolved)?.provenance

    /** One line for reports: the provenance, or `unresolved` plus the reason. */
    fun describe(redact: (String) -> String = { it }): String = when (this) {
        is Resolved -> redact(provenance)
        is Unresolved -> "unresolved (${redact(reason)})"
    }
}
