/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.factory

import atropos.core.AtroposRepoRootLocator
import atropos.core.planning.CanonicalAtomProvider
import atropos.core.planning.CanonicalAtomSet
import java.nio.file.Path

/**
 * Supplies the planner with atoms from the canonical SpecGraph atomizer.
 *
 * The factory side of [CanonicalAtomProvider]. Planning declares the need;
 * this satisfies it, so planning never imports the factory.
 *
 * Returning null is the ordinary path on a machine with no SpecGraph
 * installed, and it must stay cheap: [SpecGraphAtomizer] checks
 * `SPECGRAPH_ROOT` before touching the filesystem, so an unconfigured
 * repository pays one environment read per source rather than a subprocess.
 */
class SpecGraphCanonicalAtomProvider(
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val atomizer: SpecGraphAtomizer = SpecGraphAtomizer(),
    /** Where the provenance line is recorded. */
    private val evidenceSink: (String) -> Unit = {}
) : CanonicalAtomProvider {

    override fun atomsFor(
        projectId: String,
        sourcePath: String,
        content: String,
        promptFingerprint: String,
        promptSpans: String
    ): CanonicalAtomSet? {
        val atomization = atomizer.atomizeToRecords(
            repoRoot = repoRoot,
            projectId = projectId,
            source = content,
            promptFingerprint = promptFingerprint,
            promptSpans = promptSpans
        )

        // Recorded whether or not it succeeded. "Which atomizer planned this"
        // is the first question asked of any artifact after the fact, and a
        // fallback that left no trace would make every plan look canonical.
        evidenceSink("specgraph source=$sourcePath ${atomization.evidenceLine}")

        if (!atomization.usable) return null

        return CanonicalAtomSet(
            atoms = atomization.atoms.map { record ->
                record.toInternalAtom(
                    projectId = projectId,
                    documentId = atomization.documentId,
                    promptFingerprint = promptFingerprint,
                    promptSpans = promptSpans,
                    sourceDocumentSha256 = atomization.sourceSha256
                )
            },
            provenance = "canonical_specgraph document=${atomization.documentId} " +
                "atoms=${atomization.atoms.size} source_sha256=${atomization.sourceSha256}"
        )
    }
}
