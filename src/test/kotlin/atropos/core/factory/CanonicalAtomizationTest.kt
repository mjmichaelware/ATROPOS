/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.factory

import atropos.core.planning.AtomDimension
import atropos.core.planning.CanonicalAtomProvider
import atropos.core.planning.InternalAtom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The canonical SpecGraph atomizer used to be run as a checksum: ATROPOS read
 * back an atom count, discarded the atoms, and planned execution from its own
 * extractor. These cover the transport that now carries the atoms themselves,
 * and the fallback that keeps an absent SpecGraph a configuration rather than
 * a failure.
 */
class CanonicalAtomizationTest {

    private fun atomLine(
        id: String = "atom-1",
        dimension: String = "FUNCTIONAL_CONTRACT",
        section: String = "sec-1",
        coordinates: String = "doc.md:10-14",
        dependencies: String = "",
        territory: String = "",
        statement: String = "The queue run must narrate each job."
    ) = listOf("ATOM", id, dimension, section, coordinates, dependencies, territory, statement)
        .joinToString("\t")

    @Test
    fun `an atom line decodes into every field`() {
        val record = CanonicalAtomRecord.decode(
            atomLine(dependencies = "atom-0,atom-9", territory = "src/main/kotlin,src/test/kotlin")
        )!!

        assertEquals("atom-1", record.id)
        assertEquals("sec-1", record.sectionId)
        assertEquals("doc.md:10-14", record.sourceCoordinates)
        assertEquals(listOf("atom-0", "atom-9"), record.dependencies)
        assertEquals(listOf("src/main/kotlin", "src/test/kotlin"), record.territory)
        assertEquals("The queue run must narrate each job.", record.statement)
    }

    @Test
    fun `a multi-line statement survives the transport`() {
        val record = CanonicalAtomRecord.decode(
            atomLine(statement = "first line\\nsecond line\\twith a tab")
        )!!

        assertEquals("first line\nsecond line\twith a tab", record.statement)
    }

    @Test
    fun `a non-atom line decodes to null rather than a broken atom`() {
        assertNull(CanonicalAtomRecord.decode("META\t3\tabc\tdoc-1"))
        assertNull(CanonicalAtomRecord.decode("SCHEMA\tid,body,kind"))
        assertNull(CanonicalAtomRecord.decode(""))
        assertNull(CanonicalAtomRecord.decode("ATOM\ttoo\tfew"))
    }

    @Test
    fun `an atom with no id is refused`() {
        assertNull(CanonicalAtomRecord.decode(atomLine(id = "   ")))
    }

    @Test
    fun `an unknown dimension becomes a code-writing atom rather than failing the plan`() {
        assertEquals(
            AtomDimension.FUNCTIONAL_CONTRACT,
            CanonicalAtomRecord.dimensionOrDefault("SOME_NEW_UPSTREAM_DIMENSION")
        )
    }

    @Test
    fun `dimension names tolerate the separators an upstream vocabulary may use`() {
        assertEquals(AtomDimension.STATE_MODEL, CanonicalAtomRecord.dimensionOrDefault("state-model"))
        assertEquals(AtomDimension.STATE_MODEL, CanonicalAtomRecord.dimensionOrDefault("State Model"))
        assertEquals(AtomDimension.ERROR_MODEL, CanonicalAtomRecord.dimensionOrDefault("ERROR_MODEL"))
    }

    @Test
    fun `a canonical record maps into the shape the planner already consumes`() {
        val internal = CanonicalAtomRecord.decode(atomLine(territory = "src/main/kotlin"))!!
            .toInternalAtom(
                projectId = "atropos",
                documentId = "doc-7",
                promptFingerprint = "prompt-0123456789abcdef",
                promptSpans = "requirement",
                sourceDocumentSha256 = "a".repeat(64)
            )

        assertEquals("atom-1", internal.id)
        assertEquals("atropos", internal.projectId)
        assertEquals("doc-7", internal.documentId)
        assertEquals(AtomDimension.FUNCTIONAL_CONTRACT, internal.dimension)
        assertEquals(listOf("src/main/kotlin"), internal.territory)
        assertEquals("prompt-0123456789abcdef", internal.promptFingerprint)
    }

    @Test
    fun `the in-repo atomizer is found without an environment variable`() {
        // SpecGraph lives at apps/specgraph-foundry. It used to be reachable
        // only through SPECGRAPH_ROOT, so an unset variable skipped the
        // canonical atomizer on every run while it sat in the tree.
        val atomization = SpecGraphAtomizer().atomizeToRecords(
            repoRoot = atropos.core.AtroposRepoRootLocator.resolve(),
            projectId = "atropos-test-inrepo",
            source = "The queue run must narrate each job.",
            promptFingerprint = "prompt-0123456789abcdef",
            promptSpans = "requirement"
        )

        assertFalse(
            atomization.evidenceLine.contains("SPECGRAPH_ROOT_unset"),
            "the atomizer is in this repository and must not report itself missing"
        )
    }

    @Test
    fun `the atomizer is located from the installation, not the planned project`() {
        // A factory run passes the generated project as repoRoot. Resolving
        // the atomizer against that looked for SpecGraph inside the thing
        // SpecGraph was meant to plan, and reported root_missing on every run.
        val generatedProject = java.nio.file.Files.createTempDirectory("atropos-generated")

        val atomization = SpecGraphAtomizer().atomizeToRecords(
            repoRoot = generatedProject,
            projectId = "atropos-test-elsewhere",
            source = "The provider client must pin certificates.",
            promptFingerprint = "prompt-0123456789abcdef",
            promptSpans = "requirement"
        )

        assertFalse(
            atomization.evidenceLine.contains("root_missing"),
            "the atomizer lives with the installation, not with the project being planned"
        )
    }

    @Test
    fun `an explicitly wrong SPECGRAPH_ROOT falls back rather than failing`() {
        // The environment variable still overrides, so a bad one must degrade
        // to the internal extractor rather than taking the run down.
        val atomization = SpecGraphAtomizer(
            specGraphRootOverride = "/definitely/not/a/specgraph/checkout"
        ).atomizeToRecords(
            repoRoot = java.nio.file.Files.createTempDirectory("atropos-any"),
            projectId = "atropos-test-missing",
            source = "a requirement",
            promptFingerprint = "prompt-0123456789abcdef",
            promptSpans = "requirement"
        )

        assertFalse(atomization.usable)
        assertTrue(atomization.evidenceLine.startsWith("SKIPPED_SOFT_FAIL:"))
        assertTrue(atomization.evidenceLine.contains("internal DAG fallback required"))
    }

    @Test
    fun `the provider returns null when SpecGraph cannot plan, and records why`() {
        val evidence = mutableListOf<String>()
        val provider = SpecGraphCanonicalAtomProvider(
            repoRoot = java.nio.file.Files.createTempDirectory("atropos-no-specgraph"),
            evidenceSink = evidence::add
        )

        val atoms = provider.atomsFor(
            projectId = "atropos-test",
            sourcePath = "nl-prompt",
            content = "a requirement",
            promptFingerprint = "prompt-0123456789abcdef",
            promptSpans = "requirement"
        )

        assertNull(atoms, "null means fall back, not fail")
        assertEquals(1, evidence.size)
        assertTrue(
            evidence.single().contains("source=nl-prompt"),
            "a fallback that left no trace would make every plan look canonical"
        )
    }

    @Test
    fun `the no-op provider is the honest default`() {
        assertNull(
            CanonicalAtomProvider.NONE.atomsFor("p", "s", "content", "prompt-0123456789abcdef", "spans")
        )
    }

    @Test
    fun `provider dimension fill is attempted for every canonical atom`() {
        val responses = mutableListOf<String>()
        val provider = SpecGraphCanonicalAtomProvider(
            dimensionCompletion = { prompt, _ ->
                responses += prompt
                if (responses.size == 1) "STATE_MODEL" else "TESTS_ACCEPTANCE"
            }
        )
        val atoms = provider.fillDimensions(
            atoms = listOf(
                InternalAtom(
                    id = "atom-1", projectId = "p", documentId = "d", sectionId = "s",
                    dimension = AtomDimension.FUNCTIONAL_CONTRACT, statement = "record state",
                    sourceCoordinates = "doc:1"
                ),
                InternalAtom(
                    id = "atom-2", projectId = "p", documentId = "d", sectionId = "s",
                    dimension = AtomDimension.FUNCTIONAL_CONTRACT, statement = "run tests",
                    sourceCoordinates = "doc:2"
                )
            ),
            promptFingerprint = "prompt-0123456789abcdef",
            sourcePath = "prompt"
        )

        assertEquals(2, responses.size)
        assertEquals(listOf(AtomDimension.STATE_MODEL, AtomDimension.TESTS_ACCEPTANCE), atoms.map { it.dimension })
    }

    @Test
    fun `invalid provider dimension is rejected instead of defaulted`() {
        val provider = SpecGraphCanonicalAtomProvider()
        assertNull(provider.parseDimension("not-a-dimension"))
    }
}
