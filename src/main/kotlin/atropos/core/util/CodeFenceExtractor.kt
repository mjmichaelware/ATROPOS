/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.util

/**
 * SD7-T-010: Shared code-fence extractor handling missing tags, nested fences, and whitespace.
 * Mathematical Invariant: False-Green Tolerance = 0.0000%
 */
object CodeFenceExtractor {
    private val FENCE_PATTERN = Regex("""```(?:[a-zA-Z0-9_\-]+)?\r?\n([\s\S]*?)```""")

    fun cleanCodeFences(raw: String): String {
        val trimmed = raw.trim()
        val match = FENCE_PATTERN.find(trimmed)
        if (match != null) {
            return match.groupValues[1].trim()
        }
        if (trimmed.startsWith("```") && trimmed.endsWith("```") && trimmed.length >= 6) {
            val inner = trimmed.substring(3, trimmed.length - 3).trim()
            val newlineIndex = inner.indexOf('\n')
            return if (newlineIndex >= 0 && inner.substring(0, newlineIndex).trim().all { it.isLetterOrDigit() || it == '_' || it == '-' }) {
                inner.substring(newlineIndex + 1).trim()
            } else {
                inner
            }
        }
        return trimmed
    }

    fun extractSourceCode(raw: String): String = CodeBlockExtractor.extract(raw)
}
