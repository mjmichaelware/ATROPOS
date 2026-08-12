/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.planning

/**
 * Where the authoritative atoms for a plan come from.
 *
 * Declared here, in planning, rather than planning importing the factory. The
 * planner states what it needs — atoms for this text — and something else
 * supplies them. Reversing that would make the planner depend on the app
 * factory, which is backwards: planning is the lower layer.
 *
 * [NONE] is the honest default. A repository with no canonical atomizer
 * configured plans from [InternalAtomExtractor], and the plan says so.
 */
interface CanonicalAtomProvider {

    /**
     * @return null when no canonical atomizer is available or it could not
     *   read this source. Null means *fall back*, not *fail*: an absent
     *   SpecGraph is a configuration, and refusing to plan over it would make
     *   an optional component mandatory.
     */
    fun atomsFor(
        projectId: String,
        sourcePath: String,
        content: String,
        promptFingerprint: String,
        promptSpans: String
    ): CanonicalAtomSet?

    companion object {
        val NONE: CanonicalAtomProvider = object : CanonicalAtomProvider {
            override fun atomsFor(
                projectId: String,
                sourcePath: String,
                content: String,
                promptFingerprint: String,
                promptSpans: String
            ): CanonicalAtomSet? = null
        }
    }
}

/**
 * @param provenance which atomizer produced these, for the plan's evidence.
 *   Never inferred later: after the fact, "was this planned canonically or
 *   internally?" is the first question about any artifact, and a plan that
 *   cannot answer it is a plan nobody can audit.
 */
data class CanonicalAtomSet(
    val atoms: List<InternalAtom>,
    val provenance: String
)
