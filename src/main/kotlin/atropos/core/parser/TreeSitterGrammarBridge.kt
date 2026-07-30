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
        val lines = KotlinLexicalMasker.maskNonCode(code).lines()
        val packageName = lines.firstOrNull { it.trimStart().startsWith("package ") }
            ?.trim()
            ?.removePrefix("package ")
            ?.trim()
            .orEmpty()
        val imports = lines.filter { it.trimStart().startsWith("import ") }
            .map { it.trim().removePrefix("import ").trim() }

        val declarations = mutableListOf<KotlinDeclaration>()
        var offset = 0
        lines.forEachIndexed { index, line ->
            val lineNumber = index + 1
            val searchable = line
            collect(KotlinDeclarationKind.CLASS, CLASS_PATTERN, searchable, lineNumber, offset, declarations)
            collect(KotlinDeclarationKind.OBJECT, OBJECT_PATTERN, searchable, lineNumber, offset, declarations)
            collect(KotlinDeclarationKind.INTERFACE, INTERFACE_PATTERN, searchable, lineNumber, offset, declarations)
            collect(KotlinDeclarationKind.FUNCTION, FUNCTION_PATTERN, searchable, lineNumber, offset, declarations)
            collect(KotlinDeclarationKind.PROPERTY, PROPERTY_PATTERN, searchable, lineNumber, offset, declarations)
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
        pattern.findAll(line).forEach { match ->
            val nameGroup = match.groups[1] ?: return@forEach
            sink += KotlinDeclaration(
                kind = kind,
                name = nameGroup.value,
                line = lineNumber,
                column = nameGroup.range.first + 1,
                offset = lineOffset + nameGroup.range.first
            )
        }
    }

    private companion object {
        val CLASS_PATTERN = Regex("""\b(?:data|sealed|open|abstract|value|inner|enum|annotation|private|internal|public|protected|\s)*class\s+([A-Za-z_][A-Za-z0-9_]*)""")
        val OBJECT_PATTERN = Regex("""\b(?:data|private|internal|public|protected|\s)*object\s+([A-Za-z_][A-Za-z0-9_]*)""")
        val INTERFACE_PATTERN = Regex("""\b(?:fun|sealed|private|internal|public|protected|\s)*interface\s+([A-Za-z_][A-Za-z0-9_]*)""")
        val FUNCTION_PATTERN = Regex("""\bfun\s+(?:<[^>]+>\s*)?(?:[A-Za-z_][A-Za-z0-9_]*\.)?([A-Za-z_][A-Za-z0-9_]*)\s*\(""")
        val PROPERTY_PATTERN = Regex("""\b(?:private|internal|public|protected|override|lateinit|const|abstract|open|\s)*(?:val|var)\s+([A-Za-z_][A-Za-z0-9_]*)""")
    }
}
