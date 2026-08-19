/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.ui.design.Role

/**
 * A proof hash, woven.
 *
 * A verified run ends in a sixty-four character hex string that nobody reads
 * and nobody can compare at a glance. The same bytes drawn as a small woven
 * mark are comparable in the way a wax seal is: two runs that agree look
 * identical, and two that do not look different immediately, without anyone
 * reading a single character.
 *
 * It is decoration that carries information, which is the only kind worth
 * printing. Same hash, same sigil, every time and on every machine — the mark
 * is a pure function of the digest, so a screenshot of one is as checkable as
 * the string it came from.
 *
 * It claims nothing about validity. A sigil is drawn from whatever hash it is
 * given; that the hash represents a passing gate is [atropos.core.verification]'s
 * business, and drawing an impressive mark for an unverified run would be the
 * fake attestation AGENTS.md 0.6 forbids. Callers pass `verified` explicitly
 * and the colour follows it.
 */
class EvidenceSigil(private val theme: TerminalTheme) {

    /**
     * @param digest a hex fingerprint. Anything shorter than [MINIMUM_DIGEST]
     *   is refused rather than padded: a sigil drawn from four characters
     *   would collide constantly while looking exactly as authoritative.
     */
    fun render(digest: String, verified: Boolean): List<String> {
        val hex = digest.filter { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }.lowercase()
        if (hex.length < MINIMUM_DIGEST) return emptyList()

        val role = if (verified) Role.STATUS_VERIFIED else Role.TEXT_MUTED
        val rows = (0 until SIZE).map { row ->
            val cells = (0 until SIZE).map { column ->
                // Mirrored across the vertical axis, so the mark reads as a
                // seal rather than as a barcode. Half the digest is spent on
                // shape and the symmetry does the rest -- the eye compares
                // symmetric figures far faster than it compares noise.
                val index = row * HALF + (if (column < HALF) column else SIZE - 1 - column)
                GLYPHS[hex[index % hex.length].digitToInt(16) % GLYPHS.size]
            }.joinToString("")
            theme.paint(role, cells)
        }

        return rows + theme.subdued(hex.take(SEAL_CAPTION_CELLS))
    }

    private companion object {
        /** A square seal: wide enough to differ, small enough to sit inline. */
        const val SIZE = 8
        const val HALF = SIZE / 2

        /** Under this, two different runs would draw the same mark too often. */
        const val MINIMUM_DIGEST = 16

        /** How much of the digest is printed under the mark, for checking. */
        const val SEAL_CAPTION_CELLS = 16

        /**
         * Four weights of thread, from open to solid.
         *
         * Drawn from the same box-drawing and block vocabulary as the rest of
         * the interface, so a sigil looks like it belongs to this program and
         * not like a QR code pasted into it.
         */
        val GLYPHS = listOf("·", "░", "▒", "▓")
    }
}
