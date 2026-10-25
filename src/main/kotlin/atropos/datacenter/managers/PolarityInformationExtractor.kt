package atropos.datacenter.managers

import java.time.Instant

/**
 * Manager 1: Polarity and Information Extractor.
 * Inspects all atom output for positive vs. negative reward / learning signals.
 * Annotates records with rationale and classification before sending to DataAuditor.
 */
class PolarityInformationExtractor {
    enum class Polarity { POSITIVE, NEGATIVE, NEUTRAL, FRAUD }

    data class PolarityAnnotation(
        val atomId: String,
        val polarity: Polarity,
        val scalarWeight: Double,
        val rationale: String,
        val invariantBreached: Boolean,
        val definitiveFixConfirmed: Boolean,
        val annotatedAt: Instant = Instant.now()
    )

    fun extractAndAnnotate(record: Map<String, Any?>): PolarityAnnotation {
        val atomId = record["atom_id"]?.toString() ?: "UNKNOWN"
        val rewardStr = record["reward"]?.toString() ?: "0.0"
        val context = record["context"]?.toString() ?: ""

        val (polarity, weight) = when {
            rewardStr.contains("-10.0") || context.contains("EngineDomainViolationException") -> 
                Polarity.FRAUD to -10.0
            rewardStr.contains("+2.0") || context.contains("permanent") || context.contains("DefinitiveFix") -> 
                Polarity.POSITIVE to 2.0
            rewardStr.startsWith("+") || rewardStr.contains("1.0") && !rewardStr.contains("-") -> 
                Polarity.POSITIVE to 1.0
            rewardStr.startsWith("-") -> 
                Polarity.NEGATIVE to -1.0
            else -> 
                Polarity.NEUTRAL to 0.0
        }

        val invariantBreached = polarity == Polarity.FRAUD || rewardStr.contains("-1.5")
        val definitiveFix = polarity == Polarity.POSITIVE && weight >= 2.0

        return PolarityAnnotation(
            atomId = atomId,
            polarity = polarity,
            scalarWeight = weight,
            rationale = "Evaluated reward '$rewardStr' against context invariants: $context",
            invariantBreached = invariantBreached,
            definitiveFixConfirmed = definitiveFix
        )
    }

    fun castVote(annotation: PolarityAnnotation): ManagerVote {
        return when (annotation.polarity) {
            Polarity.FRAUD -> ManagerVote(
                managerName = "Manager-1-PolarityExtractor",
                vote = ManagerVote.Decision.REJECT,
                confidence = 1.0,
                rationale = "Hard fraud or invariant breach detected; immediate reject."
            )
            Polarity.POSITIVE -> ManagerVote(
                managerName = "Manager-1-PolarityExtractor",
                vote = ManagerVote.Decision.APPROVE,
                confidence = 0.95,
                rationale = "Positive training signal confirmed (+${annotation.scalarWeight})."
            )
            Polarity.NEGATIVE -> ManagerVote(
                managerName = "Manager-1-PolarityExtractor",
                vote = ManagerVote.Decision.APPROVE,
                confidence = 0.85,
                rationale = "Constructive negative error signal annotated for process supervision."
            )
            Polarity.NEUTRAL -> ManagerVote(
                managerName = "Manager-1-PolarityExtractor",
                vote = ManagerVote.Decision.HOLD,
                confidence = 0.60,
                rationale = "Neutral signal requires contextual reinforcement."
            )
        }
    }
}
