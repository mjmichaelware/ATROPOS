/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.observability

/**
 * Draws a card without touching what the card copies.
 *
 * The separation is the whole point. [OutputCard.body] is what lands on the
 * clipboard; everything this file adds — a header, a fence, a rule — is chrome
 * that must never end up there. Copy fidelity is a Source Doc 3 §4.1 metric,
 * and a renderer that mixed its decoration into the copyable text would make
 * the metric measure the decoration.
 *
 * Nothing here emits ANSI. Colour belongs to the theme at the render seam, and
 * a card that carried its own escapes would fight it — the same reason
 * `RailBlockFormatter` stays presentation-only. This produces plain text with
 * structure; painting happens later or not at all, and the `NO_COLOR` and
 * `TERM=dumb` cases come out right for free.
 *
 * Bodies are bounded on render, never on copy. An operator scrolling a card
 * list wants the first lines; an operator copying a card wants all of it, and
 * truncating the clipboard to fit a screen is how a 4,000-line log becomes a
 * 20-line log nobody can debug from.
 */
class CardRenderer(
    private val previewLines: Int = DEFAULT_PREVIEW_LINES,
    private val width: Int = DEFAULT_WIDTH
) {

    /** A card as a compact list entry: one header line plus a short preview. */
    fun renderPreview(card: OutputCard): String = buildString {
        appendLine(header(card))
        val lines = card.body.lines()
        lines.take(previewLines).forEach { line ->
            append("  ").appendLine(clip(line))
        }
        if (lines.size > previewLines) {
            append("  … ").append(lines.size - previewLines).appendLine(" more lines · copy for all")
        }
    }.trimEnd()

    /** A card in full, for an expanded view. The body is never shortened. */
    fun renderFull(card: OutputCard): String = buildString {
        appendLine(header(card))
        appendLine(rule())
        appendLine(card.body)
        appendLine(rule())
        appendLine(footer(card))
    }.trimEnd()

    /**
     * A card as Markdown, fenced when it has a language.
     *
     * Used by [MarkdownExporter] and by any surface that can render Markdown.
     * The fence is chosen from [OutputCard.language] rather than guessed from
     * the body, because guessing produces a `kotlin` fence on a stack trace
     * roughly as often as it produces the right one.
     */
    fun renderMarkdown(card: OutputCard): String = buildString {
        append("### ").appendLine(card.title)
        appendLine()
        metadata(card).forEach { (key, value) ->
            append("- **").append(key).append("**: ").appendLine(value)
        }
        if (metadata(card).isNotEmpty()) appendLine()
        val fence = "```"
        append(fence).appendLine(card.language.orEmpty())
        appendLine(escapeFences(card.body))
        appendLine(fence)
    }.trimEnd()

    /**
     * The header: kind, sequence, and the outcome if there is one.
     *
     * Sequence is included because it is the handle — an operator asking about
     * "the failing command" needs a number to name it by, and cards without one
     * force them to describe the card instead.
     */
    private fun header(card: OutputCard): String = buildString {
        append('[').append(card.kind.label).append(" #").append(card.sequence).append(']')
        append(' ').append(card.title)
        card.exitCode?.let { append(" (exit ").append(it).append(')') }
    }

    private fun footer(card: OutputCard): String = buildString {
        append(card.copyBytes()).append(" bytes copyable")
        card.requirement?.let { append(" · requirement ").append(it) }
        card.evidenceHash?.let { append(" · evidence ").append(it.take(16)) }
    }

    private fun metadata(card: OutputCard): List<Pair<String, String>> = buildList {
        add("Role" to card.role.canonical)
        card.provider?.let { add("Provider" to it) }
        card.requirement?.let { add("Requirement" to it) }
        card.exitCode?.let { add("Exit code" to it.toString()) }
        card.evidenceHash?.let { add("Evidence" to it) }
    }

    private fun rule(): String = "-".repeat(width.coerceIn(8, 200))

    private fun clip(line: String): String =
        if (line.length <= width) line else line.take(width - 1) + "…"

    /**
     * Prevents a body containing a fence from ending its own code block.
     *
     * A diff of a Markdown file is the ordinary case, not an exotic one, and a
     * card that terminated early would put the rest of the run's export outside
     * any fence — silently, and only in the exported copy.
     */
    private fun escapeFences(body: String): String =
        body.replace("```", "​`​`​`")

    companion object {
        const val DEFAULT_PREVIEW_LINES = 6
        const val DEFAULT_WIDTH = 78
    }
}
