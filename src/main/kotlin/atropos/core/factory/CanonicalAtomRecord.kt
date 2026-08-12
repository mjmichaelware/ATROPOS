/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.factory

import atropos.core.planning.AtomDimension
import atropos.core.planning.InternalAtom

/**
 * One atom as the canonical SpecGraph atomizer produced it.
 *
 * ATROPOS already ran `specgraph_foundry.atoms.AtomService.extract_document`
 * and read back two numbers from it — the atom count and the source hash — then
 * discarded the atoms and re-derived its own with
 * [atropos.core.planning.InternalAtomExtractor]. The canonical atomizer was
 * being used as a checksum. Every plan that reached execution was second-hand.
 *
 * The transport is tab-separated lines rather than JSON, matching
 * [atropos.core.auth.AuthorityFingerprint] and
 * [atropos.core.interrupt.FrozenRun]. That is a deliberate choice over adding a
 * JSON reader: the boundary is a subprocess whose output has to be audited by
 * eye when it goes wrong, and one field per column is readable in a way nested
 * JSON is not. It also means no parser to get wrong.
 */
data class CanonicalAtomRecord(
    val id: String,
    val dimension: String,
    val sectionId: String,
    val sourceCoordinates: String,
    val dependencies: List<String>,
    val territory: List<String>,
    val statement: String
) {
    /**
     * Converts to the shape the existing planner consumes.
     *
     * Mapping into [InternalAtom] rather than teaching the authority graph and
     * the synthesizer about a second atom type: those two already know how to
     * build a graph and turn it into executable nodes, and a parallel path
     * through them would be the duplicate planner this whole change exists to
     * remove.
     */
    fun toInternalAtom(
        projectId: String,
        documentId: String,
        promptFingerprint: String,
        promptSpans: String,
        sourceDocumentSha256: String
    ): InternalAtom = InternalAtom(
        id = id,
        projectId = projectId,
        documentId = documentId,
        sectionId = sectionId,
        dimension = dimensionOrDefault(dimension),
        statement = statement,
        sourceCoordinates = sourceCoordinates,
        dependencies = dependencies,
        territory = territory,
        promptFingerprint = promptFingerprint,
        promptSpans = promptSpans,
        sourceDocumentSha256 = sourceDocumentSha256
    )

    companion object {
        const val ATOM_PREFIX = "ATOM"
        const val META_PREFIX = "META"
        const val SCHEMA_PREFIX = "SCHEMA"

        /**
         * An unrecognised dimension becomes [AtomDimension.FUNCTIONAL_CONTRACT].
         *
         * The canonical atomizer's vocabulary is its own and may name a
         * dimension ATROPOS has no enum for. Refusing the whole plan over one
         * unknown label would make every vocabulary addition upstream a total
         * outage here; defaulting to the functional contract makes it an
         * ordinary code-writing atom, which is the safe reading — it still
         * passes every gate, it just is not specially classified.
         */
        fun dimensionOrDefault(raw: String): AtomDimension =
            AtomDimension.entries.firstOrNull {
                it.name.equals(raw.trim().replace('-', '_').replace(' ', '_'), ignoreCase = true)
            } ?: AtomDimension.FUNCTIONAL_CONTRACT

        /** Reverses the escaping applied by the emitting script. */
        fun unescape(field: String): String =
            field.replace("\\t", "\t").replace("\\n", "\n").replace("\\\\", "\\")

        fun decode(line: String): CanonicalAtomRecord? {
            val parts = line.split('\t')
            if (parts.size < 8 || parts[0] != ATOM_PREFIX) return null
            val id = parts[1].trim().ifBlank { return null }
            return CanonicalAtomRecord(
                id = id,
                dimension = parts[2].trim(),
                sectionId = parts[3].trim(),
                sourceCoordinates = parts[4].trim(),
                dependencies = splitList(parts[5]),
                territory = splitList(parts[6]),
                statement = unescape(parts[7]).trim()
            )
        }

        private fun splitList(field: String): List<String> =
            field.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }
}

/**
 * What one canonical atomization produced.
 *
 * @param atoms empty when the atomizer was unavailable or refused. Empty is not
 *   an error on its own — a repository with no SpecGraph installed is a normal
 *   configuration — but it does mean the caller must fall back rather than plan
 *   from nothing.
 * @param observedSchema the keys the atomizer's own atom objects carried, when
 *   they did not match what this bridge expects. Present so a schema change
 *   upstream produces one diagnostic run instead of a silent empty plan: the
 *   field names ATROPOS looked for are a guess about somebody else's API, and a
 *   guess that fails should say what it saw.
 */
data class CanonicalAtomization(
    val atoms: List<CanonicalAtomRecord>,
    val sourceSha256: String,
    val documentId: String,
    val evidenceLine: String,
    val observedSchema: List<String> = emptyList()
) {
    val usable: Boolean get() = atoms.isNotEmpty()
}
