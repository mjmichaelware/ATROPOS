/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui.design

/**
 * The nine status terms Source Doc 4 requires, and the single place where they
 * are tied to [RunState].
 *
 * Doc 4 names status from the operator's side — what *their* work is doing —
 * rather than from the scheduler's: Idle, Planning, Waiting, Working, Review
 * Required, Blocked, Completed, Failed, Cancelled. [RunState] names the same
 * states from the runtime's side and predates that list, so two of the names
 * differ ("Working" is [RunState.RUNNING], "Completed" is
 * [RunState.COMPLETE]).
 *
 * Those differences are why this file exists. Left unwritten, each caller
 * invents its own translation, and the translations drift: one surface shows a
 * planning job as queued, another as running, and the operator cannot trust
 * either. This file is that translation, written once. It deliberately holds no
 * states of its own — [RunState] remains the only enum of statuses, and a term
 * Doc 4 names is either mapped to a state that already exists or reported by
 * [conformance] as unresolved. Adding a state here instead of to [RunState]
 * would recreate the drift this file prevents.
 *
 * The second half of the rule is accessibility. Source Doc 3 Section E: status
 * colour "pairs with a redundant non-color signal — icon shape and text label".
 * [signal] is that non-colour channel, available for any state without touching
 * a palette, so a caller that cannot or must not use colour still has the whole
 * signal. It returns data, never escape sequences: painting belongs to
 * [Surface], and keeping it out of here is what lets a screen reader, a log
 * line, and a `NO_COLOR` terminal share one source of truth.
 */
object HoeStatusVocabulary {

    /**
     * Doc 4's nine terms in the doc's own order, in the hyphenated wire form the
     * CLI, GUI and web surfaces already share (`review-required`, never
     * `REVIEW_REQUIRED`).
     *
     * These strings must stay identical to the domain's own status wire forms
     * (`atropos.core.project.ProjectStatus.canonical`). That agreement is not
     * asserted here on purpose: this package deliberately imports nothing from
     * core, so the cross-layer check belongs in a test that can see both —
     * [conformance] accepts the domain's terms for exactly that reason.
     */
    val CANONICAL_TERMS: List<String> = listOf(
        "idle",
        "planning",
        "waiting",
        "working",
        "review-required",
        "blocked",
        "completed",
        "failed",
        "cancelled"
    )

    /**
     * Doc 4 term to state. Each of the nine maps to a different [RunState]:
     * the terms describe nine genuinely different situations, so any two of them
     * sharing a state means one of them has stopped being reportable.
     */
    private val BY_TERM: Map<String, RunState> = mapOf(
        "idle" to RunState.IDLE,
        "planning" to RunState.PLANNING,
        "waiting" to RunState.WAITING,
        "working" to RunState.RUNNING,
        "review-required" to RunState.REVIEW_REQUIRED,
        "blocked" to RunState.BLOCKED,
        "completed" to RunState.COMPLETE,
        "failed" to RunState.FAILED,
        "cancelled" to RunState.CANCELLED
    )

    /**
     * Runtime-side spellings accepted as input but never presented as Doc 4
     * terms.
     *
     * [RunState] has states Doc 4 does not name — queued, retrying, unknown —
     * and two states Doc 4 spells differently. Accepting all of them keeps
     * callers from silently degrading a name they merely spelled the other way
     * to "unknown", which would report a running job as unreadable.
     */
    private val ACCEPTED_ALIASES: Map<String, RunState> = mapOf(
        "running" to RunState.RUNNING,
        "complete" to RunState.COMPLETE,
        "queued" to RunState.QUEUED,
        "retrying" to RunState.RETRYING,
        "unknown" to RunState.UNKNOWN
    )

    /**
     * Resolves a status term to its state, or `null` when the term is not one
     * this vocabulary knows.
     *
     * Tolerant about spelling — case, surrounding space, and `_`/space/`-` word
     * separators all resolve to the same term — because these strings arrive
     * from enum names, JSON payloads and human-typed input. Not tolerant about
     * meaning: an unrecognised term returns `null` so the caller must decide
     * what to claim, rather than being handed a plausible-looking state it did
     * not earn.
     */
    fun resolve(term: String): RunState? {
        val key = normalise(term)
        return BY_TERM[key] ?: ACCEPTED_ALIASES[key]
    }

    /**
     * The Doc 4 term for a state, or `null` when Doc 4 does not name it.
     *
     * The inverse of [resolve], and the direction a surface actually needs:
     * runtime state is what the engine holds, a Doc 4 term is what the operator
     * reads. Without this, every surface re-derives the mapping from the enum
     * name and the two spellings that differ — `RUNNING`/"working" and
     * `COMPLETE`/"completed" — silently drift apart again.
     *
     * States Doc 4 does not name (queued, retrying, unknown) return `null`
     * rather than an invented term: a caller that must render something should
     * fall back to the runtime spelling knowingly, not be handed a Doc 4 word
     * the vocabulary never promised.
     */
    fun termFor(state: RunState): String? =
        BY_TERM.entries.firstOrNull { it.value == state }?.key

    /**
     * Like [resolve], but answers [RunState.UNKNOWN] for an unrecognised term.
     *
     * For surfaces that must render something. [RunState.UNKNOWN] is the honest
     * answer required by the base doc's no-fabrication rule: an untranslatable
     * status is not idle and is certainly not complete.
     */
    fun resolveOrUnknown(term: String): RunState = resolve(term) ?: RunState.UNKNOWN

    /**
     * The non-colour half of a status: its icon and its words.
     *
     * @param asciiOnly selects [RunState.asciiGlyph] for terminals and log sinks
     *   that cannot be trusted with the Unicode glyph.
     */
    fun signal(state: RunState, asciiOnly: Boolean = false): StatusSignal = StatusSignal(
        state = state,
        icon = if (asciiOnly) state.asciiGlyph else state.glyph,
        text = state.label
    )

    /**
     * The non-colour signal for a Doc 4 term, or `null` if the term is unknown.
     * Same refusal to guess as [resolve].
     */
    fun signalFor(term: String, asciiOnly: Boolean = false): StatusSignal? =
        resolve(term)?.let { signal(it, asciiOnly) }

    /**
     * Every Doc 4 term paired with its non-colour signal, in doc order.
     *
     * The legend a view can render to teach the vocabulary once instead of
     * leaving each glyph to be guessed. Terms that fail to resolve are absent
     * rather than filled in — [conformance] is what reports them.
     */
    fun legend(asciiOnly: Boolean = false): List<StatusSignal> =
        CANONICAL_TERMS.mapNotNull { signalFor(it, asciiOnly) }

    /**
     * Checks the two properties this vocabulary exists to guarantee: that every
     * required term resolves to a distinct state, and that every state carries a
     * usable non-colour signal.
     *
     * @param requiredTerms the terms that must resolve. Defaults to
     *   [CANONICAL_TERMS]; pass the domain's own status wire forms to prove the
     *   presentation vocabulary and the domain have not drifted apart.
     */
    fun conformance(requiredTerms: Collection<String> = CANONICAL_TERMS): VocabularyConformance {
        val resolved: Map<String, RunState?> = requiredTerms.associateWith { resolve(it) }

        val collisions: Map<RunState, List<String>> = resolved
            .mapNotNull { (term, state) -> state?.let { it to term } }
            .groupBy({ it.first }, { it.second })
            .filterValues { it.size > 1 }

        return VocabularyConformance(
            unresolvedTerms = resolved.filterValues { it == null }.keys.sorted(),
            collidingTerms = collisions,
            statesMissingText = RunState.entries.filter { it.label.isBlank() },
            statesMissingIcon = RunState.entries.filter {
                it.glyph.isBlank() || it.asciiGlyph.isBlank()
            }
        )
    }

    /** Case, spacing and `_`/`-` are noise; the term underneath is what matters. */
    private fun normalise(term: String): String =
        term.trim().lowercase().replace(' ', '-').replace('_', '-')
}

/**
 * A status expressed without colour: an icon shape and the words for it.
 *
 * A pair rather than one preformatted string, so the caller can place the two
 * parts where its medium needs them — aligned columns, a screen-reader name, a
 * log field — while still being unable to obtain one without the other. There is
 * no constructor path that yields a colour-only status.
 */
data class StatusSignal(
    val state: RunState,
    val icon: String,
    val text: String
)

/**
 * What [HoeStatusVocabulary.conformance] found.
 *
 * Findings are returned as the offending terms and states, not as a bare
 * boolean, so a failure names what to fix instead of only reporting that
 * something is wrong. Nothing here logs or prints: the caller — usually a test —
 * decides what a violation means.
 */
data class VocabularyConformance(
    /** Required terms with no state to resolve to. A term here is invisible to every surface. */
    val unresolvedTerms: List<String>,
    /**
     * States that more than one required term resolves to, keyed by state.
     *
     * A collision means two distinct situations render identically, which is how
     * "review required" work disappears into a pile of "waiting".
     */
    val collidingTerms: Map<RunState, List<String>>,
    /** States with no text label — colour and shape only, unreadable aloud. */
    val statesMissingText: List<RunState>,
    /** States missing a Unicode or ASCII glyph, leaving colour to carry the signal alone. */
    val statesMissingIcon: List<RunState>
) {
    /** True only when nothing above was found. */
    val conformant: Boolean
        get() = unresolvedTerms.isEmpty() &&
            collidingTerms.isEmpty() &&
            statesMissingText.isEmpty() &&
            statesMissingIcon.isEmpty()

    /** One-line human summary for a failure message. Empty when [conformant]. */
    fun describeViolations(): String = buildList {
        if (unresolvedTerms.isNotEmpty()) {
            add("unresolved terms: ${unresolvedTerms.joinToString(", ")}")
        }
        collidingTerms.forEach { (state, terms) ->
            add("${terms.joinToString(", ")} all resolve to ${state.name}")
        }
        if (statesMissingText.isNotEmpty()) {
            add("states without a label: ${statesMissingText.joinToString(", ") { it.name }}")
        }
        if (statesMissingIcon.isNotEmpty()) {
            add("states without a glyph: ${statesMissingIcon.joinToString(", ") { it.name }}")
        }
    }.joinToString("; ")
}
