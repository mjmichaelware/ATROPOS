/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.planning

/**
 * Context retrieved for one atom, attached before it executes.
 *
 * Declared in planning, satisfied elsewhere — the same shape as
 * [CanonicalAtomProvider], and for the same reason: the planner states what it
 * needs and the storage layer supplies it, rather than planning importing the
 * lakehouse.
 *
 * The retrieval is driven by the atom's *own language*. Not the operator's
 * prompt, and not the research document: by the time a DAG exists the prompt
 * has already been decomposed, and each atom concerns something narrower than
 * the request that produced it. An atom about TLS wants the TLS shelf whether
 * or not the prompt ever said the word.
 */
interface AtomContextProvider {

    /**
     * @return context for this atom, or an empty list when nothing matched.
     *   Empty is ordinary: most atoms concern the repository rather than
     *   anything in the lakehouse, and an atom with no retrieved context is
     *   not a failed atom.
     */
    fun contextFor(atom: InternalAtom): List<AtomContext>

    companion object {
        val NONE: AtomContextProvider = object : AtomContextProvider {
            override fun contextFor(atom: InternalAtom): List<AtomContext> = emptyList()
        }
    }
}

/**
 * One retrieved document, and the provenance that makes it auditable.
 *
 * @param status `HIT`, `MISS` or `REJECT`, carried verbatim from the retriever.
 *   A miss is recorded rather than dropped: an atom that *should* have had
 *   context and did not is a different situation from one that never asked,
 *   and only the record distinguishes them afterwards.
 * @param sha256 null on anything but a hit. Present so the exact bytes an atom
 *   was given can be re-fetched and checked — context that changed silently
 *   between planning and execution would make the plan unreproducible.
 */
data class AtomContext(
    val path: String,
    val sha256: String?,
    val status: String,
    val reason: String,
    val content: String
) {
    val hit: Boolean get() = status == "HIT"

    /** The line that goes into a node's payload and its evidence. */
    fun provenance(): String =
        "lakehouse path=$path status=$status" +
            (sha256?.let { " sha256=${it.take(16)}" } ?: "") +
            " reason=$reason"
}
