/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.thinking

import java.nio.file.Files
import java.nio.file.Path

/**
 * What the pipeline says about itself while it runs.
 *
 * [Thinking] gives engine code two verbs, `step` and `detail`, and that was
 * enough to prove a run was alive and no more. An operator watching `/thinking
 * 3` through a fourteen-minute factory run could see that subprocesses started
 * and that providers were asked, and could not see the thing they had actually
 * asked for: how many atoms came out of their document, what was done to each
 * one, what was looked up, what was written to disk. The stages knew all of it
 * and none of them said any of it.
 *
 * This is the vocabulary for saying it. Not a second stream -- everything here
 * lands in [Thinking.stream] like every other thought -- but a fixed set of
 * shapes, so that a count is always phrased as a count and an artifact is
 * always phrased as an artifact, and an operator scanning a thousand lines can
 * find the number they want without reading the sentences around it.
 *
 * ## Why shapes and not free text
 *
 * A trace assembled from ad-hoc strings drifts. One stage says "found 390
 * atoms", the next says "atom_count=390", a third says "390". All three are
 * true and the three together are unreadable, and no test can assert on any of
 * them. These methods are the contract: a stage that wants to report a count
 * calls [counted], and the phrasing is decided once, here.
 *
 * ## Depth
 *
 * [stage] and [counted] are L2 -- the shape of the run. Everything else is L3,
 * because per-atom and per-artifact narration on a four-hundred-atom document
 * is thousands of lines, which is exactly what `/thinking 3` promises and
 * exactly what would drown `/thinking 2`.
 */
class PipelineNarrator(private val category: String) {

    /**
     * The handful of facts an operator wants without asking for detail.
     *
     * L1 is the *default* depth, and until this existed nothing in the
     * pipeline vocabulary emitted there -- so an operator who never touched
     * `/thinking` saw none of the factory's narration at all. The stages were
     * speaking to a level nobody was listening at.
     *
     * Reserved for run-level answers: how many atoms came out of the document,
     * how many nodes are in the graph, whether the run finished. Anything that
     * happens per atom or per file belongs further down the ladder, because
     * L1 stops being an outline the moment it has more than a screen in it.
     */
    fun headline(what: String) {
        runCatching { Thinking.stream.emit(ThinkingDepth.L1, what, category = category) }
    }

    /** A stage of the pipeline beginning. The spine of the trace. */
    fun stage(what: String) = Thinking.step(category, what)

    /**
     * How many of something there are.
     *
     * @param of the population it was drawn from, when there is one -- "390 of
     *   593 statements" says something that "390 statements" does not, namely
     *   that 203 were rejected and are worth asking about.
     */
    fun counted(noun: String, quantity: Int, of: Int? = null) {
        val population = of?.let { " of $it" }.orEmpty()
        Thinking.step(category, "$quantity$population $noun")
    }

    /** A count worth seeing at the default depth. */
    fun headlineCount(noun: String, quantity: Int, of: Int? = null) {
        headline("$quantity${of?.let { " of $it" }.orEmpty()} $noun")
    }

    /** Work done to one item among many. Thousands of these per run, by design. */
    fun item(index: Int, total: Int, id: String, what: String) =
        Thinking.detail(category, "[$index/$total] $id — $what")

    /** A lookup against a store, and what came back. */
    fun lookup(store: String, query: String, hits: Int) =
        Thinking.detail(
            category,
            "$store ← ${query.take(LOOKUP_QUERY_CELLS)}: " +
                if (hits == 0) "nothing" else "$hits ${if (hits == 1) "hit" else "hits"}"
        )

    /** A judgement with a number behind it. */
    fun confidence(subject: String, value: Double, because: String) =
        Thinking.detail(category, "$subject confidence ${"%.2f".format(value)} — $because")

    /**
     * A file this run produced.
     *
     * The size is read rather than passed, because a stage reporting the size
     * it meant to write is reporting its intention. An operator asking "did it
     * write the handoff?" wants the disk's answer. A file that is not there
     * says so, which is the more useful line of the two.
     */
    fun artifact(what: String, path: Path) {
        val detail = runCatching {
            if (Files.exists(path)) "${Files.size(path)} bytes" else "MISSING"
        }.getOrDefault("unreadable")
        Thinking.detail(category, "wrote $what → $path ($detail)")
    }

    /** A decision not to do something, and why. Silence here reads as a bug. */
    fun skipped(what: String, because: String) =
        Thinking.detail(category, "skipped $what — $because")

    /** Something that went wrong without stopping the run. */
    fun trouble(what: String, detail: String) =
        Thinking.step(category, "$what — $detail")

    private companion object {
        /**
         * A query is narrated to show what was asked, not to reproduce it. A
         * full research prompt is several hundred characters and turns the
         * trace into a wall.
         */
        const val LOOKUP_QUERY_CELLS = 60
    }
}

/**
 * The narrators the pipeline uses, named for the stage rather than made up at
 * each call site.
 *
 * A category typo is invisible -- the thought still publishes, it just stops
 * lining up in the renderer's column and stops being filterable. Naming them
 * once makes that impossible.
 */
object Narrate {
    val ingest = PipelineNarrator("ingest")
    val atomize = PipelineNarrator("atomize")
    val dimension = PipelineNarrator("dimension")
    val research = PipelineNarrator("research")
    val lakehouse = PipelineNarrator("lakehouse")
    val plan = PipelineNarrator("plan")
    val build = PipelineNarrator("build")
    val verify = PipelineNarrator("verify")
    val evidence = PipelineNarrator("evidence")
    val provider = PipelineNarrator("provider")
}
