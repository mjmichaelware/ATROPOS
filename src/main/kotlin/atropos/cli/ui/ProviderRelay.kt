/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.ui.design.Role

/**
 * A provider dropping out and another catching the work.
 *
 * The cascade is the most distinctive thing this engine does — a request that
 * would have failed on one provider quietly completes on the next — and it was
 * invisible. It reached the operator as a line of prose in a full trace, if
 * they had the trace open, which they mostly do not.
 *
 * Drawn as a relay, the handoff is legible in one glance: who was asked, who
 * refused and why, and who is carrying it now. The point is not decoration.
 * An operator who cannot see the cascade cannot tell a slow provider from a
 * dead one, and cannot tell that their run is being served by a fallback whose
 * answers they might want to weigh differently.
 */
class ProviderRelay(private val theme: TerminalTheme) {

    /**
     * @param attempted providers in the order they were tried, each with the
     *   reason it did not answer. An empty reason means it did.
     */
    data class Leg(val provider: String, val refusal: String = "")

    fun render(legs: List<Leg>, width: Int): List<String> {
        if (legs.isEmpty()) return emptyList()

        val carrier = legs.lastOrNull { it.refusal.isBlank() }
        val lines = mutableListOf<String>()

        legs.forEach { leg ->
            val answered = leg.refusal.isBlank()
            val mark = if (answered) CARRYING else DROPPED
            val role = if (answered) Role.STATUS_VERIFIED else Role.TEXT_MUTED
            val detail = if (answered) "answered" else leg.refusal
            lines += TerminalText.ellipsize(
                "  " + theme.paint(role, mark) + " " +
                    theme.paint(if (answered) Role.ACCENT_FOCUS else Role.TEXT_MUTED, leg.provider) +
                    "  " + theme.subdued(detail),
                width
            )
        }

        // Named at the end, because "who actually answered this" is the
        // question an operator is asking and it should not have to be inferred
        // by scanning the list for the one without a refusal.
        lines += if (carrier == null) {
            "  " + theme.paint(Role.STATUS_FAILED, "no provider answered; queued for retry")
        } else {
            "  " + theme.subdued("carried by ") + theme.strong(carrier.provider)
        }
        return lines
    }

    private companion object {
        const val CARRYING = "●"
        const val DROPPED = "○"
    }
}
