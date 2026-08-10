package atropos.ast

data class AstImportResolution(
    val importPath: String,
    val status: AstImportStatus,
    val matches: List<String>,
    val expectedPathSuffixes: List<String>
)
