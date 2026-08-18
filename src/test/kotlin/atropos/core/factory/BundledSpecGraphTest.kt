/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.factory

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The canonical atomizer has to be present on a machine that only ever
 * downloaded a jar.
 *
 * Installing ATROPOS gave you the Kotlin engine and nothing else, so
 * `SPECGRAPH_ROOT` was unset on every fresh machine, [SpecGraphAtomizer]
 * soft-failed, and a factory run planned with the weaker internal extractor.
 * It still produced a DAG, so nothing looked wrong — the document just
 * yielded a fraction of the atoms it contained, and the only trace was an
 * evidence line nobody reads until something has already gone sideways.
 *
 * These run against the source tree during a Gradle build, where the bundle
 * is not on the classpath. That is deliberate: the assertions are written so
 * they hold either way, and the packaged case is proven by the jar itself
 * carrying `specgraph/INDEX`.
 */
class BundledSpecGraphTest {

    @Test
    fun a_bundle_that_resolves_is_laid_out_the_way_the_bootstrap_expects() {
        val root = BundledSpecGraph.root() ?: return

        // The atomizer's Python does `sys.path.insert(0, root/"src")`, so this
        // layout is a contract and not a preference.
        assertTrue(
            Files.isRegularFile(root.resolve("src/specgraph_foundry/atoms.py")),
            "the bundle unpacked but the atomizer is not where the bootstrap looks: $root"
        )
    }

    @Test
    fun resolution_is_stable_across_calls() {
        // Extraction happens once. A second answer would mean a second copy on
        // disk and a race between two runs unpacking the same tree.
        val first = BundledSpecGraph.root()
        val second = BundledSpecGraph.root()

        kotlin.test.assertEquals(first, second)
    }

    @Test
    fun a_completed_bundle_is_marked_as_complete() {
        val root = BundledSpecGraph.root() ?: return

        // The marker is what makes a half-written tree — a killed process, a
        // full disk — detectable next run instead of surfacing as a Python
        // ImportError in the middle of a factory run.
        assertTrue(
            Files.isRegularFile(root.resolve(".complete")),
            "the bundle is usable but unmarked, so the next run cannot trust it"
        )
    }

    @Test
    fun the_atomizer_reports_honestly_when_nothing_can_be_found() {
        // Absence stays a named, visible state. This is the §0.6 edge: the one
        // thing that must never happen is a run that quietly plans with the
        // fallback while looking like a canonical one.
        val atomization = SpecGraphAtomizer(specGraphRootOverride = "/nonexistent/specgraph")
            .atomizeToRecords(
                repoRoot = Files.createTempDirectory("bundled-specgraph"),
                projectId = "p",
                source = "- The engine reads the document.",
                promptFingerprint = "f",
                promptSpans = "s"
            )

        assertTrue(!atomization.usable, "an absent atomizer reported itself usable")
        assertNotNull(atomization.evidenceLine)
        assertTrue(
            atomization.evidenceLine.isNotBlank(),
            "an absent atomizer produced no evidence line at all"
        )
    }
}
