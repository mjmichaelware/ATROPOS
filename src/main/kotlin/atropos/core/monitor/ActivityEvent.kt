/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.monitor

import java.time.Instant

/**
 * One state change in the activity monitor's single stream.
 *
 * `C3-P19`: "Activity monitor shows every plan/provider/tool/diff/test/
 * verifier/artifact/deploy state" — and its IMPL note is the constraint that
 * matters: "Monitor is presentation of existing evidence; no second event
 * system."
 *
 * So this type carries no events of its own. It is a normalised *view* over
 * records the journal, verifier, artifact pipeline and run observer already
 * produce, which is why every field is descriptive and none is a source of
 * truth. A monitor that minted its own events would immediately disagree with
 * the evidence it claims to display, and the disagreement would be invisible
 * because both sides would look internally consistent.
 */
data class ActivityEvent(
    val id: String,
    val at: Instant,
    val stage: ActivityStage,
    /** The subject: a node id, provider name, artifact id. */
    val subject: String,
    /** The completion vocabulary term, never a free-form status. */
    val outcome: String,
    val detail: String
)

/**
 * The stages `C3-P19` enumerates.
 *
 * Fixed rather than open so the monitor cannot silently grow a category that no
 * evidence source actually produces — an unmapped stage would render as a gap
 * the operator could not explain.
 */
enum class ActivityStage(val canonical: String) {
    PLAN("plan"),
    PROVIDER("provider"),
    TOOL("tool"),
    DIFF("diff"),
    TEST("test"),
    VERIFIER("verifier"),
    ARTIFACT("artifact"),
    DEPLOY("deploy");

    companion object {
        fun fromCanonical(term: String): ActivityStage? =
            entries.firstOrNull { it.canonical.equals(term.trim(), ignoreCase = true) }
    }
}

/**
 * The monitor's ordered view.
 *
 * Ordering is by timestamp then id, so a rerun over the same evidence produces
 * the same sequence. A monitor whose order depended on collection timing would
 * make two operators looking at the same run see different histories.
 */
class ActivityStream(private val events: List<ActivityEvent>) {

    fun ordered(): List<ActivityEvent> = events.sortedWith(compareBy({ it.at }, { it.id }))

    fun stage(stage: ActivityStage): List<ActivityEvent> = ordered().filter { it.stage == stage }

    /** Stages with no event yet, so the monitor can show a gap as a gap. */
    fun missingStages(): List<ActivityStage> =
        ActivityStage.entries.filterNot { stage -> events.any { it.stage == stage } }

    /**
     * True when every stage has reported at least once.
     *
     * Deliberately not called "healthy": a complete stream says the pipeline
     * ran end to end, not that it succeeded. Outcome is a separate question and
     * conflating them would let a fully-failed run look complete.
     */
    fun isComplete(): Boolean = missingStages().isEmpty()

    fun render(): String =
        "activity events=${events.size} stages=${ActivityStage.entries.size - missingStages().size}" +
            "/${ActivityStage.entries.size} complete=${isComplete()}"
}
