/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

import atropos.core.parser.TreeSitterGrammarBridge
import java.nio.file.Files
import java.nio.file.Path

class CImplScalarEngine(
    private val astBridge: TreeSitterGrammarBridge = TreeSitterGrammarBridge()
) {
    fun evaluate(worktreeRoot: Path, expectedFiles: List<String>): Int {
        for (filePath in expectedFiles) {
            val path = worktreeRoot.resolve(filePath)
            if (!Files.isRegularFile(path)) {
                return 0
            }
            if (Files.size(path) == 0L) {
                return 0
            }

            val sourceCode = Files.readString(path)
            if (sourceCode.contains("TODO()") || sourceCode.contains("NotImplementedError")) {
                return 0
            }
            val tree = astBridge.parseTree(sourceCode)
            if (tree.parseErrors.isNotEmpty()) {
                return 0
            }
        }
        return 1
    }
}