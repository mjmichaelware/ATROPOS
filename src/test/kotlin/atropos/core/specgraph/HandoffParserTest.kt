/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.specgraph

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `atropos_handoff.json` was produced on every SpecGraph export and read by
 * nothing on this side. These check the reader against the shape
 * `ExportService._build_handoff` actually writes, including the two places it
 * differs from what a reader would assume: `plan_nodes` holds binding objects
 * rather than node ids, and `payload_json` arrives as an escaped JSON string
 * rather than as a nested object.
 */
class HandoffParserTest {

    /** Mirrors the structure of `_build_handoff`, trimmed to two atoms. */
    private val handoffJson = """
        {
          "schema": "specgraph.atropos.handoff.v1",
          "producer": "specgraph-foundry",
          "project": {"id": "proj-1", "slug": "demo", "name": "Demo Project"},
          "plan": {
            "id": "plan-9",
            "status": "VERIFIED",
            "input_fingerprint": "fp-abc",
            "authority_graph_id": "graph-a",
            "execution_graph_id": "graph-e"
          },
          "execution": {
            "graph_id": "graph-e",
            "nodes": [
              {"id": "n1", "node_key": "000001-contract-aa", "node_type": "CONTRACT",
               "title": "Specify: the CLI must accept --json", "status": "PENDING",
               "payload_json": "{\"atom_id\": \"atom-1\", \"open_dimensions\": 0, \"stage\": \"CONTRACT\"}"},
              {"id": "n2", "node_key": "000001-implementation-aa", "node_type": "IMPLEMENTATION",
               "title": "Implement: the CLI must accept --json", "status": "PENDING",
               "payload_json": "{\"atom_id\": \"atom-1\", \"open_dimensions\": 0, \"stage\": \"IMPLEMENTATION\"}"},
              {"id": "n3", "node_key": "000001-verification-aa", "node_type": "VERIFICATION",
               "title": "Verify: the CLI must accept --json", "status": "PENDING",
               "payload_json": "{\"atom_id\": \"atom-1\", \"open_dimensions\": 0, \"stage\": \"VERIFICATION\"}"},
              {"id": "n4", "node_key": "000002-contract-bb", "node_type": "CONTRACT",
               "title": "Specify: secrets must never be logged", "status": "BLOCKED",
               "payload_json": "{\"atom_id\": \"atom-2\", \"open_dimensions\": 3, \"stage\": \"CONTRACT\"}"}
            ],
            "edges": [
              {"from_node_id": "n1", "to_node_id": "n2", "edge_type": "MUST_PRECEDE"},
              {"from_node_id": "n2", "to_node_id": "n3", "edge_type": "MUST_PRECEDE"},
              {"from_node_id": "n1", "to_node_id": "gone", "edge_type": "MUST_PRECEDE"}
            ],
            "ready_node_ids": ["n1", "n4"]
          },
          "requirements": [
            {"atom_id": "atom-1", "statement": "The CLI must accept --json, per \"the spec\".",
             "kind": "REQUIREMENT", "modality": "MUST", "source": "doc.md#L4-9",
             "plan_nodes": [
               {"graph_node_id": "n2", "atom_id": "atom-1", "stage": "IMPLEMENTATION", "sequence_number": 2},
               {"graph_node_id": "n1", "atom_id": "atom-1", "stage": "CONTRACT", "sequence_number": 1},
               {"graph_node_id": "n3", "atom_id": "atom-1", "stage": "VERIFICATION", "sequence_number": 3}
             ]},
            {"atom_id": "atom-2", "statement": "Secrets must never be logged.",
             "kind": "REQUIREMENT", "modality": "MUST", "source": "doc.md#L20-22",
             "plan_nodes": []}
          ],
          "routing_law": ["LOCAL_TOOLCHAIN", "FREE_READY_PROVIDER"],
          "execution_contract": {
            "authority_owner": "specgraph-foundry",
            "runtime_owner": "atropos",
            "source_authority_is_immutable": true,
            "execution_graph_must_be_acyclic": true,
            "implementation_requires_verification": true
          }
        }
    """.trimIndent()

    @Test
    fun `a v1 handoff parses into project, plan and graph`() {
        val handoff = assertNotNull(HandoffParser.parse(handoffJson))

        assertEquals("demo", handoff.project.slug)
        assertEquals("plan-9", handoff.plan.id)
        assertEquals("fp-abc", handoff.plan.inputFingerprint)
        assertEquals(4, handoff.execution.nodes.size)
        assertEquals("atropos", handoff.contract.runtimeOwner)
        assertTrue(handoff.contract.sourceAuthorityIsImmutable)
    }

    @Test
    fun `a document of another schema is refused rather than half-read`() {
        assertNull(HandoffParser.parse(handoffJson.replace("handoff.v1", "handoff.v2")))
        assertNull(HandoffParser.parse("""{"nodes": []}"""))
    }

    /**
     * The trap this reader was written around. `id` appears on the project, the
     * plan, the graph and every node; a document-wide search returns whichever
     * comes first in the file and looks entirely plausible.
     */
    @Test
    fun `nested ids come from their own scope, not from the first match in the file`() {
        val handoff = assertNotNull(HandoffParser.parse(handoffJson))

        assertEquals("proj-1", handoff.project.id)
        assertEquals("plan-9", handoff.plan.id)
        assertEquals("graph-e", handoff.execution.graphId)
    }

    /**
     * `payload_json` is stored by SpecGraph with `json.dumps` into a TEXT
     * column, so it arrives escaped. Without unescaping first, the atom a node
     * implements is unreadable and every node loses its requirement.
     */
    @Test
    fun `the atom id is recovered from the escaped payload string`() {
        val handoff = assertNotNull(HandoffParser.parse(handoffJson))

        assertEquals("atom-1", handoff.execution.nodes.first { it.id == "n1" }.atomId)
        assertEquals(3, handoff.execution.nodes.first { it.id == "n4" }.openDimensions)
    }

    @Test
    fun `a quote inside a statement does not truncate it`() {
        val handoff = assertNotNull(HandoffParser.parse(handoffJson))

        assertEquals(
            "The CLI must accept --json, per \"the spec\".",
            handoff.requirements.first { it.atomId == "atom-1" }.statement
        )
    }

    /**
     * `plan_nodes` holds `plan_node_bindings` rows, not ids. Reading them as
     * strings would scrape every key and value in each object.
     */
    @Test
    fun `plan nodes parse as bindings and order by the plan's own sequence`() {
        val requirement = assertNotNull(HandoffParser.parse(handoffJson))
            .requirements.first { it.atomId == "atom-1" }

        assertEquals(3, requirement.planNodes.size)
        assertEquals(listOf("n1", "n2", "n3"), requirement.planNodeIds)
        assertTrue(requirement.fullyStaged)
    }

    @Test
    fun `a requirement with no plan node is reported orphaned`() {
        val requirement = assertNotNull(HandoffParser.parse(handoffJson))
            .requirements.first { it.atomId == "atom-2" }

        assertTrue(requirement.orphaned)
        assertTrue(requirement.mandatory)
        assertFalse(requirement.fullyStaged)
    }

    /**
     * A truncated bundle can present an edge to a node that is not there. A
     * dependency on a node that will never complete blocks its dependant
     * forever with nothing in the log to say why.
     */
    @Test
    fun `an edge to an absent node is dropped rather than carried`() {
        val graph = assertNotNull(HandoffParser.parse(handoffJson)).execution

        assertEquals(2, graph.edges.size)
        assertTrue(graph.edges.none { it.toNodeId == "gone" })
    }

    @Test
    fun `dependencies follow the document's must-precede direction`() {
        val graph = assertNotNull(HandoffParser.parse(handoffJson)).execution

        assertEquals(listOf("n1"), graph.dependenciesOf("n2"))
        assertEquals(listOf("n2"), graph.dependenciesOf("n3"))
        assertEquals(emptyList(), graph.dependenciesOf("n1"))
    }

    @Test
    fun `roots are the nodes nothing must precede`() {
        val graph = assertNotNull(HandoffParser.parse(handoffJson)).execution

        assertEquals(setOf("n1", "n4"), graph.roots().map { it.id }.toSet())
    }

    @Test
    fun `a plan that has not passed verify_plan is not verified`() {
        listOf("DRAFT", "FAILED", "", "SOMETHING_NEW").forEach { status ->
            val json = handoffJson.replace("\"status\": \"VERIFIED\"", "\"status\": \"$status\"")
            assertFalse(
                assertNotNull(HandoffParser.parse(json)).plan.verified,
                "$status must not count as verified"
            )
        }
    }

    @Test
    fun `a cycle in the edges is detected`() {
        val cyclic = handoffJson.replace(
            """{"from_node_id": "n1", "to_node_id": "gone", "edge_type": "MUST_PRECEDE"}""",
            """{"from_node_id": "n3", "to_node_id": "n1", "edge_type": "MUST_PRECEDE"}"""
        )

        assertFalse(assertNotNull(HandoffParser.parse(cyclic)).execution.acyclic())
        assertTrue(assertNotNull(HandoffParser.parse(handoffJson)).execution.acyclic())
    }

    @Test
    fun `executability names each failed condition separately`() {
        val draft = handoffJson.replace("\"status\": \"VERIFIED\"", "\"status\": \"DRAFT\"")
        val blockers = assertNotNull(HandoffParser.parse(draft)).executability().blockers()

        assertEquals(listOf("plan_not_verified"), blockers)
        assertTrue(assertNotNull(HandoffParser.parse(handoffJson)).executability().executable)
    }

    @Test
    fun `a handoff addressed to another runtime is not executable here`() {
        val foreign = handoffJson.replace("\"runtime_owner\": \"atropos\"", "\"runtime_owner\": \"other\"")

        assertTrue(
            assertNotNull(HandoffParser.parse(foreign)).executability()
                .blockers().contains("runtime_owner_not_atropos")
        )
    }
}
