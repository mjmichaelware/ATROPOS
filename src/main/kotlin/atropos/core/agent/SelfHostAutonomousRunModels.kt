package atropos.core.agent

import atropos.core.verification.GovernedCompileGateResult

data class SelfHostAutonomousRunResult(
    val ok: Boolean,
    val message: String,
    val goal: SelfHostGoal?,
    val promotion: SelfHostPromotionResult?,
    val evidenceBundle: SelfHostEvidenceBundleResult?,
    val steps: List<String>,
    /** Null when the chain stopped before the compile gate could be reached. */
    val compileGate: GovernedCompileGateResult? = null,
    /** Null only when no goal was ever created, so there is nothing to prove. */
    val proof: SelfHostRunProof? = null
)
