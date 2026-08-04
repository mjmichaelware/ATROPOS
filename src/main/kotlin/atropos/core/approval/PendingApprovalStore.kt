/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.approval

import atropos.core.AtroposRepoRootLocator
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

/**
 * The durable record of what is waiting on a human.
 *
 * Append-only, for the same reason the Progress Ledger is: an approval history
 * that can be rewritten cannot answer "who released this action", and that
 * question is the entire point of recording it. A decision is a new line
 * referring to the same id, and the latest line wins on read.
 *
 * The store never decides anything. It records that policy asked, and records
 * what a human answered — the release itself belongs to the executor that owns
 * the action, so nothing here can cause an action to run.
 */
class PendingApprovalStore(
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val codec: PendingApprovalCodec = PendingApprovalCodec(),
    private val clock: () -> Instant = { Instant.now() }
) {
    private val file: Path = repoRoot.resolve(".atropos/approvals/pending.log").normalize()
    private val counter = AtomicLong(0)

    /**
     * Records that policy required a human decision, returning the record.
     *
     * Idempotent per proposal: a retried proposal must not queue a second card
     * for the same action, or an operator approving one of them would leave the
     * other pending forever.
     */
    fun record(
        proposalId: String,
        actor: String,
        operation: String,
        territory: List<String>,
        reason: String
    ): PendingApproval {
        pendingFor(proposalId)?.let { return it }
        val requestedAt = clock()
        val approval = PendingApproval(
            id = "apr-${requestedAt.toEpochMilli()}-${counter.incrementAndGet()}",
            proposalId = proposalId,
            actor = actor,
            operation = operation,
            territory = territory,
            reason = reason,
            requestedAt = requestedAt
        )
        append(approval)
        return approval
    }

    /**
     * Records a human decision.
     *
     * Refuses an unknown id and refuses to overwrite a decision already made:
     * a second answer to a settled question is either a mistake or an attempt
     * to launder one, and neither should silently replace the first.
     */
    fun decide(
        id: String,
        approved: Boolean,
        decidedBy: String,
        surface: ApprovalSurface,
        note: String? = null
    ): ApprovalOutcome {
        val existing = all().lastOrNull { it.id == id }
            ?: return ApprovalOutcome.Refused("no approval with id $id")
        if (!existing.isPending) {
            return ApprovalOutcome.Refused("approval $id was already decided and cannot be changed")
        }
        if (decidedBy.isBlank()) {
            // §20.7: a decision with no attributed decider cannot be checked
            // against the rule that a proposer may not approve itself.
            return ApprovalOutcome.Refused("an approval decision must name who made it")
        }
        val decided = existing.copy(
            decision = ApprovalDecision(approved, decidedBy, surface, clock(), note)
        )
        append(decided)
        return ApprovalOutcome.Recorded(decided)
    }

    /** Everything ever recorded, oldest first, with later lines superseding earlier ones. */
    fun all(): List<PendingApproval> {
        if (!Files.isRegularFile(file)) return emptyList()
        val byId = LinkedHashMap<String, PendingApproval>()
        return try {
            Files.readAllLines(file, StandardCharsets.UTF_8)
                .asSequence()
                .filter { it.isNotBlank() }
                .mapNotNull(codec::decode)
                .forEach { byId[it.id] = it }
            byId.values.toList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun pending(): List<PendingApproval> = all().filter { it.isPending }

    private fun pendingFor(proposalId: String): PendingApproval? =
        all().lastOrNull { it.proposalId == proposalId && it.isPending }

    private fun append(approval: PendingApproval) {
        Files.createDirectories(file.parent)
        Files.writeString(
            file,
            codec.encode(approval) + "\n",
            StandardCharsets.UTF_8,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        )
    }
}

sealed class ApprovalOutcome {
    data class Recorded(val approval: PendingApproval) : ApprovalOutcome()
    data class Refused(val reason: String) : ApprovalOutcome()
}
