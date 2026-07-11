package atropos.ast

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
    val line: Int,
    val column: Int,
    val offset: Int
)

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
    private val repoRoot: Path = Path.of(".").toAbsolutePath().normalize()
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

    private fun parseFile(path: Path): List<AstSymbol> {
        val lines = path.toFile().readLines(StandardCharsets.UTF_8)
        val packageName = lines.firstOrNull { it.trimStart().startsWith("package ") }
            ?.removePrefix("package ")
            ?.trim()
            .orEmpty()
        val imports = lines.filter { it.trimStart().startsWith("import ") }
            .map { it.removePrefix("import ").trim() }
        val symbols = mutableListOf<AstSymbol>()
        var offset = 0

        symbols += AstSymbol(
            kind = AstSymbolKind.FILE,
            name = path.fileName.toString(),
            qualifiedName = "${packageName}.${path.fileName}",
            file = path,
            packageName = packageName,
            imports = imports,
            line = 1,
            column = 1,
            offset = 0
        )

        lines.forEachIndexed { index, line ->
            val lineNumber = index + 1
            fun add(kind: AstSymbolKind, regex: Regex) {
                regex.find(line)?.let { match ->
                    val name = match.groupValues[1]
                    val qualified = if (packageName.isBlank()) name else "$packageName.$name"
                    symbols += AstSymbol(
                        kind = kind,
                        name = name,
                        qualifiedName = qualified,
                        file = path,
                        packageName = packageName,
                        imports = imports,
                        line = lineNumber,
                        column = match.range.first + 1,
                        offset = offset + match.range.first
                    )
                }
            }

            add(AstSymbolKind.CLASS, Regex("""\bclass\s+([A-Za-z_][A-Za-z0-9_]*)"""))
            add(AstSymbolKind.OBJECT, Regex("""\bobject\s+([A-Za-z_][A-Za-z0-9_]*)"""))
            add(AstSymbolKind.INTERFACE, Regex("""\binterface\s+([A-Za-z_][A-Za-z0-9_]*)"""))
            add(AstSymbolKind.FUNCTION, Regex("""\bfun\s+([A-Za-z_][A-Za-z0-9_]*)\s*\("""))
            add(AstSymbolKind.PROPERTY, Regex("""\b(?:val|var)\s+([A-Za-z_][A-Za-z0-9_]*)"""))
            offset += line.length + 1
        }
        return symbols
    }
}
