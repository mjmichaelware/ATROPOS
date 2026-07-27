package atropos.core.parser

enum class KotlinDeclarationKind {
    CLASS,
    OBJECT,
    INTERFACE,
    FUNCTION,
    PROPERTY
}

data class KotlinDeclaration(
    val kind: KotlinDeclarationKind,
    val name: String,
    val line: Int,
    val column: Int,
    val offset: Int
)

data class KotlinParseTree(
    val packageName: String,
    val imports: List<String>,
    val declarations: List<KotlinDeclaration>
)

class TreeSitterGrammarBridge {
    fun parseTree(code: String): KotlinParseTree {
        val lines = code.lines()
        val packageName = lines.firstOrNull { it.trimStart().startsWith("package ") }
            ?.removePrefix("package ")
            ?.trim()
            .orEmpty()
        val imports = lines.filter { it.trimStart().startsWith("import ") }
            .map { it.removePrefix("import ").trim() }

        val declarations = mutableListOf<KotlinDeclaration>()
        var offset = 0
        lines.forEachIndexed { index, line ->
            val lineNumber = index + 1
            collect(KotlinDeclarationKind.CLASS, CLASS_PATTERN, line, lineNumber, offset, declarations)
            collect(KotlinDeclarationKind.OBJECT, OBJECT_PATTERN, line, lineNumber, offset, declarations)
            collect(KotlinDeclarationKind.INTERFACE, INTERFACE_PATTERN, line, lineNumber, offset, declarations)
            collect(KotlinDeclarationKind.FUNCTION, FUNCTION_PATTERN, line, lineNumber, offset, declarations)
            collect(KotlinDeclarationKind.PROPERTY, PROPERTY_PATTERN, line, lineNumber, offset, declarations)
            offset += line.length + 1
        }

        return KotlinParseTree(
            packageName = packageName,
            imports = imports,
            declarations = declarations
        )
    }

    private fun collect(
        kind: KotlinDeclarationKind,
        pattern: Regex,
        line: String,
        lineNumber: Int,
        lineOffset: Int,
        sink: MutableList<KotlinDeclaration>
    ) {
        pattern.find(line)?.let { match ->
            sink += KotlinDeclaration(
                kind = kind,
                name = match.groupValues[1],
                line = lineNumber,
                column = match.range.first + 1,
                offset = lineOffset + match.range.first
            )
        }
    }

    private companion object {
        val CLASS_PATTERN = Regex("""\bclass\s+([A-Za-z_][A-Za-z0-9_]*)""")
        val OBJECT_PATTERN = Regex("""\bobject\s+([A-Za-z_][A-Za-z0-9_]*)""")
        val INTERFACE_PATTERN = Regex("""\binterface\s+([A-Za-z_][A-Za-z0-9_]*)""")
        val FUNCTION_PATTERN = Regex("""\bfun\s+([A-Za-z_][A-Za-z0-9_]*)\s*\(""")
        val PROPERTY_PATTERN = Regex("""\b(?:val|var)\s+([A-Za-z_][A-Za-z0-9_]*)""")
    }
}
