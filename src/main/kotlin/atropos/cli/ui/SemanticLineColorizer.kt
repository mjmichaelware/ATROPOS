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
     * Colours a value by what it is.
     *
     * Order matters: outcome first, because a line saying `PASS` on a path is
     * primarily an outcome and only incidentally a path. Getting that backwards
     * makes the one word an operator scans for the same colour as the noise
     * around it.
     */
    private fun paintValue(value: String): String {
        val upper = value.uppercase()
        return when {
            FAILURE_MARKERS.any { upper.contains(it) } -> theme.paint(Role.STATUS_ERROR, value)
            DEGRADED_MARKERS.any { upper.contains(it) } -> theme.paint(Role.STATUS_PENDING, value)
            SUCCESS_MARKERS.any { upper.contains(it) } -> theme.paint(Role.STATUS_VERIFIED, value)
            UNSET_MARKERS.any { upper.contains(it) } -> theme.paint(Role.TEXT_MUTED, value)
            looksLikePath(value) -> theme.paint(Role.PATH, value)
            looksLikeDigest(value) -> theme.paint(Role.TEXT_MUTED, value)
            else -> theme.paint(Role.TEXT_PRIMARY, value)
        }
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

    private fun looksLikePath(value: String): Boolean =
        value.contains('/') && !value.contains(' ') && value.length > 3

    /** A hash or an opaque id: present for provenance, not for reading. */
    private fun looksLikeDigest(value: String): Boolean =
        value.length >= 12 && value.none { it.isWhitespace() } &&
            value.count { it.isDigit() } >= 4 &&
            value.all { it.isLetterOrDigit() || it == '-' || it == '_' }

    private companion object {
        const val ESCAPE = '\u001B'
        const val COLON = ':'
        const val MAX_KEY = 32

        val GAP = Regex(" {2,}")

        /** Checked before anything else; an outcome outranks its subject. */
        val FAILURE_MARKERS = listOf("FAIL", "ERROR", "REFUS", "BLOCKED", "VIOLATION", "REJECT")
        val DEGRADED_MARKERS = listOf("DEGRADED", "SOFT_FAIL", "SKIPPED", "WARN", "PARTIAL", "PENDING", "MISS")
        val SUCCESS_MARKERS = listOf("PASS", "VERIFIED", "COMPLETE", "GRANTED", "HIT", "OK", "SUCCEEDED")
        val UNSET_MARKERS = listOf("UNCONFIGURED", "UNSET", "NOT_REQUIRED", "UNRECORDED", "NONE", "ABSENT")
    }
}
