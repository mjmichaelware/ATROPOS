package atropos.core.parser

enum class KotlinDeclarationKind {
    CLASS,
    ENUM,
    ANNOTATION,
    OBJECT,
    INTERFACE,
    FUNCTION,
    PROPERTY,
    TYPEALIAS
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
        val lines = KotlinLexicalMasker.maskNonCode(code).split('\n')
        val utf8Offsets = Utf8OffsetIndex(code)
        val packageName = lines.firstOrNull { it.removeSuffix("\r").trimStart().startsWith("package ") }
            ?.trim()
            ?.removePrefix("package ")
            ?.trim()
            .orEmpty()
        val imports = lines.filter { it.removeSuffix("\r").trimStart().startsWith("import ") }
            .map { it.removeSuffix("\r").trim().removePrefix("import ").trim() }

        val declarations = mutableListOf<KotlinDeclaration>()
        var offset = 0
        lines.forEachIndexed { index, line ->
            val lineNumber = index + 1
            val searchable = line.removeSuffix("\r")
            collect(KotlinDeclarationKind.CLASS, CLASS_PATTERN, searchable, lineNumber, offset, utf8Offsets, declarations)
            collect(KotlinDeclarationKind.ENUM, ENUM_PATTERN, searchable, lineNumber, offset, utf8Offsets, declarations)
            collect(KotlinDeclarationKind.ANNOTATION, ANNOTATION_PATTERN, searchable, lineNumber, offset, utf8Offsets, declarations)
            collect(KotlinDeclarationKind.OBJECT, OBJECT_PATTERN, searchable, lineNumber, offset, utf8Offsets, declarations)
            collect(KotlinDeclarationKind.INTERFACE, INTERFACE_PATTERN, searchable, lineNumber, offset, utf8Offsets, declarations)
            collect(KotlinDeclarationKind.FUNCTION, FUNCTION_PATTERN, searchable, lineNumber, offset, utf8Offsets, declarations)
            collect(KotlinDeclarationKind.PROPERTY, PROPERTY_PATTERN, searchable, lineNumber, offset, utf8Offsets, declarations)
            collect(KotlinDeclarationKind.TYPEALIAS, TYPEALIAS_PATTERN, searchable, lineNumber, offset, utf8Offsets, declarations)
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
        utf8Offsets: Utf8OffsetIndex,
        sink: MutableList<KotlinDeclaration>
    ) {
        pattern.findAll(line).forEach { match ->
            val nameGroup = match.groups[1] ?: return@forEach
            sink += KotlinDeclaration(
                kind = kind,
                name = nameGroup.value,
                line = lineNumber,
                column = nameGroup.range.first + 1,
                offset = utf8Offsets.atCharacterOffset(lineOffset + nameGroup.range.first)
            )
        }
    }

    private companion object {
        val CLASS_PATTERN = Regex("""\b(?:data|sealed|open|abstract|value|inner|private|internal|public|protected|\s)*class\s+([A-Za-z_][A-Za-z0-9_]*)""")
        val ENUM_PATTERN = Regex("""\b(?:private|internal|public|protected|\s)*enum\s+class\s+([A-Za-z_][A-Za-z0-9_]*)""")
        val ANNOTATION_PATTERN = Regex("""\b(?:private|internal|public|protected|\s)*annotation\s+class\s+([A-Za-z_][A-Za-z0-9_]*)""")
        val OBJECT_PATTERN = Regex("""\b(?:data|private|internal|public|protected|\s)*object\s+([A-Za-z_][A-Za-z0-9_]*)""")
        val INTERFACE_PATTERN = Regex("""\b(?:sealed|private|internal|public|protected|\s)*interface\s+([A-Za-z_][A-Za-z0-9_]*)""")
        val FUNCTION_PATTERN = Regex("""\bfun\s+(?:<[^>]+>\s*)?(?:[A-Za-z_][A-Za-z0-9_]*\.)?([A-Za-z_][A-Za-z0-9_]*)\s*\(""")
        val PROPERTY_PATTERN = Regex("""\b(?:private|internal|public|protected|override|lateinit|const|abstract|open|\s)*(?:val|var)\s+([A-Za-z_][A-Za-z0-9_]*)""")
        val TYPEALIAS_PATTERN = Regex("""\b(?:private|internal|public|protected|\s)*typealias\s+([A-Za-z_][A-Za-z0-9_]*)""")
    }
}
