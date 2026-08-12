package atropos.ast

import java.nio.file.Path

data class AstSymbol(
    val kind: AstSymbolKind,
    val name: String,
    val qualifiedName: String,
    val file: Path,
    val packageName: String,
    val imports: List<String>,
    val dependencyRefs: List<String>,
    val expectedPathSuffix: String,
    val packagePathInvariantHolds: Boolean,
    val line: Int,
    val column: Int,
    val offset: Int
)
