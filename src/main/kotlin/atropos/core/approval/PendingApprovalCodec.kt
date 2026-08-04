/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.approval

import atropos.core.security.RedactionFilter
import java.time.Instant

/**
 * The durable line format for a pending approval.
 *
 * Kept apart from the store so the wire shape can be tested without touching a
 * filesystem, and so the store owns only durability. Every field is redacted on
 * the way out and escaped on the way in: an approval record names an actor and
 * a reason, both of which can carry text this process did not author.
 */
class PendingApprovalCodec(
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    fun encode(approval: PendingApproval): String = buildString {
        field("id", approval.id)
        field("proposal", approval.proposalId)
        field("actor", approval.actor)
        field("operation", approval.operation)
        field("territory", approval.territory.joinToString(FIELD_LIST_SEPARATOR))
        field("reason", approval.reason)
        field("requestedAt", approval.requestedAt.toString())
        approval.decision?.let { decision ->
            field("approved", decision.approved.toString())
            field("decidedBy", decision.decidedBy)
            field("surface", decision.surface.name)
            field("decidedAt", decision.decidedAt.toString())
            decision.note?.let { field("note", it) }
        }
    }.trimEnd()

    fun decode(line: String): PendingApproval? {
        val fields = line.split(FIELD_SEPARATOR)
            .mapNotNull { part ->
                val key = part.substringBefore('=', "")
                if (key.isBlank()) return@mapNotNull null
                key to unescape(part.substringAfter('=', ""))
            }
            .toMap()

        val id = fields["id"]?.takeIf { it.isNotBlank() } ?: return null
        val requestedAt = fields["requestedAt"]?.let { runCatching { Instant.parse(it) }.getOrNull() }
            ?: return null

        // A record whose decision is half-written is not a decision. Reading it
        // as pending would be wrong in the safe direction only by accident, so
        // it is refused outright rather than guessed at.
        val approved = fields["approved"]?.toBooleanStrictOrNull()
        val decidedBy = fields["decidedBy"]
        val surface = fields["surface"]?.let { name -> ApprovalSurface.entries.firstOrNull { it.name == name } }
        val decidedAt = fields["decidedAt"]?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val decision = if (approved != null && decidedBy != null && surface != null && decidedAt != null) {
            ApprovalDecision(approved, decidedBy, surface, decidedAt, fields["note"])
        } else if (approved != null || decidedBy != null || surface != null || decidedAt != null) {
            return null
        } else {
            null
        }

        return PendingApproval(
            id = id,
            proposalId = fields["proposal"].orEmpty(),
            actor = fields["actor"].orEmpty(),
            operation = fields["operation"].orEmpty(),
            territory = fields["territory"].orEmpty()
                .split(FIELD_LIST_SEPARATOR)
                .filter { it.isNotBlank() },
            reason = fields["reason"].orEmpty(),
            requestedAt = requestedAt,
            decision = decision
        )
    }

    private fun StringBuilder.field(key: String, value: String) {
        append(key).append('=').append(escape(redactionFilter.redact(value))).append(FIELD_SEPARATOR)
    }

    private fun escape(value: String): String = value
        .replace("\\", "\\\\")
        .replace(FIELD_SEPARATOR, "\\u0009")
        .replace("\n", "\\n")
        .replace(FIELD_LIST_SEPARATOR, "\\u001f")

    private fun unescape(value: String): String = value
        .replace("\\u001f", FIELD_LIST_SEPARATOR)
        .replace("\\n", "\n")
        .replace("\\u0009", FIELD_SEPARATOR)
        .replace("\\\\", "\\")

    private companion object {
        const val FIELD_SEPARATOR = "\t"
        const val FIELD_LIST_SEPARATOR = ""
    }
}
