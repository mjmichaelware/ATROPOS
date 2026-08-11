package atropos.ast

data class AstLookupResult(
    val query: String,
    val matches: List<AstSymbol>
) {
    fun render(): String = buildString {
        appendLine("ast:")
        appendLine("  query: $query")
        appendLine("  matches: ${matches.size}")
        matches.forEach { symbol ->
            appendLine(
                "  ${symbol.kind.name.lowercase()} ${symbol.qualifiedName} " +
                    "file=${symbol.file.toString().replace('\\', '/')} line=${symbol.line} column=${symbol.column}"
            )
        }
    }.trimEnd()
}
