/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui.design

/**
 * The status vocabulary from Source Doc 3 Section A, fixed across all 16 views
 * and both CLI and GUI surfaces.
 *
 * Source Doc 3 Section E makes the redundancy rule explicit and hard:
 *
 * > Every status-color use pairs with a redundant non-color signal — icon shape
 * > and text label — since color-only status fails for colorblind users if
 * > color is the sole channel; this is a hard rule, not a nice-to-have.
 *
 * That rule is enforced structurally here: a state cannot be constructed
 * without a [glyph] and a [label], so there is no code path that renders this
 * vocabulary as colour alone. Doc 2 rule 124 additionally requires the
 * vocabulary to survive `NO_COLOR` and `TERM=dumb`, where [Role] resolves to
 * no SGR at all and the glyph plus label carry the entire signal.
 *
 * [waiting] is deliberately distinct from [running]: per Section A, "waiting is
 * not the same state as working, don't let them look identical."
 */
enum class RunState(
    val label: String,
    val glyph: String,
    val asciiGlyph: String,
    val role: Role,
    /** Section A motion column. The CLI honours this as animate-or-not only. */
    val animated: Boolean
) {
    /** Accepted, not started. Neutral, static. */
    IDLE("idle", "○", "o", Role.STATUS_IDLE, animated = false),

    /** Queued behind other work. Neutral, static. */
    QUEUED("queued", "◔", "q", Role.STATUS_IDLE, animated = false),

    /** Actively executing. Accent, animated. */
    RUNNING("running", "◐", ">", Role.STATUS_RUNNING, animated = true),

    /** Waiting on input. Warning tone, deliberately NOT animated. */
    WAITING("waiting", "◇", "?", Role.STATUS_WAITING, animated = false),

    /** Blocked or stalled. Warning tone, slow pulse. */
    BLOCKED("blocked", "◼", "!", Role.STATUS_WAITING, animated = true),

    /** Retrying. Accent, animated, must render an attempt counter. */
    RETRYING("retrying", "↻", "@", Role.STATUS_RUNNING, animated = true),

    /** Failed. Danger — reserved exclusively for failure per Section A. */
    FAILED("failed", "✖", "X", Role.STATUS_FAILED, animated = false),

    /** Cancelled. Neutral, label struck through. */
    CANCELLED("cancelled", "⊘", "-", Role.STATUS_CANCELLED, animated = false),

    /** Complete. Success tone, fades to neutral so it stops competing. */
    COMPLETE("complete", "✔", "+", Role.STATUS_COMPLETE, animated = false),

    /**
     * Not wired, not probed, genuinely unknown.
     *
     * Not part of Section A's vocabulary, but required by the base doc's
     * "no fake data / no fabricated zeros" rule so a surface with no data has
     * something honest to render. Must never be shown as [COMPLETE].
     */
    UNKNOWN("unknown", "·", ".", Role.STATUS_UNKNOWN, animated = false);

    /** Section A: cancelled renders with a struck-through label. */
    val struckThrough: Boolean get() = this == CANCELLED

    companion object {
        /** Maps a nullable boolean truthfully: `null` is unknown, never failure. */
        fun ofNullable(value: Boolean?): RunState = when (value) {
            true -> COMPLETE
            false -> FAILED
            null -> UNKNOWN
        }
    }
}
