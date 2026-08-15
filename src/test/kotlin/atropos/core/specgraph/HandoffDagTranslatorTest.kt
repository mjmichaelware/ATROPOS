/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.specgraph

import atropos.core.dag.DagNodeAction
import atropos.core.dag.DagNodeState
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * SpecGraph synthesizes and verifies an execution graph; ATROPOS used to throw
 * it away and re-derive one from atoms alone. These check that the translation
 * carries the plan across unchanged rather than re-planning it — and that it
 * refuses rather than guesses where SpecGraph says something this side cannot
 * represent.
 */
class HandoffDagTranslatorTest {

    private val repoRoot: Path = Path.of("/tmp/atropos-translate-test")
    private val translator = HandoffDagTranslator()

    private fun node(
        id: String,
        type: String,
        status: String = "PENDING",
        atom: String? = "atom-1",
        open: Int = 0
    ) = HandoffNode(
        id = id,
        nodeKey = "000001-${type.lowercase()}-aa",
        nodeType = type,
        title = "$type: something",
        status = status,
        atomId = atom,
        openDimensions = open
    )

    private fun handoff(
        nodes: List<HandoffNode> = listOf(
            node("n1", "CONTRACT"),
            node("n2", "IMPLEMENTATION"),
            node("n3", "VERIFICATION")
        ),
        edges: List<HandoffEdge> = listOf(
            HandoffEdge("n1", "n2", "MUST_PRECEDE"),
            HandoffEdge("n2", "n3", "MUST_PRECEDE")
        ),
        ready: List<String> = listOf("n1"),
        status: String = "VERIFIED",
        runtimeOwner: String = "atropos",
        requirements: List<HandoffRequirement> = listOf(
            HandoffRequirement(
                atomId = "atom-1",
                statement = "The CLI must accept --json.",
                kind = "REQUIREMENT",
                modality = "MUST",
                source = "doc.md#L4-9",
                planNodes = listOf(
                    HandoffPlanBinding("n1", "atom-1", "CONTRACT", 1),
                    HandoffPlanBinding("n2", "atom-1", "IMPLEMENTATION", 2),
                    HandoffPlanBinding("n3", "atom-1", "VERIFICATION", 3)
                )
            )
        )
    ) = HandoffDocument(
        schema = HandoffDocument.SCHEMA,
        producer = "specgraph-foundry",
        project = HandoffProject("proj-1", "demo", "Demo"),
        plan = HandoffPlan("plan-9", status, "fp-abc", "graph-a", "graph-e"),
        execution = HandoffExecutionGraph("graph-e", nodes, edges, ready),
        requirements = requirements,
        routingLaw = listOf("LOCAL_TOOLCHAIN"),
        contract = HandoffExecutionContract("specgraph-foundry", runtimeOwner, true, true, true)
    )

    @Test
    fun `a verified handoff becomes a definition with the plan's own node ids`() {
        val translation = translator.translate(handoff(), repoRoot)

        val definition = assertNotNull(translation.definition)
        assertTrue(translation.usable)
        assertEquals(listOf("n1", "n2", "n3"), definition.nodes.map { it.id })
        assertEquals("proj-1", definition.projectId)
        assertTrue(definition.id.startsWith("specgraph-"))
    }

    /**
     * The point of the ingest. The order is SpecGraph's, carried across rather
     * than recomputed from atom dependencies on this side.
     */
    @Test
    fun `dependencies come from the handoff's must-precede edges`() {
        val definition = assertNotNull(translator.translate(handoff(), repoRoot).definition)

        assertEquals(emptyList(), definition.findNode("n1")?.dependencies)
        assertEquals(listOf("n1"), definition.findNode("n2")?.dependencies)
        assertEquals(listOf("n2"), definition.findNode("n3")?.dependencies)
    }

    @Test
    fun `the three stages map to generation, generation and verification`() {
        val definition = assertNotNull(translator.translate(handoff(), repoRoot).definition)

        assertEquals(DagNodeAction.PROVIDER_CALL, definition.findNode("n1")?.action)
        assertEquals(DagNodeAction.PROVIDER_CALL, definition.findNode("n2")?.action)
        assertEquals(DagNodeAction.VERIFY, definition.findNode("n3")?.action)
    }

    /**
     * SpecGraph could add a fourth stage. Mapping it to whatever the current
     * `else` branch happens to be would run it as the wrong kind of step while
     * reporting success.
     */
    @Test
    fun `an unrecognised stage is refused rather than defaulted`() {
        val translation = translator.translate(
            handoff(
                nodes = listOf(node("n1", "CONTRACT"), node("n9", "DEPLOYMENT")),
                edges = emptyList()
            ),
            repoRoot
        )

        val definition = assertNotNull(translation.definition)
        assertEquals(listOf("n1"), definition.nodes.map { it.id })
        assertTrue(translation.refusals.any { it.startsWith("unmapped_node_type:DEPLOYMENT") })
    }

    @Test
    fun `a dependency on a refused node is dropped and reported`() {
        val translation = translator.translate(
            handoff(
                nodes = listOf(node("n9", "DEPLOYMENT"), node("n2", "IMPLEMENTATION")),
                edges = listOf(HandoffEdge("n9", "n2", "MUST_PRECEDE"))
            ),
            repoRoot
        )

        val definition = assertNotNull(translation.definition)
        assertEquals(emptyList(), definition.findNode("n2")?.dependencies)
        assertTrue(translation.refusals.any { it.startsWith("dropped_dependency node=n2") })
    }

    /**
     * SpecGraph blocks a node when its atom still has open research dimensions.
     * That judgement was made with the research results, which ATROPOS does not
     * have — recomputing it here would start work SpecGraph said was unsettled.
     */
    @Test
    fun `a node SpecGraph blocked stays blocked`() {
        val translation = translator.translate(
            handoff(
                nodes = listOf(node("n1", "CONTRACT", status = "BLOCKED", open = 3), node("n2", "IMPLEMENTATION")),
                edges = emptyList()
            ),
            repoRoot
        )

        val definition = assertNotNull(translation.definition)
        assertEquals(DagNodeState.BLOCKED, definition.findNode("n1")?.state)
        assertEquals(DagNodeState.PENDING, definition.findNode("n2")?.state)
    }

    @Test
    fun `an unverified plan yields no definition at all`() {
        val translation = translator.translate(handoff(status = "DRAFT"), repoRoot)

        assertNull(translation.definition)
        assertFalse(translation.usable)
        assertEquals(listOf("plan_not_verified"), translation.blockers)
        assertTrue(translation.evidenceLine().startsWith("SKIPPED_SOFT_FAIL"))
    }

    @Test
    fun `a cyclic graph is refused before any node is built`() {
        val translation = translator.translate(
            handoff(
                edges = listOf(
                    HandoffEdge("n1", "n2", "MUST_PRECEDE"),
                    HandoffEdge("n2", "n3", "MUST_PRECEDE"),
                    HandoffEdge("n3", "n1", "MUST_PRECEDE")
                )
            ),
            repoRoot
        )

        assertNull(translation.definition)
        assertTrue(translation.blockers.contains("execution_graph_cyclic"))
    }

    @Test
    fun `a handoff with nothing ready is refused rather than started`() {
        assertTrue(
            translator.translate(handoff(ready = emptyList()), repoRoot)
                .blockers.contains("no_ready_nodes")
        )
    }

    /**
     * Without this the executor sees only the title, which
     * `_sanitize_export_title` has already truncated. The full statement lives
     * in the traceability data and nowhere else on the node.
     */
    @Test
    fun `the payload carries the requirement statement and its source`() {
        val definition = assertNotNull(translator.translate(handoff(), repoRoot).definition)
        val payload = assertNotNull(definition.findNode("n2")?.actionPayload)

        assertTrue(payload.contains("requirement=The CLI must accept --json."))
        assertTrue(payload.contains("source=doc.md#L4-9"))
        assertTrue(payload.contains("atom_id=atom-1"))
        assertTrue(payload.contains("stage=IMPLEMENTATION"))
        assertTrue(payload.contains("plan_input_fingerprint=fp-abc"))
    }

    @Test
    fun `open research dimensions are stated on the node that carries them`() {
        val translation = translator.translate(
            handoff(nodes = listOf(node("n1", "CONTRACT", open = 4)), edges = emptyList()),
            repoRoot
        )

        assertTrue(
            assertNotNull(translation.definition?.findNode("n1")?.actionPayload)
                .contains("open_research_dimensions=4")
        )
    }

    @Test
    fun `a requirement with no plan node is surfaced, not silently dropped`() {
        val translation = translator.translate(
            handoff(
                requirements = listOf(
                    HandoffRequirement("atom-2", "Secrets must never be logged.", "REQUIREMENT",
                        "MUST", "doc.md#L20-22", planNodes = emptyList())
                )
            ),
            repoRoot
        )

        assertEquals(listOf("atom-2"), translation.orphanedRequirements().map { it.atomId })
    }

    /**
     * `implementation_requires_verification` in the execution contract forbids
     * exactly this: a requirement built and never checked.
     */
    @Test
    fun `a requirement with no verification stage is surfaced`() {
        val translation = translator.translate(
            handoff(
                requirements = listOf(
                    HandoffRequirement("atom-1", "The CLI must accept --json.", "REQUIREMENT",
                        "MUST", "doc.md#L4-9",
                        planNodes = listOf(
                            HandoffPlanBinding("n1", "atom-1", "CONTRACT", 1),
                            HandoffPlanBinding("n2", "atom-1", "IMPLEMENTATION", 2)
                        ))
                )
            ),
            repoRoot
        )

        assertEquals(listOf("atom-1"), translation.unverifiedRequirements().map { it.atomId })
    }

    @Test
    fun `an optional requirement is not reported as an orphan`() {
        val translation = translator.translate(
            handoff(
                requirements = listOf(
                    HandoffRequirement("atom-3", "The CLI may colourise output.", "RECOMMENDATION",
                        "MAY", "doc.md#L30", planNodes = emptyList())
                )
            ),
            repoRoot
        )

        assertTrue(translation.orphanedRequirements().isEmpty())
    }

    /**
     * Under-building against a spec is the more expensive error, so an unknown
     * modality counts as mandatory rather than being quietly skipped.
     */
    @Test
    fun `an unrecognised modality counts as mandatory`() {
        assertTrue(
            HandoffRequirement("a", "s", "k", "REQUIRED_BY_LAW", "src", emptyList()).mandatory
        )
        assertFalse(HandoffRequirement("a", "s", "k", "may", "src", emptyList()).mandatory)
    }

    @Test
    fun `translation is deterministic for the same handoff`() {
        val fixed = java.time.Instant.parse("2026-01-01T00:00:00Z")
        val first = translator.translate(handoff(), repoRoot, fixed).definition
        val second = translator.translate(handoff(), repoRoot, fixed).definition

        assertEquals(first, second)
    }
}
