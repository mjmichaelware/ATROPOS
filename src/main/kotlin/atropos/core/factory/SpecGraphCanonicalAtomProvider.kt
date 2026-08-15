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
    /**
     * Where the provenance line is recorded.
     *
     * Defaults to the thinking stream rather than to nothing. A no-op default
     * meant that during planning — the one place these atoms actually decide
     * the DAG — every per-source atomization was silent: whether SpecGraph
     * planned a document or the internal extractor did could not be established
     * from any artifact the run produced. That is the exact question this line
     * exists to answer, and the run reported a node count either way.
     */
    private val evidenceSink: (String) -> Unit = { line ->
        atropos.core.thinking.Thinking.stream.emit(atropos.core.thinking.ThinkingDepth.L2, line)
    }
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

        val mapped = atomization.atoms.map { record ->
            record.toInternalAtom(
                projectId = projectId,
                documentId = atomization.documentId,
                promptFingerprint = promptFingerprint,
                promptSpans = promptSpans,
                sourceDocumentSha256 = atomization.sourceSha256
            )
        }

        // The same stage model the internal extractor uses. Applied here too so
        // a plan orders the same way whichever atomizer produced it -- otherwise
        // the canonical path, which is the one meant to be authoritative, is the
        // only one that emits an edgeless graph.
        val staged = atropos.core.planning.InternalAtomDependencyModel.withStageDependencies(mapped)

        // SpecGraph's atoms carry its own vocabulary, not ATROPOS dimensions, so
        // `dimensionOrDefault` maps every one of them to FUNCTIONAL_CONTRACT.
        // That is a real loss and it is silent: a plan of nothing but contracts
        // has no implementation or verification stage to depend on it, which is
        // why a canonical atomization currently yields roots and no edges.
        //
        // The structural fix is to consume SpecGraph's *execution graph* rather
        // than its atoms -- it already stages every atom into CONTRACT ->
        // IMPLEMENTATION -> VERIFICATION and joins them with MUST_PRECEDE. Until
        // that is wired, this at least says so out loud.
        val distinctDimensions = staged.map { it.dimension }.distinct()
        if (staged.size > 1 && distinctDimensions.size == 1) {
            evidenceSink(
                "specgraph source=$sourcePath SKIPPED_SOFT_FAIL:dimension_collapse " +
                    "atoms=${staged.size} all=${distinctDimensions.single().name.lowercase()}; " +
                    "canonical atoms carry no ATROPOS dimension, so the plan is contracts only"
            )
        }

        return CanonicalAtomSet(
            atoms = staged,
            provenance = "canonical_specgraph document=${atomization.documentId} " +
                "atoms=${staged.size} source_sha256=${atomization.sourceSha256} " +
                atropos.core.planning.InternalAtomDependencyModel.render(staged)
        )
    }
}
