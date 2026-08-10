package atropos.core.verification

data class CompletionGateReport(
    val nodeId: String,
    val canComplete: Boolean,
    val gateResults: List<GateResult>,
    val message: String
)
