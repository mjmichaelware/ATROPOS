/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import java.time.Instant

/**
 * P20-20.18: Immutable audit log recording all self-improvement proposals, their evaluation, and promotion.
 */
data class AuditEvent(
    val eventId: String,
    val proposalId: String,
    val timestamp: Instant,
    val action: String,
    val agentId: String,
    val result: String,
    val cryptographicSignature: String
)

class Phase20AuditLog {
    private val events = mutableListOf<AuditEvent>()

    fun append(event: AuditEvent) {
        require(event.eventId.isNotBlank()) { "Event ID cannot be blank" }
        require(event.cryptographicSignature.isNotBlank()) { "Missing signature" }
        events.add(event)
    }

    fun getLogForProposal(proposalId: String): List<AuditEvent> {
        return events.filter { it.proposalId == proposalId }
    }
}
