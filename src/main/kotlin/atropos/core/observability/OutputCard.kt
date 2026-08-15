/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.observability

import atropos.core.journal.EventCategory

/**
 * One discretely copyable unit of run output.
 *
 * Source Doc 3 §5.2: plans, commands, outputs, diffs, logs, provider responses,
 * tests and reports "must be copyable as discrete cards". The word doing the
 * work is *discrete*. A transcript is copyable in the trivial sense that a
 * terminal has a selection; what an operator actually needs is to lift one
 * command, or one diff, without the eleven lines of narration around it — and
 * on a phone, without a mouse to select with.
 *
 * A card is therefore a bounded region with a body that means something on its
 * own. [body] is exactly what lands on the clipboard: no rail glyphs, no
 * timestamps, no ANSI. Everything else here is chrome for rendering it, and
 * [CardRenderer] owns that. Keeping the copyable text separate from its
 * presentation is what makes copy fidelity — a Source Doc 3 §4.1 metric —
 * measurable rather than aspirational.
 *
 * @param language a fence hint for Markdown export (`kotlin`, `diff`, `bash`).
 *   Null for prose. Not a syntax highlighter; it is the one piece of
 *   information a downstream renderer cannot recover from the body itself.
 */
data class OutputCard(
    val kind: CardKind,
    val title: String,
    val body: String,
    val sequence: Long,
    val language: String? = null,
    val requirement: String? = null,
    val provider: String? = null,
    val role: ExecutionRole = ExecutionRole.SYSTEM,
    val evidenceHash: String? = null,
    val exitCode: Int? = null
) {
    /** Exactly what a copy action places on the clipboard — nothing added. */
    fun copyText(): String = body

    /**
     * Bytes of copyable text, which is what the copy-fidelity metric compares.
     *
     * A card whose rendered form is 900 characters and whose copy is 200 has
     * lost 700 characters of something, and the only way to notice is to
     * measure both.
     */
    fun copyBytes(): Int = body.toByteArray(Charsets.UTF_8).size

    /** True when this card records a failure, whatever its kind. */
    val failed: Boolean
        get() = exitCode != null && exitCode != 0 || kind == CardKind.ERROR

    companion object {
        /**
         * Derives a card from an event, or null when the event is not
         * card-worthy.
         *
         * Heartbeats and status ticks are events but not cards: a card is
         * something an operator would want to lift out, and a card stream that
         * includes every tick buries the ones that matter. Returning null is
         * the honest answer for those rather than producing an empty card.
         */
        fun from(event: ExecutionEvent): OutputCard? {
            val kind = CardKind.of(event.category) ?: return null
            return OutputCard(
                kind = kind,
                title = titleFor(kind, event),
                body = event.payload,
                sequence = event.sequence,
                language = kind.language,
                requirement = event.requirement,
                provider = event.provider,
                role = event.role,
                evidenceHash = event.evidenceHash
            )
        }

        private fun titleFor(kind: CardKind, event: ExecutionEvent): String = buildString {
            append(kind.label)
            event.task?.let { append(" · ").append(it) }
            event.provider?.let { append(" · ").append(it) }
        }
    }
}

/**
 * The card types Source Doc 3 §5.2 enumerates, mapped from journal categories.
 *
 * A closed set rather than a free label, for the same reason [ExecutionRole] is
 * closed: the card list is filtered, and `diff` / `Diff` / `diffs` are three
 * kinds to a filter and one to a reader.
 */
enum class CardKind(val label: String, val language: String?) {
    PLAN("Plan", null),
    COMMAND("Command", "bash"),
    OUTPUT("Output", null),
    DIFF("Diff", "diff"),
    LOG("Log", null),
    PROVIDER_RESPONSE("Provider response", null),
    TEST("Test", null),
    REPORT("Report", null),
    ERROR("Error", null);

    companion object {
        /**
         * The card kind an event category implies, or null when the category
         * is not card-worthy.
         */
        fun of(category: EventCategory): CardKind? = when (category) {
            EventCategory.DAG -> PLAN
            EventCategory.COMMAND -> COMMAND
            EventCategory.STDOUT -> OUTPUT
            EventCategory.STDERR -> LOG
            EventCategory.DIFF -> DIFF
            EventCategory.TEST -> TEST
            EventCategory.VERIFICATION -> REPORT
            EventCategory.TEXT, EventCategory.REASONING -> PROVIDER_RESPONSE
            EventCategory.TOOL_CALL -> COMMAND
            EventCategory.ERROR, EventCategory.FAILURE -> ERROR
            EventCategory.FILE_MUTATION -> DIFF
            EventCategory.COMPLETION -> REPORT
            EventCategory.POLICY -> REPORT
            else -> null
        }
    }
}

val OutputCard.status: String get() = if (this.failed) "FAILED" else "SUCCESS"
val OutputCard.evidenceLinks: List<String> get() = listOfNotNull(this.evidenceHash)
val OutputCard.content: String get() = this.body
