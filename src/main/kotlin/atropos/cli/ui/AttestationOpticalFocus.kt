/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

/** Adds a non-interactive visual focus cue to attestation state. */
class AttestationOpticalFocus {
    data class Cue(val glyph: String, val state: String, val preservesInput: Boolean = true)

    fun cue(attested: Boolean): Cue = if (attested) {
        Cue(glyph = "◎", state = "attested")
    } else {
        Cue(glyph = "○", state = "unattested")
    }

    fun prefix(attested: Boolean): String = "${cue(attested).glyph} "
}
