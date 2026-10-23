/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.telemetry

import atropos.core.EngineDomainViolationException
import atropos.core.autonomy.RewardPenaltyStore
import atropos.core.dopamine.NeuralPathwayRewardEngine
import atropos.core.phase20.AntiGamingDecision

/**
 * SD7-T1-TEL18: Anti-Gaming Fraud & Self-Verification Auditor Harvester.
 *
 * Adversarial telemetry auditor that intercepts self-ratification attempts, fake PASS echoes,
 * test verification gate alterations, and unauthorized engine-code mutations.
 * Enforces immediate abortion and hard ZERO_REWARD_FRAUD_PENALTY (-10.0).
 */
class AntiGamingFraudAndSelfVerificationAuditorHarvester(
    private val rewardStore: RewardPenaltyStore = RewardPenaltyStore(),
    private val ringBuffer: TelemetryRingBuffer? = null
) {
    /**
     * Audits proposed actions or execution outputs for fraudulent self-ratification.
     */
    fun auditProposal(
        agentId: String,
        instanceId: String,
        targetFilePath: String,
        proposedContent: String,
        isEngineRootMutation: Boolean = false
    ): AntiGamingDecision {
        val fraudReasons = mutableListOf<String>()

        if (isEngineRootMutation || targetFilePath.contains("/atropos/core/") || targetFilePath.startsWith("src/main/kotlin/atropos/")) {
            fraudReasons.add("Axiomatic domain violation: attempt to mutate immutable engine root ($targetFilePath)")
        }

        if (proposedContent.contains("assert(true)") || proposedContent.contains("assertTrue(true)") || proposedContent.contains("return 0; // fake pass")) {
            fraudReasons.add("Vacuous test bypass or self-comparing mock detected")
        }

        if (proposedContent.contains("VerifiedCompletionGate") && proposedContent.contains("fun verify(") && proposedContent.contains("return true")) {
            fraudReasons.add("Adversarial alteration of VerifiedCompletionGate binary verification logic")
        }

        if (fraudReasons.isNotEmpty()) {
            val combinedReason = fraudReasons.joinToString("; ")

            // 1. Hard -10.0 fraud penalty
            rewardStore.recordPenalty(
                agentId = agentId,
                action = "fraud.anti_gaming_violation",
                value = NeuralPathwayRewardEngine.HARD_FRAUD_PENALTY,
                reason = combinedReason
            )

            // 2. Stream unforgeable adversarial proposal to telemetry
            val payload = TestAssertionDiffRecord(
                testName = "anti_gaming_audit",
                assertionType = "FRAUD_DETECTED",
                expectedSnippet = "isolated_target_mutation",
                actualSnippet = combinedReason,
                stackTraceSnippet = proposedContent.take(1000)
            )

            val fraudRecord = FrontierSweTelemetryRecord.create(
                instanceId = instanceId,
                h0PromptLineage = "fraud-attempt-$agentId",
                payload = payload,
                rewardSignal = NeuralPathwayRewardEngine.HARD_FRAUD_PENALTY
            )
            ringBuffer?.offer(fraudRecord)

            if (isEngineRootMutation) {
                throw EngineDomainViolationException(combinedReason)
            }

            return AntiGamingDecision(passed = false, reason = combinedReason)
        }

        return AntiGamingDecision(passed = true, reason = "Proposal verified clean against fraud rules")
    }
}
