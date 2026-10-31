/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.governance

import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * ATOM 24.1: Human-in-the-Loop Agent Authorization Challenge.
 *
 * When an autonomous agent requires administrative authorization for a privileged action,
 * pauses execution and prompts the human owner explicitly. The agent must never assume,
 * fabricate, or bypass the authorization token.
 *
 * Research Directive: Cryptographic challenge-response protocols that bind approval tokens
 * to specific action hashes and execution timeouts.
 */
class DecoupledHumanInTheLoopChallengeGate(
    private val clock: () -> Instant = { Instant.now() }
) {
    data class ActionChallenge(
        val challengeId: String,
        val actionHash: String,
        val description: String,
        val createdAt: Instant,
        val expiresAt: Instant,
        var approvedSignature: String? = null
    ) {
        fun isExpired(now: Instant): Boolean = now.isAfter(expiresAt)
        fun isApproved(): Boolean = approvedSignature != null
    }

    private val pendingChallenges = ConcurrentHashMap<String, ActionChallenge>()

    fun createChallenge(actionProposal: ActionProposal, timeout: Duration = Duration.ofMinutes(10)): ActionChallenge {
        val hash = sha256("${actionProposal.proposalId}:${actionProposal.patchDiff}")
        val challenge = ActionChallenge(
            challengeId = "chal-${actionProposal.proposalId}",
            actionHash = hash,
            description = actionProposal.rationale,
            createdAt = clock(),
            expiresAt = clock().plus(timeout)
        )
        pendingChallenges[challenge.challengeId] = challenge
        return challenge
    }

    fun submitHumanApproval(challengeId: String, operatorSignature: String): Boolean {
        val challenge = pendingChallenges[challengeId] ?: return false
        if (challenge.isExpired(clock())) {
            pendingChallenges.remove(challengeId)
            return false
        }
        if (operatorSignature.isBlank() || operatorSignature.startsWith("agent-")) {
            // Agent self-approval is forbidden
            return false
        }
        challenge.approvedSignature = operatorSignature
        return true
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
