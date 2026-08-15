/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.specgraph

import atropos.core.json.JsonStringField

/**
 * Reads `atropos_handoff.json` into a [HandoffDocument].
 *
 * Scoped parsing throughout: each nested object's body is extracted first and
 * fields are read from *that* scope rather than from the whole document. The
 * handoff repeats field names at several depths — `id` appears on the project,
 * the plan, the graph and every node — so a document-wide search for `"id"`
 * returns whichever happens to come first in the file, which is a bug that
 * produces a plausible wrong answer rather than an error.
 *
 * Returns null rather than throwing on a document it cannot read. The caller's
 * fallback is the internal planner, which is a normal path; an exception would
 * make an absent or older-schema handoff look like a crash.
 */
object HandoffParser {

    /** Parses handoff text, or null when it is not a v1 handoff. */
    fun parse(json: String): HandoffDocument? {
        val schema = JsonStringField.text(json, "schema") ?: return null
        if (schema != HandoffDocument.SCHEMA) return null

        val projectBody = JsonStringField.objectBody(json, "project") ?: return null
        val planBody = JsonStringField.objectBody(json, "plan") ?: return null
        val executionBody = JsonStringField.objectBody(json, "execution") ?: return null
        val contractBody = JsonStringField.objectBody(json, "execution_contract") ?: return null

        return HandoffDocument(
            schema = schema,
            producer = JsonStringField.text(json, "producer").orEmpty(),
            project = HandoffProject(
                id = JsonStringField.text(projectBody, "id").orEmpty(),
                slug = JsonStringField.text(projectBody, "slug").orEmpty(),
                name = JsonStringField.text(projectBody, "name").orEmpty()
            ),
            plan = HandoffPlan(
                id = JsonStringField.text(planBody, "id").orEmpty(),
                status = JsonStringField.text(planBody, "status").orEmpty(),
                inputFingerprint = JsonStringField.text(planBody, "input_fingerprint").orEmpty(),
                authorityGraphId = JsonStringField.text(planBody, "authority_graph_id").orEmpty(),
                executionGraphId = JsonStringField.text(planBody, "execution_graph_id").orEmpty()
            ),
            execution = parseExecution(executionBody),
            requirements = parseRequirements(json),
            routingLaw = JsonStringField.arrayBody(json, "routing_law")
                ?.let { JsonStringField.values(it) }
                ?.map(JsonStringField::unescape)
                .orEmpty(),
            contract = HandoffExecutionContract(
                authorityOwner = JsonStringField.text(contractBody, "authority_owner").orEmpty(),
                runtimeOwner = JsonStringField.text(contractBody, "runtime_owner").orEmpty(),
                sourceAuthorityIsImmutable =
                    JsonStringField.booleanValue(contractBody, "source_authority_is_immutable") ?: false,
                executionGraphMustBeAcyclic =
                    JsonStringField.booleanValue(contractBody, "execution_graph_must_be_acyclic") ?: false,
                implementationRequiresVerification =
                    JsonStringField.booleanValue(contractBody, "implementation_requires_verification") ?: false
            )
        )
    }

    private fun parseExecution(body: String): HandoffExecutionGraph {
        val nodes = JsonStringField.arrayBody(body, "nodes")
            ?.let { JsonStringField.objectsIn(it) }
            ?.mapNotNull(::parseNode)
            .orEmpty()

        val edges = JsonStringField.arrayBody(body, "edges")
            ?.let { JsonStringField.objectsIn(it) }
            ?.mapNotNull(::parseEdge)
            .orEmpty()

        // Edges are filtered against the node set. SpecGraph forbids an edge to
        // a node that does not exist, but a truncated bundle can still present
        // one, and a dependency on a node that will never complete blocks its
        // dependant forever with nothing in the log to say why.
        val nodeIds = nodes.map { it.id }.toSet()

        return HandoffExecutionGraph(
            graphId = JsonStringField.text(body, "graph_id").orEmpty(),
            nodes = nodes,
            edges = edges.filter { it.fromNodeId in nodeIds && it.toNodeId in nodeIds },
            readyNodeIds = JsonStringField.arrayBody(body, "ready_node_ids")
                ?.let { JsonStringField.values(it) }
                ?.map(JsonStringField::unescape)
                ?.filter { it in nodeIds }
                .orEmpty()
        )
    }

    private fun parseNode(body: String): HandoffNode? {
        val id = JsonStringField.text(body, "id")?.trim().orEmpty()
        if (id.isEmpty()) return null

        // payload_json arrives as an *escaped JSON string*, not as an object,
        // because SpecGraph stores it with json.dumps into a TEXT column. It has
        // to be unescaped before its own fields can be read, which is why this
        // cannot simply scope into it the way the other nested objects do.
        val payload = JsonStringField.text(body, "payload_json").orEmpty()

        return HandoffNode(
            id = id,
            nodeKey = JsonStringField.text(body, "node_key").orEmpty(),
            nodeType = JsonStringField.text(body, "node_type").orEmpty(),
            title = JsonStringField.text(body, "title").orEmpty(),
            status = JsonStringField.text(body, "status").orEmpty(),
            atomId = payload.takeIf { it.isNotEmpty() }
                ?.let { JsonStringField.text(it, "atom_id") }
                ?.takeIf { it.isNotBlank() },
            openDimensions = payload.takeIf { it.isNotEmpty() }
                ?.let { JsonStringField.longValue(it, "open_dimensions") }
                ?.toInt() ?: 0
        )
    }

    private fun parseEdge(body: String): HandoffEdge? {
        val from = JsonStringField.text(body, "from_node_id")?.trim().orEmpty()
        val to = JsonStringField.text(body, "to_node_id")?.trim().orEmpty()
        if (from.isEmpty() || to.isEmpty()) return null
        return HandoffEdge(
            fromNodeId = from,
            toNodeId = to,
            edgeType = JsonStringField.text(body, "edge_type").orEmpty()
        )
    }

    private fun parseRequirements(json: String): List<HandoffRequirement> =
        JsonStringField.arrayBody(json, "requirements")
            ?.let { JsonStringField.objectsIn(it) }
            ?.mapNotNull { body ->
                val atomId = JsonStringField.text(body, "atom_id")?.trim().orEmpty()
                if (atomId.isEmpty()) return@mapNotNull null
                HandoffRequirement(
                    atomId = atomId,
                    statement = JsonStringField.text(body, "statement").orEmpty(),
                    kind = JsonStringField.text(body, "kind").orEmpty(),
                    modality = JsonStringField.text(body, "modality").orEmpty(),
                    source = JsonStringField.text(body, "source").orEmpty(),
                    planNodes = JsonStringField.arrayBody(body, "plan_nodes")
                        ?.let { nodes -> JsonStringField.objectsIn(nodes) }
                        ?.mapNotNull(::parseBinding)
                        .orEmpty()
                )
            }
            .orEmpty()

    /**
     * One `plan_node_bindings` row.
     *
     * A binding with no `graph_node_id` names no node, so it cannot make a
     * requirement covered. Dropping it is what keeps [HandoffRequirement.orphaned]
     * truthful — counting an empty binding would report a requirement as
     * implemented by a node that does not exist.
     */
    private fun parseBinding(body: String): HandoffPlanBinding? {
        val nodeId = JsonStringField.text(body, "graph_node_id")?.trim().orEmpty()
        if (nodeId.isEmpty()) return null
        return HandoffPlanBinding(
            graphNodeId = nodeId,
            atomId = JsonStringField.text(body, "atom_id").orEmpty(),
            stage = JsonStringField.text(body, "stage").orEmpty(),
            sequenceNumber = JsonStringField.longValue(body, "sequence_number")?.toInt() ?: 0
        )
    }
}
