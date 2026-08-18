/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.agent

import atropos.core.dag.DagExecutionService
import atropos.core.factory.CanonicalAtomRecord
import atropos.core.factory.CanonicalAtomization
import atropos.core.ingest.IngestTerritory
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The behaviour that was missing: a self-host goal naming a document gets a
 * graph built from that document, not the three-node cradle.
 */
class SelfHostDocumentPlanTest {

    private val repoRoot: Path = Files.createTempDirectory("atropos-selfhost-doc")
    private val clock = { Instant.parse("2026-01-01T00:00:00Z") }

    private fun record(task: String) = GoalRunRecord(
        id = "shg-test",
        goalId = "shg-test",
        task = task,
        provider = "self-host",
        status = GoalRunStatus.RUNNING,
        activePhase = "11",
        createdAt = clock(),
        updatedAt = clock(),
        metaFile = repoRoot.resolve("shg-test.meta")
    )

    private fun atom(id: String, statement: String, dependsOn: List<String> = emptyList()) =
        CanonicalAtomRecord(
            id = id,
            dimension = "FUNCTIONAL_CONTRACT",
            sectionId = "",
            sourceCoordinates = "",
            dependencies = dependsOn,
            territory = emptyList(),
            statement = statement
        )

    /** An atomization that returns what the test says, without running python. */
    private fun atomizerReturning(atoms: List<CanonicalAtomRecord>):
        (Path, String, String, String, String) -> CanonicalAtomization = { _, _, _, _, _ ->
        CanonicalAtomization(
            atoms = atoms,
            sourceSha256 = "sha",
            documentId = "doc",
            evidenceLine = if (atoms.isEmpty()) "SKIPPED_SOFT_FAIL:none" else "PASS:test"
        )
    }

    private fun writeDocument(name: String, body: String): Path {
        val path = repoRoot.resolve(name)
        Files.createDirectories(path.parent)
        Files.writeString(path, body)
        return path
    }

    private fun planWith(atoms: List<CanonicalAtomRecord>) =
        SelfHostDocumentPlan(repoRoot = repoRoot, atomize = atomizerReturning(atoms))

    @Test
    fun a_goal_naming_a_document_is_atomized_from_it() {
        writeDocument("docs/spec.md", "- provider registry lists every provider\n")

        val plan = planWith(listOf(atom("a1", "Provider registry lists every provider.")))
            .atomize("shg-test", "implement @docs/spec.md")

        assertNotNull(plan)
        assertEquals(1, plan.atoms.size)
        assertEquals("spec.md", plan.label)
    }

    @Test
    fun the_bare_path_works_as_well_as_the_at_mention() {
        // The operator types `@docs/spec.md`; the mention machinery may hand
        // the goal the stripped path. Both have to resolve to the same file.
        writeDocument("docs/spec.md", "- something\n")
        val subject = planWith(listOf(atom("a1", "Something.")))

        assertNotNull(subject.atomize("shg-test", "build docs/spec.md"))
        assertNotNull(subject.atomize("shg-test", "build @docs/spec.md"))
    }

    @Test
    fun a_goal_stating_an_instruction_plans_nothing() {
        assertNull(planWith(listOf(atom("a1", "x"))).atomize("shg-test", "make ATROPOS build itself"))
    }

    @Test
    fun a_goal_carrying_the_document_inline_is_atomized_from_it() {
        // The case an end-to-end run found and the unit tests could not: by
        // the time a self-host goal exists, the CLI has already replaced
        // `@spec.md` with the file's contents, so the task is tens of
        // thousands of characters of specification with no filename anywhere
        // in it. Requiring a path meant a four-hundred-atom document silently
        // got the three-node cradle graph.
        val expanded = "implement the following.\n" + "- an obligation stated at length.\n".repeat(200)

        val plan = planWith(listOf(atom("a1", "An obligation."))).atomize("shg-test", expanded)

        assertNotNull(plan)
        assertNull(plan.source)
        assertEquals("the goal prompt", plan.label)
        assertEquals(1, plan.atoms.size)
    }

    @Test
    fun the_dag_is_built_from_an_inline_document_too() {
        val expanded = "implement the following.\n" + "- an obligation stated at length.\n".repeat(200)

        val dag = SelfHostBootstrapDagFactory(
            repoRoot = repoRoot,
            dagService = DagExecutionService(repoRoot = repoRoot),
            clock = clock,
            documentPlan = planWith(listOf(atom("a1", "First."), atom("a2", "Second.")))
        ).create(record(expanded), "11")

        assertEquals(2, dag.nodes.size)
        assertTrue(dag.label.contains("the goal prompt"), dag.label)
    }

    @Test
    fun a_document_that_does_not_exist_plans_nothing() {
        assertNull(planWith(listOf(atom("a1", "x"))).atomize("shg-test", "implement @docs/absent.md"))
    }

    @Test
    fun a_path_outside_every_granted_root_is_refused() {
        // The file exists and is readable; what makes it inadmissible is where
        // it is. `..` in a goal prompt is not a typo worth being helpful about.
        val outside = Files.createTempDirectory("atropos-elsewhere").resolve("secret.md")
        Files.writeString(outside, "- something\n")
        val escape = repoRoot.relativize(outside).toString()

        assertNull(planWith(listOf(atom("a1", "x"))).atomize("shg-test", "implement @$escape"))
    }

    @Test
    fun a_document_in_a_granted_ingest_root_is_admitted() {
        // The case restricting this to the repository would have refused: an
        // operator attaching a specification out of their phone's Downloads
        // folder. IngestTerritory already owns which roots those are.
        val downloads = Files.createTempDirectory("atropos-downloads")
        Files.writeString(downloads.resolve("spec.md"), "- something\n")

        val plan = SelfHostDocumentPlan(
            repoRoot = repoRoot,
            atomize = atomizerReturning(listOf(atom("a1", "Something."))),
            territory = IngestTerritory(
                launchDirectory = repoRoot,
                env = { key -> downloads.toString().takeIf { key == "ATROPOS_INGEST_ROOTS" } }
            )
        ).atomize("shg-test", "implement @spec.md")

        assertNotNull(plan)
        assertEquals(downloads.resolve("spec.md"), plan.source)
        assertEquals("spec.md", plan.label)
    }

    @Test
    fun an_atomizer_that_found_nothing_falls_back_rather_than_planning_an_empty_graph() {
        writeDocument("docs/spec.md", "- something\n")

        assertNull(planWith(emptyList()).atomize("shg-test", "implement @docs/spec.md"))
    }

    @Test
    fun the_dag_has_one_node_per_atom() {
        writeDocument("docs/spec.md", "- a\n- b\n- c\n")
        val atoms = listOf(
            atom("a1", "First obligation."),
            atom("a2", "Second obligation.", dependsOn = listOf("a1")),
            atom("a3", "Third obligation.", dependsOn = listOf("a2"))
        )

        val dag = SelfHostBootstrapDagFactory(
            repoRoot = repoRoot,
            dagService = DagExecutionService(repoRoot = repoRoot),
            clock = clock,
            documentPlan = planWith(atoms)
        ).create(record("implement @docs/spec.md"), "11")

        assertEquals(3, dag.nodes.size)
        assertEquals(listOf("First obligation.", "Second obligation.", "Third obligation."), dag.nodes.map { it.label })
        // The statement reaches the node verbatim, so the provider is asked the
        // document's question and not a paraphrase of it.
        assertEquals("Second obligation.", dag.nodes[1].actionPayload)
        assertEquals(listOf(dag.nodes[0].id), dag.nodes[1].dependencies)
    }

    @Test
    fun a_forward_dependency_is_dropped_so_the_graph_cannot_cycle() {
        // This graph is executed on a phone, where a cycle is a run that never
        // ends. Only backward edges survive, which makes acyclicity a property
        // of construction rather than something to trust upstream for.
        writeDocument("docs/spec.md", "- a\n- b\n")
        val atoms = listOf(
            atom("a1", "First.", dependsOn = listOf("a2")),
            atom("a2", "Second.", dependsOn = listOf("a1"))
        )

        val dag = SelfHostBootstrapDagFactory(
            repoRoot = repoRoot,
            dagService = DagExecutionService(repoRoot = repoRoot),
            clock = clock,
            documentPlan = planWith(atoms)
        ).create(record("implement @docs/spec.md"), "11")

        assertEquals(emptyList(), dag.nodes[0].dependencies)
        assertEquals(listOf(dag.nodes[0].id), dag.nodes[1].dependencies)
    }

    @Test
    fun every_atom_gets_a_node_even_when_two_share_an_id() {
        // Node ids used to be keyed by atom id, so a repeated id collapsed two
        // atoms into one entry and the graph came back a node short of the
        // document -- silently, and visible only by counting. An end-to-end
        // run produced 389 nodes from 390 atoms for exactly this reason.
        writeDocument("docs/spec.md", "- a\n- b\n")
        val atoms = listOf(
            atom("same", "First."),
            atom("same", "Second."),
            atom("other", "Third.", dependsOn = listOf("same"))
        )

        val dag = SelfHostBootstrapDagFactory(
            repoRoot = repoRoot,
            dagService = DagExecutionService(repoRoot = repoRoot),
            clock = clock,
            documentPlan = planWith(atoms)
        ).create(record("implement @docs/spec.md"), "11")

        assertEquals(3, dag.nodes.size)
        assertEquals(3, dag.nodes.map { it.id }.distinct().size, "two nodes share an id")
        // The dependency resolves to the atom that earned the id first.
        assertEquals(listOf(dag.nodes[0].id), dag.nodes[2].dependencies)
    }

    @Test
    fun a_goal_with_no_document_still_gets_the_cradle_graph() {
        // The bootstrap path is not removed. It is the right answer for a goal
        // that names nothing, and the fallback when a named document cannot be
        // read.
        val dag = SelfHostBootstrapDagFactory(
            repoRoot = repoRoot,
            dagService = DagExecutionService(repoRoot = repoRoot),
            clock = clock,
            documentPlan = planWith(emptyList())
        ).create(record("make ATROPOS build itself"), "11")

        assertEquals(3, dag.nodes.size)
        assertTrue(dag.label.startsWith("self-host bootstrap"), dag.label)
    }
}
