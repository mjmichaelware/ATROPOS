package atropos.ast

enum class AstImportStatus {
    LOCAL_EXACT,
    EXTERNAL,
    WILDCARD,
    AMBIGUOUS,
    UNRESOLVED
}
