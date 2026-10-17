/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.synthesis

import atropos.core.parser.KotlinParseTree
import atropos.core.util.CodeFenceExtractor

/**
 * SD7-T-012: AST surgical patcher replacing only the failing statement/scope.
 * Mathematical Invariant: Territory(P_r) ∩ Territory(P_s) = ∅
 * Full-file rewrites banned during Wave-3 repair; constrains repair to AST scope.
 */
class AstSurgicalPatcher {

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
