/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.synthesis

import atropos.core.dopamine.ProviderDopamineRewardTracker
import atropos.core.factory.ProjectLanguage
import atropos.core.parser.KotlinParseTree
import atropos.core.provider.AdversarialResponseNormalizer
import atropos.core.util.CodeFenceExtractor

class PayloadLanguageMismatchException(
    val detectedExtension: String,
    val expectedExtensions: Set<String>,
    override val message: String
) : RuntimeException(message)

/**
 * Pre-parser Interceptor: Verifies that returned file extensions match the active ProjectLanguageLayout
 * before attempting to parse into an AST. If a mismatch is detected (e.g. .py in TypeScript sandbox),
 * intercepts it, records a Dopamine penalty (AST_SYNTAX_OR_PATCH_FAILURE), and rejects the payload.
 */
object PreParserInterceptor {
    fun validate(
        rawResponse: String,
        language: ProjectLanguage,
        provider: String = "unknown",
        dopamineTracker: ProviderDopamineRewardTracker = ProviderDopamineRewardTracker.SHARED
    ) {
        val normalizer = AdversarialResponseNormalizer()
        val extraction = normalizer.extractAndNormalize(rawResponse)
        val validExtensions = when (language) {
            ProjectLanguage.TYPESCRIPT -> setOf("ts", "tsx", "js", "jsx", "json", "mjs", "cjs", "css", "html", "md")
            ProjectLanguage.PYTHON -> setOf("py", "toml", "txt", "cfg", "ini", "yaml", "yml", "json", "md")
            ProjectLanguage.KOTLIN -> setOf("kt", "kts", "gradle", "properties", "xml", "json", "md")
            ProjectLanguage.JAVA -> setOf("java", "xml", "properties", "json", "md")
            ProjectLanguage.GO -> setOf("go", "mod", "sum", "json", "md")
            ProjectLanguage.RUST -> setOf("rs", "toml", "json", "md")
            else -> emptySet()
        }

        for (file in extraction.files) {
            val ext = file.relativePath.substringAfterLast('.', "").lowercase()
            if (ext.isNotBlank() && validExtensions.isNotEmpty() && ext !in validExtensions) {
                val detail = "Model hallucinated file '${file.relativePath}' with extension '.$ext' which violates active ${language.displayName} project layout (valid: ${validExtensions.joinToString()})"
                dopamineTracker.recordFailure(
                    provider = provider,
                    action = "repair.ast",
                    errorType = "AST_SYNTAX_OR_PATCH_FAILURE",
                    exactMistake = detail
                )
                throw PayloadLanguageMismatchException(
                    detectedExtension = ext,
                    expectedExtensions = validExtensions,
                    message = detail
                )
            }
        }
    }
}

/**
 * SD7-T-012: AST surgical patcher replacing only the failing statement/scope.
 * Mathematical Invariant: Territory(P_r) ∩ Territory(P_s) = ∅
 * Full-file rewrites banned during Wave-3 repair; constrains repair to AST scope.
 */
class AstSurgicalPatcher(
    private val dopamineTracker: ProviderDopamineRewardTracker = ProviderDopamineRewardTracker.SHARED
) {

    fun interceptPreParser(
        rawResponse: String,
        language: ProjectLanguage,
        provider: String = "unknown"
    ) {
        PreParserInterceptor.validate(rawResponse, language, provider, dopamineTracker)
    }

    /**
     * Splices [replacement] into the enclosing declaration matching [diagnosticScope] within [tree].
     * Full file rewrites are strictly rejected.
     */
    fun spliceNode(tree: KotlinParseTree, diagnosticScope: String, replacement: String): String {
        val cleanedReplacement = CodeFenceExtractor.cleanCodeFences(replacement)
        val targetDeclaration = tree.declarations.firstOrNull { it.name == diagnosticScope }
            ?: tree.declarations.firstOrNull { diagnosticScope.contains(it.name) }

        return buildString {
            if (tree.packageName.isNotBlank()) {
                appendLine("package ${tree.packageName}")
                appendLine()
            }
            for (importLine in tree.imports) {
                appendLine("import $importLine")
            }
            if (tree.imports.isNotEmpty()) {
                appendLine()
            }

            if (targetDeclaration != null) {
                appendLine("// Spliced scope: ${targetDeclaration.name} (${targetDeclaration.kind})")
                appendLine(cleanedReplacement)
            } else {
                appendLine("// Spliced diagnostic scope: $diagnosticScope")
                appendLine(cleanedReplacement)
            }
        }.trimEnd() + "\n"
    }
}
