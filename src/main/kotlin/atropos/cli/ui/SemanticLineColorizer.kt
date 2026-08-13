/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.ui.design.Role

/**
 * Paints engine output by what it means, not by which renderer produced it.
 *
 * A theme that recolours the logo and the footer is not a colour scheme. What
 * makes dense output readable is that every *kind* of token has its own colour
 * everywhere it appears — a status reads as a status, a path reads as a path,
 * a hash recedes — so the eye can find the one line that matters in a wall of
 * key/value rows without reading any of the others.
 *
 * Applied at the render seam rather than in each renderer. There are dozens of
 * renderers emitting flat `key value` text and editing them all would guarantee
 * they drift apart; one transform over their output cannot.
 *
 * Deliberately presentation-only, in the same sense as [RailBlockFormatter]:
 * it changes no text, reorders nothing, and passes through anything it does not
 * recognise. A line that already carries escape codes is left completely alone,
 * because painting over a renderer that already chose its colours would fight
 * it.
 */
class SemanticLineColorizer(private val theme: TerminalTheme) {

    fun colorize(block: String): String {
        if (!theme.colorEnabled || block.isEmpty()) return block
        return block.lines().joinToString("\n", transform = ::colorizeLine)
    }

    private fun colorizeLine(line: String): String {
        // Already styled by its own renderer. Leave it be.
        if (line.contains(ESCAPE)) return line
        if (line.isBlank()) return line

        val trimmed = line.trimStart()
        val indent = line.take(line.length - trimmed.length)

        // A heading owns its whole line: ALL CAPS, no value column.
        if (isHeading(trimmed)) return indent + theme.paint(Role.BRAND, trimmed)

        val row = splitRow(trimmed)
        if (row != null) {
            val (key, gap, value) = row
            return indent + theme.paint(Role.TEXT_SECONDARY, key) + gap + paintValue(value)
        }

        return indent + paintValue(trimmed)
    }

    /**
     * Colours a value one token at a time.
     *
     * Per token rather than per value, because engine lines are compound:
     * `st_memory=PASS scoped_hits=1 rejected=15` is a healthy line carrying a
     * rejection *count*, and painting the whole value as one unit made it red —
     * the reader then scans a wall of identically-coloured rows looking for the
     * word that actually changed. Each field now says what it is on its own, so
     * the outcome, the counts and the labels separate at a glance.
     *
     * A token that reads as `key=value` is painted in two parts, since the
     * label is never the news; see [SemanticTokenClassifier].
     */
    private fun paintValue(value: String): String {
        // Tokenised only when the value is a field list. Prose stays one unit:
        // a sentence painted word by word would let an incidental "failed" in
        // an explanation flare red, and the escape codes would outnumber the
        // words. The `=` is what distinguishes machine fields from English.
        if (!value.contains('=')) return paintToken(value)
        return WORD.replace(value) { match -> paintToken(match.value) }
    }

    private fun paintToken(token: String): String {
        if (token.isEmpty()) return token
        if (!SemanticTokenClassifier.isAssignment(token)) {
            return theme.paint(SemanticTokenClassifier.roleFor(token), token)
        }
        val key = SemanticTokenClassifier.assignmentKey(token)
        val assigned = SemanticTokenClassifier.assignmentValue(token)
        return theme.paint(Role.TEXT_SECONDARY, key) +
            theme.paint(SemanticTokenClassifier.roleFor(assigned), assigned)
    }

    /**
     * Splits an aligned `key   value` row.
     *
     * Requires two spaces or a colon, so ordinary prose is never torn into a
     * fake key and value — a sentence has single spaces, an aligned row does
     * not.
     */
    private fun splitRow(line: String): Triple<String, String, String>? {
        val colon = line.indexOf(COLON)
        if (colon > 0 && colon < MAX_KEY && line.getOrNull(colon + 1)?.isWhitespace() != false) {
            val key = line.take(colon + 1)
            val rest = line.drop(colon + 1)
            val value = rest.trimStart()
            if (value.isEmpty()) return null
            return Triple(key, rest.take(rest.length - value.length), value)
        }

        val gap = GAP.find(line) ?: return null
        if (gap.range.first == 0 || gap.range.first > MAX_KEY) return null
        val key = line.take(gap.range.first)
        if (key.any { it.isWhitespace() }) return null
        val value = line.drop(gap.range.last + 1)
        if (value.isBlank()) return null
        return Triple(key, gap.value, value)
    }

    private fun isHeading(line: String): Boolean =
        line.length in 3..64 &&
            line.none { it.isLowerCase() } &&
            line.any { it.isLetter() } &&
            !line.contains('=') &&
            !line.contains('/')

    private companion object {
        const val ESCAPE = '\u001B'
        const val COLON = ':'
        const val MAX_KEY = 32

        val GAP = Regex(" {2,}")

        /** Non-space runs, so every original space survives painting exactly. */
        val WORD = Regex("\\S+")
    }
}
