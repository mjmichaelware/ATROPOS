/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.util

/**
 * SD7-T-011: CodeBlockExtractor enforcing string unescaping and code fence normalization.
 * Mathematical Invariant: False-Green Tolerance = 0.0000%
 */
object CodeBlockExtractor {
    fun normalize(code: String): String =
        code.replace("\\\"", "\"").replace("\\'", "'")

    fun extract(raw: String): String =
        normalize(CodeFenceExtractor.cleanCodeFences(raw))
}
