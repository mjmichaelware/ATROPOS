/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.artifact.export

/**
 * What can be exported.
 *
 * `SUP.ART.HANDOFF-EXPORT` names the catalog: "handoffs, reports, evidence
 * summaries, swarm status". A closed set rather than a free-text argument,
 * because the argument reaches this from a command line and an export whose
 * type was a string would eventually be an export whose type was a path.
 *
 * [filenameStem] is fixed per type and never taken from operator input. The
 * only variable part of an exported filename is the timestamp, which the
 * exporter mints. That keeps `/export report --to <somewhere>` from being a
 * way to choose an arbitrary filename in a directory ATROPOS can write to.
 */
enum class HandoffType(
    val canonical: String,
    val filenameStem: String,
    val description: String
) {
    HANDOFF(
        "handoff",
        "atropos-handoff",
        "what was done, what is next, and what the next agent needs to know"
    ),
    REPORT(
        "report",
        "atropos-report",
        "the current run's outcome in prose"
    ),
    EVIDENCE(
        "evidence",
        "atropos-evidence",
        "a summary of the evidence bundles behind the current claims"
    ),
    SWARM(
        "swarm",
        "atropos-swarm",
        "the attested topology and what each node holds"
    ),
    AUDIT(
        "audit",
        "atropos-audit",
        "auditor findings, including the ones that blocked promotion"
    );

    companion object {
        fun fromCanonical(term: String): HandoffType? =
            entries.firstOrNull { it.canonical.equals(term.trim(), ignoreCase = true) }

        fun catalog(): String =
            entries.joinToString("\n") { "  ${it.canonical.padEnd(9)} ${it.description}" }
    }
}
