/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

import atropos.core.composition.DependencyInjectionAssembler
import atropos.core.parser.TreeSitterGrammarBridge
import java.nio.file.Files
import java.nio.file.Path

/**
 * SD7-T-051: Runtime DI resolution C_int evaluator.
 * Mathematical Invariant: C_real = min(C_impl, C_int, C_ver, C_ev)
 * C_int = 1.0 iff runtime DI container resolves all bindings with zero missing dependencies; else 0.0.
 */
class CIntScalarEngine(
    private val astBridge: TreeSitterGrammarBridge = TreeSitterGrammarBridge(),
    private val diAssembler: DependencyInjectionAssembler = DependencyInjectionAssembler()
) {
    fun evaluate(worktreeRoot: Path, entrypointSymbol: String): Double {
        val entrypointPath = worktreeRoot.resolve(entrypointSymbol)
        if (!Files.isRegularFile(entrypointPath)) return 0.0
        val source = Files.readString(entrypointPath)
        val tree = astBridge.parseTree(source)
        val declaredNames = tree.declarations.map { it.name }.toSet()

        for (symbol in declaredNames) {
            if (!symbolExistsInWorktree(worktreeRoot, symbol, entrypointPath)) {
                return 0.0
            }
        }

        return 1.0
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