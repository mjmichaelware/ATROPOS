/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.core.security.RedactionFilter

/**
 * UI-only status vocabulary for Pass 10 agent jobs. This enum exists so the renderer has a
 * fixed, known set of labels to color and align — it does not model backend job execution.
 */
enum class AgentJobStatus(val label: String) {
    QUEUED("queued"),
    PLANNING("planning"),
    PATCHING("patching"),
    APPLYING("applying"),
    VERIFYING("verifying"),
    REPAIRING("repairing"),
    PASSED("passed"),
    FAILED("failed"),
    REFUSED("refused");

    companion object {
        fun fromLabel(value: String): AgentJobStatus? =
            entries.firstOrNull { it.label.equals(value.trim(), ignoreCase = true) }
    }
}

/** UI-facing shape of one agent job. Populated by whatever wires the job backend in later; not fetched here. */
data class AgentJobSummary(
    val id: String,
    val task: String,
    val status: AgentJobStatus,
    val provider: String? = null,
    val patchId: String? = null,
    val verificationId: String? = null,
    val smokeCommand: String? = null,
    val smokeSummary: String? = null,
    val finalReport: String? = null,
    val commitProposal: String? = null,
    val nextSuggestedCommand: String? = null,
    val contextExportPath: String? = null,
    val startedAt: String? = null,
    val updatedAt: String? = null,
    val changedPathsCount: Int? = null,
    val note: String? = null
)

data class AgentJobEvent(
    val at: String,
    val status: AgentJobStatus,
    val note: String? = null
)

fun AgentJobSummary.redact(filter: RedactionFilter): AgentJobSummary = copy(
    task = filter.redact(task),
    smokeCommand = smokeCommand?.let { filter.redact(it) },
    smokeSummary = smokeSummary?.let { filter.redact(it) },
    finalReport = finalReport?.let { filter.redact(it) },
    commitProposal = commitProposal?.let { filter.redact(it) },
    nextSuggestedCommand = nextSuggestedCommand?.let { filter.redact(it) },
    note = note?.let { filter.redact(it) }
)

fun AgentJobEvent.redact(filter: RedactionFilter): AgentJobEvent = copy(
    note = note?.let { filter.redact(it) }
)
