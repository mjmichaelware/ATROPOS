/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui.design

/**
 * The 13 canonical verbs from Source Doc 3 Section B.
 *
 * > A client asks "what verbs are valid on this object right now" and gets back
 * > a short contextual list — it never enumerates a global command catalog.
 *
 * Section E turns this into two testable acceptance criteria:
 *
 * 1. every view's rendered action buttons match exactly the valid-verb response
 *    the contract layer returns for the currently-selected object;
 * 2. no view across all four surfaces ever displays more than the 13 canonical
 *    verbs, tested by a static scan at build time.
 *
 * This enum is the single source of truth those tests scan against. ATROPOS's
 * ~150 backend operations remain the literal implementations behind these verbs
 * — that mapping is server-side and invisible to every client, so a new backend
 * capability joins an existing verb rather than growing a fourteenth.
 *
 * Section E also requires each verb to carry an accessible name distinct from
 * its icon: "screen reader announces 'Run', 'Cancel', 'Resolve', never just
 * reads a symbol description". [accessibleName] is that string, and it is
 * mandatory rather than derived from the glyph.
 */
enum class CanonicalVerb(
    val verb: String,
    val accessibleName: String,
    val glyph: String,
    val asciiGlyph: String,
    /**
     * Whether this verb needs explicit confirmation before it executes.
     *
     * Section F open decision 2 records that the full decision table is
     * unresolved: only the Paid/Emergency unlock friction is specified, with
     * "Remove/Prune and Cancel likely need confirm, Run/Search/Lookup likely
     * don't". The defaults below encode that stated leaning and are marked
     * provisional rather than silently invented — see PROVISIONAL below.
     */
    val confirmByDefault: Boolean
) {
    RUN("run", "Run", "▶", ">", confirmByDefault = false),
    CANCEL("cancel", "Cancel", "■", "x", confirmByDefault = true),
    RETRY("retry", "Retry", "↻", "@", confirmByDefault = false),
    RECOVER("recover", "Recover", "⤾", "^", confirmByDefault = false),
    ACKNOWLEDGE("acknowledge", "Acknowledge", "✓", "a", confirmByDefault = false),
    RESOLVE("resolve", "Resolve", "◈", "r", confirmByDefault = false),
    ASSIGN("assign", "Assign or Revoke", "⇄", "=", confirmByDefault = true),
    SEARCH("search", "Search or Look up", "⌕", "/", confirmByDefault = false),
    EXPORT("export", "Export", "⇧", "e", confirmByDefault = false),
    COMPARE("compare", "Compare", "⇹", "c", confirmByDefault = false),
    VERIFY("verify", "Verify", "✔", "v", confirmByDefault = false),
    CONFIGURE("configure", "Configure", "⚙", "s", confirmByDefault = true),
    PRUNE("prune", "Remove or Prune", "⌫", "d", confirmByDefault = true);

    companion object {
        /**
         * PROVISIONAL: [confirmByDefault] encodes Section F open decision 2's
         * stated leaning, not a resolved decision table. Recorded in
         * UI_PARITY_BLOCKERS.md as an open decision so it is not mistaken for
         * settled policy.
         */
        const val CONFIRM_POLICY_STATUS = "PROVISIONAL — Source Doc 3 Section F open decision 2"

        fun byVerb(value: String): CanonicalVerb? =
            entries.firstOrNull { it.verb.equals(value.trim(), ignoreCase = true) }
    }
}

/**
 * Nouns the canonical verbs operate on. Section B: verbs are "parameterized by
 * a noun/object type (Job, Provider, TerritoryViolation, DagNode, Snapshot,
 * etc.)".
 */
enum class CanonicalNoun(val noun: String) {
    JOB("job"),
    PROVIDER("provider"),
    TERRITORY_VIOLATION("territory-violation"),
    DAG_NODE("dag-node"),
    SNAPSHOT("snapshot"),
    PATCH("patch"),
    SESSION("session"),
    TAB("tab"),
    MEMORY_RECORD("memory-record"),
    VERIFICATION("verification"),
    ARTIFACT("artifact"),
    KEY("key"),
    OBSERVATION("observation"),
    BACKLOG_ITEM("backlog-item"),
    SOURCE_ADDRESS("source-address")
}

/**
 * "Which verbs are valid on this object right now."
 *
 * A renderer asks this and draws only what comes back; it never hard-codes an
 * action set. Keeping the query here — rather than in each renderer — is what
 * makes Section E acceptance test 1 checkable from one place.
 *
 * This is the presentation-side contract shape only. It deliberately holds no
 * business logic and reaches no service: per Source Doc 3 §1.1, "no file shall
 * mix presentation concerns with core decision logic". Wiring it to real
 * backend state is the server-side mapping described in Section B, which does
 * not exist yet — so [validVerbs] is documented as NOT WIRED rather than
 * returning invented availability.
 */
object VerbAvailability {
    const val STATUS = "NOT WIRED — awaiting contract/ module from Source Doc 3 Section B"

    /**
     * Verbs structurally applicable to a noun in a given state.
     *
     * This encodes only the per-view "Primary verbs" lists written in Source
     * Doc 3 Section D. It is **not** a live availability check: it cannot know
     * whether a specific object currently permits an action. Callers must treat
     * the result as the upper bound of what a view may offer, never as proof
     * that an action will succeed.
     */
    fun structurallyApplicable(noun: CanonicalNoun, state: RunState): Set<CanonicalVerb> {
        val base = when (noun) {
            CanonicalNoun.JOB -> setOf(
                CanonicalVerb.RUN, CanonicalVerb.CANCEL, CanonicalVerb.RETRY, CanonicalVerb.RECOVER
            )
            CanonicalNoun.PROVIDER -> setOf(
                CanonicalVerb.CONFIGURE, CanonicalVerb.VERIFY, CanonicalVerb.SEARCH
            )
            CanonicalNoun.TERRITORY_VIOLATION -> setOf(
                CanonicalVerb.RESOLVE, CanonicalVerb.ACKNOWLEDGE, CanonicalVerb.ASSIGN
            )
            CanonicalNoun.DAG_NODE -> setOf(
                CanonicalVerb.RUN, CanonicalVerb.SEARCH, CanonicalVerb.VERIFY
            )
            CanonicalNoun.SNAPSHOT -> setOf(
                CanonicalVerb.RUN, CanonicalVerb.COMPARE, CanonicalVerb.VERIFY, CanonicalVerb.PRUNE
            )
            CanonicalNoun.PATCH -> setOf(
                CanonicalVerb.RUN, CanonicalVerb.VERIFY, CanonicalVerb.COMPARE, CanonicalVerb.RECOVER
            )
            CanonicalNoun.SESSION, CanonicalNoun.TAB -> setOf(
                CanonicalVerb.RUN, CanonicalVerb.EXPORT, CanonicalVerb.PRUNE, CanonicalVerb.SEARCH
            )
            CanonicalNoun.MEMORY_RECORD -> setOf(CanonicalVerb.RUN, CanonicalVerb.SEARCH)
            CanonicalNoun.VERIFICATION -> setOf(CanonicalVerb.VERIFY, CanonicalVerb.SEARCH)
            CanonicalNoun.ARTIFACT -> setOf(
                CanonicalVerb.RUN, CanonicalVerb.EXPORT, CanonicalVerb.COMPARE, CanonicalVerb.VERIFY
            )
            CanonicalNoun.KEY -> setOf(CanonicalVerb.CONFIGURE, CanonicalVerb.VERIFY)
            CanonicalNoun.OBSERVATION -> setOf(CanonicalVerb.ACKNOWLEDGE, CanonicalVerb.RESOLVE)
            CanonicalNoun.BACKLOG_ITEM -> setOf(
                CanonicalVerb.RUN, CanonicalVerb.CANCEL, CanonicalVerb.SEARCH
            )
            CanonicalNoun.SOURCE_ADDRESS -> setOf(CanonicalVerb.SEARCH)
        }

        // State narrows the structural set: you cannot cancel what is not going,
        // and you cannot retry what never failed.
        return base.filterTo(mutableSetOf()) { verb ->
            when (verb) {
                CanonicalVerb.CANCEL -> state in setOf(
                    RunState.RUNNING, RunState.QUEUED, RunState.WAITING,
                    RunState.BLOCKED, RunState.RETRYING
                )
                CanonicalVerb.RETRY -> state in setOf(RunState.FAILED, RunState.CANCELLED)
                CanonicalVerb.RECOVER -> state == RunState.FAILED
                CanonicalVerb.RUN -> state !in setOf(RunState.RUNNING, RunState.RETRYING)
                else -> true
            }
        }
    }
}
