/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.planning

import atropos.core.dag.DagNodeState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A measured factory run produced 12 nodes, 0 edges, and all 12 simultaneously
 * `READY` — including a `tests_acceptance` node that could run before the
 * `functional_contract` node whose output it verifies. Nine of the twelve
 * carried byte-identical payloads.
 *
 * These pin the shape of an internally-planned DAG so none of that can return
 * silently: a plan with no edges is indistinguishable from a correct one in
 * every count the run reports.
 */
class InternalPlanShapeTest {

    private val ingestion = InternalIngestionService()
    private val extractor = InternalAtomExtractor()

    private val requirements = """
        # Application requirements
        project_id=factory-1

        ## Requirements

        The cli named todo MUST be generated from the user request.
        The cli MUST support tasks stored in a file.
        The generated source MUST compile and its tests must verify behaviour.
        The run MUST record evidence for an audit.
    """.trimIndent()

    private fun atoms(source: String = "requirements", text: String = requirements) =
        extractor.extract(ingestion.ingestText("proj-1", source, text))

    @Test
    fun `an extracted plan has edges rather than being a flat set`() {
        val extracted = atoms()

        assertTrue(extracted.isNotEmpty(), "the document must produce atoms at all")
        assertTrue(
            extracted.any { it.dependencies.isNotEmpty() },
            "every atom had an empty dependency list, so the plan had no edges: " +
                InternalAtomDependencyModel.render(extracted)
        )
    }

    /**
     * The specific ordering failure observed: verification scheduled alongside
     * the work it verifies.
     */
    @Test
    fun `verification depends on implementation, which depends on the contract`() {
        val extracted = atoms()
        val byId = extracted.associateBy { it.id }

        val verification = extracted.filter { AtomStage.of(it.dimension) == AtomStage.VERIFICATION }
        assertTrue(verification.isNotEmpty(), "this document states tests, so it must have a checking atom")

        verification.forEach { atom ->
            assertTrue(atom.dependencies.isNotEmpty(), "${atom.dimension} must not be free to run first")
            assertTrue(
                atom.dependencies.all { byId.getValue(it).let { dep -> AtomStage.of(dep.dimension) != AtomStage.VERIFICATION } },
                "${atom.dimension} must wait on earlier stages, not on its own"
            )
        }
    }

    @Test
    fun `contract atoms are the roots and nothing precedes them`() {
        val extracted = atoms()

        extracted.filter { AtomStage.of(it.dimension) == AtomStage.CONTRACT }
            .forEach { assertEquals(emptyList(), it.dependencies, "${it.dimension} is a contract and must be a root") }
    }

    /**
     * Sections describe different requirements and have no inherent order.
     * Joining them would serialise work that can genuinely run in parallel.
     */
    @Test
    fun `stage edges never cross a section boundary`() {
        val extracted = atoms()
        val byId = extracted.associateBy { it.id }

        extracted.forEach { atom ->
            atom.dependencies.forEach { dependency ->
                assertEquals(
                    atom.sectionId,
                    byId.getValue(dependency).sectionId,
                    "an edge joined two sections that state no order between them"
                )
            }
        }
    }

    @Test
    fun `no atom depends on itself and every dependency exists`() {
        val extracted = atoms()
        val ids = extracted.map { it.id }.toSet()

        extracted.forEach { atom ->
            assertTrue(atom.id !in atom.dependencies, "${atom.id} depends on itself")
            atom.dependencies.forEach {
                assertTrue(it in ids, "${atom.id} depends on $it, which no atom produces")
            }
        }
    }

    /**
     * A section with no contract atom must not block: its implementation should
     * start rather than wait on a node that was never created.
     */
    @Test
    fun `a section with no contract atom still has a runnable root`() {
        val extracted = atoms(text = "# Notes\n\nThe screen renders at narrow widths without colour.\n")

        assertTrue(extracted.isNotEmpty())
        assertTrue(
            extracted.any { it.dependencies.isEmpty() },
            "no atom could start: " + InternalAtomDependencyModel.render(extracted)
        )
    }

    /**
     * Nine nodes shared one payload because `statement` is the whole section and
     * the dimension reached the executor in no form at all.
     */
    @Test
    fun `each dimension of a section is given a distinct brief`() {
        val extracted = atoms()
        val graph = InternalAuthorityGraphBuilder().build("proj-1", extracted)
        val dag = InternalExecutionDagSynthesizer()
            .synthesize("proj-1", "todo", graph, java.nio.file.Path.of("/tmp/atropos-plan-shape"))

        val payloads = dag.nodes.mapNotNull { it.actionPayload }
        assertEquals(
            payloads.size,
            payloads.toSet().size,
            "two nodes were handed byte-identical input and differ only by a label the executor never sees"
        )
        assertTrue(payloads.all { it.contains("dimension=") && it.contains("focus=") })
    }

    /**
     * Two documents both have a `sec-1`, so `dimension: sectionId` named two
     * different nodes identically in every listing and error message.
     */
    @Test
    fun `node labels distinguish atoms from different documents`() {
        val fromRequirements = atoms(source = "requirements")
        val fromPrompt = atoms(source = "nl-prompt", text = "# Prompt\n\nStore tasks in a file.\n")
        val graph = InternalAuthorityGraphBuilder().build("proj-1", fromRequirements + fromPrompt)

        val dag = InternalExecutionDagSynthesizer()
            .synthesize("proj-1", "todo", graph, java.nio.file.Path.of("/tmp/atropos-plan-shape"))

        assertEquals(dag.nodes.size, dag.nodes.map { it.label }.toSet().size, "two nodes share a label")
    }

    /**
     * The readiness consequence of having edges: only the roots may start.
     * Previously every node was ready at once.
     */
    @Test
    fun `only root nodes are ready at plan time`() {
        val extracted = atoms()
        val graph = InternalAuthorityGraphBuilder().build("proj-1", extracted)
        val dag = InternalExecutionDagSynthesizer()
            .synthesize("proj-1", "todo", graph, java.nio.file.Path.of("/tmp/atropos-plan-shape"))

        val ready = dag.nodes.filter { it.isReady(dag.nodes.associate { node -> node.id to node.state }) }

        assertTrue(ready.isNotEmpty(), "something must be startable")
        assertTrue(ready.size < dag.nodes.size, "every node was ready at once, which means there were no edges")
        assertTrue(ready.all { it.state == DagNodeState.PENDING || it.state == DagNodeState.READY })
    }

    @Test
    fun `the plan shape is reportable so an edgeless plan is visible`() {
        val rendered = InternalAtomDependencyModel.render(atoms())

        assertTrue(rendered.contains("nodes="))
        assertTrue(rendered.contains("edges="))
        assertTrue(rendered.contains("roots="))
    }
}
