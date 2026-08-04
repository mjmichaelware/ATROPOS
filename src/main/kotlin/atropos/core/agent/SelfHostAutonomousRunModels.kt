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
) {
    /**
     * A positive CLI result must carry every independent promotion artifact.
     *
     * The compile gate joins the conjunction rather than replacing any part of
     * it: a run that never reached the gate has no evidence that the mutated
     * source compiles, and §0 forbids reporting that as VERIFIED.
     */
    fun isVerifiedSuccess(): Boolean =
        ok && promotion?.promoted == true && promotion?.jarSwap?.promoted == true &&
            promotion?.gateReport?.canComplete == true && evidenceBundle?.ok == true &&
            compileGate?.passed == true
}
