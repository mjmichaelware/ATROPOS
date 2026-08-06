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
        val masked = KotlinLexicalMasker.maskNonCode(code)
        val utf8Offsets = Utf8OffsetIndex(code)
        val packageName = PACKAGE_PATTERN.find(masked)?.groupValues?.get(1)
            .orEmpty()
        val imports = IMPORT_PATTERN.findAll(masked).map { it.groupValues[1] }.toList()
        val declarations = DECLARATION_PATTERNS.flatMap { (kind, pattern) ->
            pattern.findAll(masked).mapNotNull { match ->
                val nameGroup = match.groups[1] ?: return@mapNotNull null
                val nameOffset = match.range.first + nameGroup.range.first
                val lineStart = masked.lastIndexOf('\n', nameOffset - 1) + 1
                KotlinDeclaration(
                    kind = kind,
                    name = nameGroup.value,
                    line = masked.substring(0, nameOffset).count { it == '\n' } + 1,
                    column = nameOffset - lineStart + 1,
                    offset = utf8Offsets.atCharacterOffset(nameOffset)
                )
            }
        }.sortedBy { it.offset }

        return KotlinParseTree(
            packageName = packageName,
            imports = imports,
            declarations = declarations
        )
    }

    private companion object {
        val PACKAGE_PATTERN = Regex("""(?m)^\s*package\s+([A-Za-z_][A-Za-z0-9_.]*)""")
        val IMPORT_PATTERN = Regex("""(?m)^\s*import\s+([A-Za-z_][A-Za-z0-9_.]*(?:\.\*)?)(?:\s+as\s+[A-Za-z_][A-Za-z0-9_]*)?""")
        private const val MODIFIERS = "data|sealed|open|abstract|value|inner|enum|annotation|private|internal|public|protected|final|expect|actual|const|lateinit"
        private val PREFIX = "(?:\\b(?:$MODIFIERS)\\s+)*"
        private const val FUNCTION_MODIFIERS = "override|suspend|inline|operator|infix|tailrec|external|expect|actual|private|internal|public|protected|final"
        private val FUNCTION_PREFIX = "(?:\\b(?:$FUNCTION_MODIFIERS)\\s+)*"
        val DECLARATION_PATTERNS = listOf(
            KotlinDeclarationKind.CLASS to Regex("""\b${PREFIX}class\s+([A-Za-z_][A-Za-z0-9_]*)"""),
            KotlinDeclarationKind.OBJECT to Regex("""\b${PREFIX}object\s+([A-Za-z_][A-Za-z0-9_]*)"""),
            KotlinDeclarationKind.INTERFACE to Regex("""\b${PREFIX}interface\s+([A-Za-z_][A-Za-z0-9_]*)"""),
            KotlinDeclarationKind.FUNCTION to Regex("""\b${FUNCTION_PREFIX}fun\s+(?:<[^>]+>\s*)?(?:[A-Za-z_][A-Za-z0-9_]*\.)?([A-Za-z_][A-Za-z0-9_]*)\s*\("""),
            KotlinDeclarationKind.PROPERTY to Regex("""\b${PREFIX}(?:val|var)\s+([A-Za-z_][A-Za-z0-9_]*)""")
        )
    }
}
