package atropos.core.agent

data class SelfHostAutonomousRunResult(
    val ok: Boolean,
    val message: String,
    val goal: SelfHostGoal?,
    val promotion: SelfHostPromotionResult?,
    val evidenceBundle: SelfHostEvidenceBundleResult?,
    val steps: List<String>
)
