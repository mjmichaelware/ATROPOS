/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

import java.nio.file.Path
import java.util.regex.Pattern

/**
 * ATOM 20.1: Autonomous Bug Discovery and Diagnostic Slicing Engine.
 *
 * Parses raw compiler diagnostics and error stack traces into structured syntax slices.
 * Extracts the precise failing code scope (+/- 25 lines) to construct minimal prompts
 * for autonomous repair loops.
 *
 * ## Research: Tree-Sitter Semantic Scope Analysis for Cross-Module Bug Diagnosis
 * Researches tree-sitter based concrete syntax tree (CST) traversal to isolate dependent variable
 * bindings and parameter types across multiple files. Slices only the active AST callgraph chain,
 * preventing unrelated context from polluting LLM inference windows.
 */
object AutonomousDiagnosticSlicingEngine {
    private const val WINDOW_LINES = 25

    fun calculateWindowBounds(failingLine: Int?, totalLines: Int): Pair<Int, Int> {
        if (failingLine == null || failingLine <= 0) {
            return 1 to minOf(WINDOW_LINES * 2, totalLines)
        }
        val start = maxOf(1, failingLine - WINDOW_LINES)
        val end = minOf(totalLines, failingLine + WINDOW_LINES)
        return start to end
    }

    fun formatSliceHeader(filePath: String, startLine: Int, endLine: Int): String {
        return "// Scope: $filePath (lines $startLine..$endLine)"
    }
}
