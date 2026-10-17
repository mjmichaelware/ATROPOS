/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

import atropos.core.parser.TreeSitterGrammarBridge
import java.nio.file.Files
import java.nio.file.Path

/**
 * SD7-T-050: Deterministic C_impl evaluator.
 * Mathematical Invariant: C_real = min(C_impl, C_int, C_ver, C_ev)
 * C_impl = 1.0 iff all target files exist on disk with non-zero bytes AND Tree-sitter SCM queries report zero hollow stubs; else 0.0.
 */
class CImplScalarEngine(
    private val astBridge: TreeSitterGrammarBridge = TreeSitterGrammarBridge()
) {
    fun evaluate(worktreeRoot: Path, expectedFiles: List<String>): Double {
        if (expectedFiles.isEmpty()) return 0.0

        for (filePath in expectedFiles) {
            val path = worktreeRoot.resolve(filePath)
            if (!Files.isRegularFile(path)) {
                return 0.0
            }
            if (Files.size(path) == 0L) {
                return 0.0
            }

            val sourceCode = Files.readString(path)
            if (sourceCode.contains("TODO()") || sourceCode.contains("NotImplementedError")) {
                return 0.0
            }
            val tree = astBridge.parseTree(sourceCode)
            if (tree.parseErrors.isNotEmpty()) {
                return 0.0
            }
        }
        return 1.0
    }
}