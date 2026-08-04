package atropos.core.verification

data class FactoryCompletionInput(
    val nodeId: String,
    val branch: String,
    val expectedBranch: String,
    val files: List<String>,
    val verificationOutput: String,
    val auditorAllowed: Boolean,
    val promptSha256: String,
    val researchSha256: String
)
