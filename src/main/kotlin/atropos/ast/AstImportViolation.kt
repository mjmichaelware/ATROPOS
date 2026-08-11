package atropos.ast

data class AstImportViolation(
    val rule: String,
    val imports: List<String>,
    val evidence: String,
    val remediation: String
)
