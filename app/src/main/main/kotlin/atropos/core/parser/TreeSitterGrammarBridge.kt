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
    val offset: Int,
    val scope: List<String> = emptyList(),
    val characterOffset: Int = offset
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
        val imports = IMPORT_PATTERN.findAll(masked).map { match ->
            val path = match.groupValues[1]
            val alias = match.groupValues.getOrNull(2).orEmpty()
            if (alias.isBlank()) path else "$path as $alias"
        }.toList()
        val rawDeclarations = DECLARATION_PATTERNS.flatMap { (kind, pattern) ->
            pattern.findAll(masked).mapNotNull { match ->
                val nameGroup = match.groups[1] ?: return@mapNotNull null
                // MatchGroup ranges are absolute in the input string; adding
                // the enclosing match offset double-counts every later match.
                val nameOffset = nameGroup.range.first
                val lineStart = masked.lastIndexOf('\n', nameOffset - 1) + 1
                KotlinDeclaration(
                    kind = kind,
                    name = nameGroup.value,
                    line = masked.substring(0, nameOffset).count { it == '\n' } + 1,
                    column = nameOffset - lineStart + 1,
                    offset = utf8Offsets.atCharacterOffset(nameOffset),
                    characterOffset = nameOffset
                )
            }
        }.sortedBy { it.offset }
        val containers = rawDeclarations
            .filter { it.kind in CONTAINER_KINDS }
            .mapNotNull { declaration ->
                val openBrace = masked.indexOf('{', declaration.characterOffset + declaration.name.length)
                if (openBrace < 0) return@mapNotNull null
                val nextDeclarationOffset = rawDeclarations
                    .asSequence()
                    .map { it.characterOffset }
                    .filter { it > declaration.characterOffset }
                    .minOrNull()
                if (nextDeclarationOffset != null && openBrace > nextDeclarationOffset) {
                    return@mapNotNull null
                }
                val closeBrace = matchingBrace(masked, openBrace) ?: return@mapNotNull null
                DeclarationContainer(
                    name = declaration.name,
                    openBrace = openBrace,
                    closeBrace = closeBrace
                )
            }
        val declarations = rawDeclarations.map { declaration ->
            val scope = containers
                .filter { declaration.characterOffset > it.openBrace && declaration.characterOffset < it.closeBrace }
                .sortedBy { it.closeBrace - it.openBrace }
                .asReversed()
                .map { it.name }
            declaration.copy(scope = scope)
        }

        return KotlinParseTree(
            packageName = packageName,
            imports = imports,
            declarations = declarations
        )
    }

    private fun matchingBrace(source: String, openBrace: Int): Int? {
        var depth = 0
        for (index in openBrace until source.length) {
            when (source[index]) {
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) return index
                }
            }
        }
        return null
    }

    private data class DeclarationContainer(
        val name: String,
        val openBrace: Int,
        val closeBrace: Int
    )

    private companion object {
        val PACKAGE_PATTERN = Regex("""(?m)^\s*package\s+([A-Za-z_][A-Za-z0-9_.]*)""")
        val IMPORT_PATTERN = Regex("""(?m)^\s*import\s+([A-Za-z_][A-Za-z0-9_.]*(?:\.\*)?)(?:\s+as\s+([A-Za-z_][A-Za-z0-9_]*))?""")
        private const val MODIFIERS = "data|sealed|open|abstract|value|inner|enum|annotation|private|internal|public|protected|final|expect|actual|const|lateinit"
        private const val CLASS_MODIFIERS = "data|sealed|open|abstract|value|inner|private|internal|public|protected|final|expect|actual"
        private val PREFIX = "(?:\\b(?:$MODIFIERS)\\s+)*"
        private val CLASS_PREFIX = "(?:\\b(?:$CLASS_MODIFIERS)\\s+)*"
        private const val FUNCTION_MODIFIERS = "override|suspend|inline|operator|infix|tailrec|external|expect|actual|private|internal|public|protected|final"
        private val FUNCTION_PREFIX = "(?:\\b(?:$FUNCTION_MODIFIERS)\\s+)*"
        private val CONTAINER_KINDS = setOf(
            KotlinDeclarationKind.CLASS,
            KotlinDeclarationKind.ENUM,
            KotlinDeclarationKind.ANNOTATION,
            KotlinDeclarationKind.OBJECT,
            KotlinDeclarationKind.INTERFACE
        )
        val DECLARATION_PATTERNS = listOf(
            KotlinDeclarationKind.ENUM to Regex("""\b${PREFIX}enum\s+class\s+([A-Za-z_][A-Za-z0-9_]*)"""),
            KotlinDeclarationKind.ANNOTATION to Regex("""\b${PREFIX}annotation\s+class\s+([A-Za-z_][A-Za-z0-9_]*)"""),
            KotlinDeclarationKind.CLASS to Regex("""(?<!enum\s)(?<!annotation\s)\b${CLASS_PREFIX}class\s+([A-Za-z_][A-Za-z0-9_]*)"""),
            KotlinDeclarationKind.OBJECT to Regex("""\b${PREFIX}object\s+([A-Za-z_][A-Za-z0-9_]*)"""),
            KotlinDeclarationKind.INTERFACE to Regex("""\b${PREFIX}interface\s+([A-Za-z_][A-Za-z0-9_]*)"""),
            KotlinDeclarationKind.FUNCTION to Regex("""\b${FUNCTION_PREFIX}fun\s+(?:<[^>]+>\s*)?(?:[A-Za-z_][A-Za-z0-9_]*\.)?([A-Za-z_][A-Za-z0-9_]*)\s*\("""),
            KotlinDeclarationKind.PROPERTY to Regex("""\b${PREFIX}(?:val|var)\s+([A-Za-z_][A-Za-z0-9_]*)"""),
            KotlinDeclarationKind.TYPEALIAS to Regex("""\b${PREFIX}typealias\s+([A-Za-z_][A-Za-z0-9_]*)""")
        )
    }
}
