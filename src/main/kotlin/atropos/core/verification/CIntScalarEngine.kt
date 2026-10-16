/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

import atropos.core.parser.TreeSitterGrammarBridge
import java.nio.file.Path
import java.nio.file.Files

class CIntScalarEngine(
    private val astBridge: TreeSitterGrammarBridge = TreeSitterGrammarBridge()
) {
    fun evaluate(worktreeRoot: Path, entrypointSymbol: String): Int {
        val entrypointPath = worktreeRoot.resolve(entrypointSymbol)
        if (!Files.isRegularFile(entrypointPath)) return 0
        val tree = astBridge.parseTree(Files.readString(entrypointPath))
        val declaredNames = tree.declarations.map { it.name }.toSet()

        for (symbol in declaredNames) {
            if (!symbolExistsInWorktree(worktreeRoot, symbol, entrypointPath)) {
                return 0
            }
        }

        return 1
    }

    private fun symbolExistsInWorktree(worktreeRoot: Path, symbol: String, exclude: Path): Boolean {
        val allFiles = mutableListOf<Path>()
        Files.walk(worktreeRoot).use { stream ->
            stream.forEach { if ((it.toString().endsWith(".kt") || it.toString().endsWith(".java")) && it != exclude) allFiles.add(it) }
        }

        for (file in allFiles) {
            val content = Files.readString(file)
            if (content.contains(symbol)) return true
        }
        return false
    }
}