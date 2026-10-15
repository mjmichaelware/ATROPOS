package atropos.core.verification

import atropos.core.parser.TreeSitterGrammarBridge
import java.nio.file.Path
import java.nio.file.Files

class CIntScalarEngine(
    private val astBridge: TreeSitterGrammarBridge = TreeSitterGrammarBridge()
) {
    fun evaluate(worktreeRoot: Path, entrypointSymbol: String): Int {
        val astRoot = astBridge.parse(Files.readString(worktreeRoot.resolve(entrypointSymbol)), "kotlin")
        val callers = astBridge.query(astRoot, "call-graph")

        for (caller in callers) {
            val symbol = caller.text
            if (!symbolExistsInWorktree(worktreeRoot, symbol)) {
                return 0
            }
        }

        return 1
    }

    private fun symbolExistsInWorktree(worktreeRoot: Path, symbol: String): Boolean {
        val allFiles = mutableListOf<Path>()
        Files.walk(worktreeRoot).forEach { if (it.toString().endsWith(".kt") || it.toString().endsWith(".java")) allFiles.add(it) }

        for (file in allFiles) {
            val content = Files.readString(file)
            if (content.contains(symbol)) return true
        }
        return false
    }
}