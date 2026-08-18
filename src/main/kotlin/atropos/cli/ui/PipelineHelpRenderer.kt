/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.ui.design.Role

/**
 * What ATROPOS actually does, end to end, and how to drive each stage.
 *
 * `/help` lists 231 commands alphabetically, which answers "what can I type"
 * and not "what is this for". An operator who has just installed the engine
 * needs the second question answered first -- and the one-line tip they were
 * getting instead ("turn a research document into a DAG") described the
 * factory's first stage and called it the whole factory.
 *
 * Stages, not internals. Each entry says what the stage is for, what it
 * produces, and the command that drives it. Nothing here describes how the
 * compiler segments a statement or how the lakehouse is addressed: that is
 * architecture, it changes, and an operator does not need it to work the
 * tool.
 */
class PipelineHelpRenderer(private val theme: TerminalTheme) {

    private data class Stage(
        val ordinal: String,
        val title: String,
        val purpose: String,
        val produces: String,
        val commands: List<String>
    )

    fun render(width: Int): List<String> {
        val safeWidth = width.coerceAtLeast(28)

        return buildList {
            add(theme.paint(Role.BRAND, " THE PIPELINE"))
            add(theme.subdued(" A document, or a sentence, becomes verified work."))
            add("")

            STAGES.forEach { stage ->
                add(
                    theme.paint(Role.ACCENT_FOCUS, " " + stage.ordinal + "  ") +
                        theme.strong(stage.title)
                )
                wrapped(stage.purpose, safeWidth - 5).forEach { add("     " + theme.subdued(it)) }
                add("     " + theme.metadata("gives you: " + stage.produces))
                stage.commands.forEach { command ->
                    add("     " + theme.paint(Role.ACCENT_FOCUS, command))
                }
                add("")
            }

            add(theme.paint(Role.BRAND, " TWO WAYS IN"))
            add("")
            add("   " + theme.paint(Role.ACCENT_FOCUS, "@path/to/spec.pdf"))
            wrapped(
                "Attach a document you already wrote. txt, md, docx and pdf arrive " +
                    "as text; images arrive described. Tab completes the path.",
                safeWidth - 5
            ).forEach { add("     " + theme.subdued(it)) }
            add("")
            add("   " + theme.paint(Role.ACCENT_FOCUS, "/factory run build a notes app with tags and search"))
            wrapped(
                "Describe it instead. The engine writes the specification for you, " +
                    "then treats it exactly as it would a document you attached -- " +
                    "so the same decomposition, research and verification apply either way.",
                safeWidth - 5
            ).forEach { add("     " + theme.subdued(it)) }
            add("")
            add(theme.subdued(" /thinking 3 shows every stage as it happens."))
        }
    }

    private fun wrapped(text: String, width: Int): List<String> =
        AnsiLineWrapper.wrap(text, width.coerceAtLeast(12))

    private companion object {
        val STAGES = listOf(
            Stage(
                "1", "Ingest",
                "Your document is read and canonicalised. It is bounded to the " +
                    "directory you launched from plus anything you granted, and " +
                    "hashed, so what was read is provable later.",
                "a canonical source with a hash",
                listOf("/factory run implement @spec.pdf")
            ),
            Stage(
                "2", "Decompose",
                "The document is split into atoms -- one obligation each. A " +
                    "requirement with two verbs becomes two atoms, because a " +
                    "thing that cannot be verified separately cannot be built " +
                    "separately.",
                "every requirement, individually addressable",
                listOf("/dag nodes", "/dag status")
            ),
            Stage(
                "3", "Dimension",
                "Each atom is examined across sixteen fixed dimensions -- the " +
                    "questions that have to be answered before it can be built " +
                    "rather than guessed at.",
                "sixteen answered questions per atom",
                listOf("/dag hig")
            ),
            Stage(
                "4", "Research",
                "Gaps become research tasks. Anything the lakehouse already " +
                    "knows is attached to the atom it belongs to; anything still " +
                    "missing is asked of a provider until confidence is high " +
                    "enough to proceed.",
                "atoms carrying their own evidence",
                listOf("/status", "/providers")
            ),
            Stage(
                "5", "Plan",
                "Atoms and their dependencies become an execution DAG. Order is " +
                    "derived from what depends on what, not from the order you " +
                    "happened to write things in.",
                "a runnable graph with a defined order",
                listOf("/dag runnable", "/dag cycles")
            ),
            Stage(
                "6", "Build",
                "Nodes execute in an isolated worktree. Each one is verified " +
                    "before it merges, so a failure is contained to the node that " +
                    "caused it.",
                "code, merged only after it verifies",
                listOf("/factory run", "/self-host run")
            ),
            Stage(
                "7", "Verify",
                "Gates decide whether the work actually holds -- compilation, " +
                    "tests, the checks the specification asked for. Nothing is " +
                    "reported as done on a gate that did not run. On a phone " +
                    "there is no JDK to compile with, so set " +
                    "ATROPOS_COMPILE_GATE=github and the compile runs on " +
                    "GitHub Actions instead of stalling here.",
                "a pass or a named failure, never a guess",
                listOf("/verify narrow", "/verify wide", "/tests matrix")
            ),
            Stage(
                "8", "Evidence",
                "The run writes down what it did: the source hash, the atoms, " +
                    "the research, the gate results. A claim you can check beats " +
                    "a claim you have to trust.",
                "an evidence bundle on disk",
                listOf("/self-host export-evidence", "/export", "/scavenge")
            )
        )
    }
}
