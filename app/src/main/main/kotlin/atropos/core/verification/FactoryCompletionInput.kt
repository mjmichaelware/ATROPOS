package atropos.core.verification

data class FactoryCompletionInput(
    val nodeId: String,
    val branch: String,
    val expectedBranch: String,
    val files: List<String>,
    val verificationOutput: String,
    val auditorAllowed: Boolean,
    val promptSha256: String,
    val researchSha256: String,
    val sourceCommitId: String = "",
    val sourceTreeSha256: String = "",
    val promptFingerprint: String = "",
    val promptSpans: String = "",
    val directorAllowed: Boolean = false,
    val proposalSha256: String = "",
    val plannedAtomIds: List<String> = emptyList(),
    val atomResearch: List<String> = emptyList(),
    val projectRoot: String = "",
    val factoryTerritory: String = "",
    val directorDecision: String = "",
    val auditorDecision: String = "",
    val auditorReportSha256: String = ""
)
