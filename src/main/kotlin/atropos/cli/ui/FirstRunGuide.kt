/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.ui.design.Role

/**
 * The first five minutes, ending in something real.
 *
 * A new operator's first screen is a prompt, and a prompt is only obvious to
 * someone who already knows what the program does. This engine is not the tool
 * it looks like — a sentence typed into the box becomes atoms, research, a
 * graph, code and a proof, not an immediate diff — so the gap between "I
 * opened it" and "I understand it" is wider here than in a tool that just
 * writes code.
 *
 * Three steps, each with the exact command, and each marked with whether it is
 * already true of this install. Reading a checklist that says `done` against
 * work you did not do is worse than no checklist, so every mark comes from a
 * fact the caller measured rather than from a step counter.
 */
class FirstRunGuide(private val theme: TerminalTheme) {

    /**
     * @param providerConfigured whether any provider holds a key.
     * @param documentAttached whether a document has been ingested here.
     * @param runCompleted whether a run has ever reached evidence in this
     *   workspace.
     */
    data class Progress(
        val providerConfigured: Boolean,
        val documentAttached: Boolean,
        val runCompleted: Boolean
    )

    private data class Step(
        val ordinal: String,
        val title: String,
        val why: String,
        val command: String,
        val done: Boolean
    )

    fun render(progress: Progress, width: Int): List<String> {
        val safeWidth = width.coerceAtLeast(MINIMUM_CELLS)
        val steps = listOf(
            Step(
                "1", "Give it a model to think with",
                "Nothing can be researched or built until one provider holds a key. " +
                    "Local models count and cost nothing.",
                "/providers",
                progress.providerConfigured
            ),
            Step(
                "2", "Give it something to build",
                "A specification, a design note, a page of requirements — or just " +
                    "describe what you want in a sentence.",
                "@path/to/your-document.md",
                progress.documentAttached
            ),
            Step(
                "3", "Let it run, and read the proof",
                "It splits the document into atoms, researches them, orders them, " +
                    "builds them, and writes down what it did.",
                "/factory run implement @your-document.md",
                progress.runCompleted
            )
        )

        val lines = mutableListOf<String>()
        lines += theme.paint(Role.BRAND, "FIRST RUN")
        // Every prose line here is wrapped, not only the step bodies. The
        // header and the closing line are the two an operator reads first and
        // last, and they were the two overrunning a phone.
        lines += wrap("Three steps. Each one is checked against this install, not counted.", safeWidth)
            .map(theme::subdued)
        lines += ""

        steps.forEach { step ->
            val mark = if (step.done) DONE else PENDING
            val role = if (step.done) Role.STATUS_VERIFIED else Role.TEXT_MUTED
            lines += wrap("${step.ordinal}. ${step.title}", safeWidth - TITLE_INDENT)
                .mapIndexed { index, part ->
                    if (index == 0) theme.paint(role, "  $mark ") + theme.strong(part)
                    else "     " + theme.strong(part)
                }
            lines += wrap(step.why, safeWidth - DETAIL_INDENT).map { "     " + theme.subdued(it) }
            lines += wrap(step.command, safeWidth - DETAIL_INDENT)
                .map { "     " + theme.paint(Role.ACCENT_FOCUS, it) }
            lines += ""
        }

        val closing =
            if (steps.all(Step::done)) "All three are done. /pipeline shows what happens next."
            else "/pipeline explains every stage. /shortcuts lists every key."
        val closingRole = if (steps.all(Step::done)) Role.STATUS_VERIFIED else Role.TEXT_MUTED
        lines += wrap(closing, safeWidth - CLOSING_INDENT).map { "  " + theme.paint(closingRole, it) }
        return lines
    }

    /** Broken between words; a half-word in a first-run guide is a bad start. */
    private fun wrap(text: String, room: Int): List<String> {
        val width = room.coerceAtLeast(12)
        val lines = mutableListOf<String>()
        val line = StringBuilder()
        text.split(' ').filter(String::isNotEmpty).forEach { word ->
            when {
                line.isEmpty() -> line.append(word)
                line.length + 1 + word.length <= width -> line.append(' ').append(word)
                else -> {
                    lines += line.toString()
                    line.setLength(0)
                    line.append(word)
                }
            }
        }
        if (line.isNotEmpty()) lines += line.toString()
        return lines
    }

    private companion object {
        const val MINIMUM_CELLS = 24
        const val DETAIL_INDENT = 5
        const val TITLE_INDENT = 4
        const val CLOSING_INDENT = 2
        const val DONE = "✓"
        const val PENDING = "○"
    }
}
