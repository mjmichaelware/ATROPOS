package atropos.core.agent

data class SelfHostAutonomousRunResult(
    val ok: Boolean,
    val message: String,
    val goal: SelfHostGoal?,
    val promotion: SelfHostPromotionResult?,
    val evidenceBundle: SelfHostEvidenceBundleResult?,
    val steps: List<String>
) {
    /** A positive CLI result must carry every independent promotion artifact. */
    fun isVerifiedSuccess(): Boolean =
        ok && promotion?.promoted == true && promotion?.jarSwap?.promoted == true &&
            promotion?.gateReport?.canComplete == true && evidenceBundle?.ok == true
}
