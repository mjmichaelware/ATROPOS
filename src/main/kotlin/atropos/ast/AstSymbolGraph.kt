package atropos.ast

import atropos.core.parser.KotlinDeclarationKind
import atropos.core.parser.TreeSitterGrammarBridge
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.invariantSeparatorsPathString

enum class AstSymbolKind {
    FILE,
    CLASS,
    OBJECT,
    INTERFACE,
    FUNCTION,
    PROPERTY
}

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

enum class AstImportStatus {
    LOCAL_EXACT,
    EXTERNAL,
    WILDCARD,
    AMBIGUOUS,
    UNRESOLVED
}

data class AstImportResolution(
    val importPath: String,
    val status: AstImportStatus,
    val matches: List<String>,
    val expectedPathSuffixes: List<String>
)

data class AstImportReconciliationResult(
    val file: Path,
    val packageName: String,
    val packagePathInvariantHolds: Boolean,
    val resolutions: List<AstImportResolution>
) {
    fun render(): String = buildString {
        appendLine("ast-imports:")
        appendLine("  file: ${file.invariantSeparatorsPathString}")
        appendLine("  package: $packageName")
        appendLine("  package_path_invariant: $packagePathInvariantHolds")
        resolutions.forEach { resolution ->
            appendLine(
                "  import ${resolution.importPath} status=${resolution.status.name.lowercase()} " +
                    "matches=${resolution.matches.joinToString(",").ifBlank { "none" }}"
            )
        }
    }.trimEnd()
}

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
                    "file=${symbol.file.invariantSeparatorsPathString} line=${symbol.line} column=${symbol.column}"
            )
        }
    }.trimEnd()
}

class AstSymbolGraph(
    private val repoRoot: Path = Path.of(".").toAbsolutePath().normalize(),
    private val parser: TreeSitterGrammarBridge = TreeSitterGrammarBridge()
) {
    fun build(): List<AstSymbol> {
        val sourceRoot = repoRoot.resolve("src/main/kotlin")
        if (!Files.isDirectory(sourceRoot)) return emptyList()
        return Files.walk(sourceRoot).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.extension == "kt" }
                .sorted()
                .flatMap { parseFile(it).stream() }
                .toList()
        }
    }

    fun lookup(query: String): AstLookupResult {
        val normalized = query.trim()
        val symbols = build().filter {
            it.qualifiedName == normalized ||
                it.name == normalized ||
                it.qualifiedName.contains(normalized, ignoreCase = true)
        }
        return AstLookupResult(normalized, symbols)
    }

    fun impactedByPaths(paths: List<String>): List<AstSymbol> {
        val normalized = paths.map { repoRoot.resolve(it).normalize() }.toSet()
        return build().filter { it.file.normalize() in normalized }
    }

    fun reconcileImports(path: String): AstImportReconciliationResult {
        val target = repoRoot.resolve(path).normalize()
        val symbols = build()
        val fileSymbols = symbols.filter { it.file.normalize() == target }
        require(fileSymbols.isNotEmpty()) { "unknown Kotlin source: $path" }
        val fileSymbol = fileSymbols.first { it.kind == AstSymbolKind.FILE }
        val symbolIndex = symbols
            .filter { it.kind == AstSymbolKind.CLASS || it.kind == AstSymbolKind.OBJECT || it.kind == AstSymbolKind.INTERFACE }
            .groupBy { it.qualifiedName }
        val simpleNameIndex = symbols
            .filter { it.kind == AstSymbolKind.CLASS || it.kind == AstSymbolKind.OBJECT || it.kind == AstSymbolKind.INTERFACE }
            .groupBy { it.name }
        val resolutions = fileSymbol.imports.distinct().sorted().map { importPath ->
            when {
                importPath.endsWith(".*") -> AstImportResolution(
                    importPath = importPath,
                    status = AstImportStatus.WILDCARD,
                    matches = emptyList(),
                    expectedPathSuffixes = emptyList()
                )
                isExternalImport(importPath) -> AstImportResolution(
                    importPath = importPath,
                    status = AstImportStatus.EXTERNAL,
                    matches = emptyList(),
                    expectedPathSuffixes = emptyList()
                )
                symbolIndex.containsKey(importPath) -> {
                    val matches = symbolIndex.getValue(importPath)
                    AstImportResolution(
                        importPath = importPath,
                        status = if (matches.size == 1) AstImportStatus.LOCAL_EXACT else AstImportStatus.AMBIGUOUS,
                        matches = matches.map { it.qualifiedName }.sorted(),
                        expectedPathSuffixes = matches.map { it.expectedPathSuffix }.distinct().sorted()
                    )
                }
                else -> {
                    val simpleName = importPath.substringAfterLast('.')
                    val matches = simpleNameIndex[simpleName].orEmpty()
                    AstImportResolution(
                        importPath = importPath,
                        status = when {
                            matches.isEmpty() -> AstImportStatus.UNRESOLVED
                            matches.size == 1 -> AstImportStatus.LOCAL_EXACT
                            else -> AstImportStatus.AMBIGUOUS
                        },
                        matches = matches.map { it.qualifiedName }.sorted(),
                        expectedPathSuffixes = matches.map { it.expectedPathSuffix }.distinct().sorted()
                    )
                }
            }
        }
        return AstImportReconciliationResult(
            file = target,
            packageName = fileSymbol.packageName,
            packagePathInvariantHolds = fileSymbol.packagePathInvariantHolds,
            resolutions = resolutions
        )
    }

    private fun parseFile(path: Path): List<AstSymbol> {
        val source = Files.readString(path, StandardCharsets.UTF_8)
        val tree = parser.parseTree(source)
        val relative = repoRoot.relativize(path).invariantSeparatorsPathString
        val expectedPathSuffix = expectedPathSuffix(tree.packageName, path)
        val packagePathInvariantHolds = relative.endsWith(expectedPathSuffix)
        val symbols = mutableListOf<AstSymbol>()

        symbols += AstSymbol(
            kind = AstSymbolKind.FILE,
            name = path.fileName.toString(),
            qualifiedName = "${tree.packageName}.${path.fileName}",
            file = path,
            packageName = tree.packageName,
            imports = tree.imports,
            dependencyRefs = tree.imports,
            expectedPathSuffix = expectedPathSuffix,
            packagePathInvariantHolds = packagePathInvariantHolds,
            line = 1,
            column = 1,
            offset = 0
        )

        tree.declarations.forEach { declaration ->
            val kind = when (declaration.kind) {
                KotlinDeclarationKind.CLASS -> AstSymbolKind.CLASS
                KotlinDeclarationKind.OBJECT -> AstSymbolKind.OBJECT
                KotlinDeclarationKind.INTERFACE -> AstSymbolKind.INTERFACE
                KotlinDeclarationKind.FUNCTION -> AstSymbolKind.FUNCTION
                KotlinDeclarationKind.PROPERTY -> AstSymbolKind.PROPERTY
            }
            val qualified = if (tree.packageName.isBlank()) {
                declaration.name
            } else {
                "${tree.packageName}.${declaration.name}"
            }
            symbols += AstSymbol(
                kind = kind,
                name = declaration.name,
                qualifiedName = qualified,
                file = path,
                packageName = tree.packageName,
                imports = tree.imports,
                dependencyRefs = tree.imports,
                expectedPathSuffix = expectedPathSuffix,
                packagePathInvariantHolds = packagePathInvariantHolds,
                line = declaration.line,
                column = declaration.column,
                offset = declaration.offset
            )
        }
        return symbols
    }

    private fun expectedPathSuffix(packageName: String, path: Path): String =
        if (packageName.isBlank()) {
            path.fileName.toString()
        } else {
            packageName.replace('.', '/') + "/" + path.fileName
        }

    private fun isExternalImport(importPath: String): Boolean =
        importPath.startsWith("java.") ||
            importPath.startsWith("javax.") ||
            importPath.startsWith("kotlin.") ||
            importPath.startsWith("android.") ||
            importPath.startsWith("androidx.")
}
