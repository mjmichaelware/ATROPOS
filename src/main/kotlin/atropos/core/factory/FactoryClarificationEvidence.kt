package atropos.core.factory

data class FactoryClarificationEvidence(
    val answers: List<Boolean>,
    val answersSha256: String,
    val lineageSha256: String
)
