/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.commands

import atropos.core.dag.DagService
import atropos.core.dag.DocumentIngestionService
import atropos.core.dag.ExtractedRequirement
import atropos.core.dag.ImplementationState

/**
 * `/dag` — Phase 16 requirement DAG status, ingestion, and HIG.
 *
 * This is a surface over the existing [DagService] and [DocumentIngestionService].
 * It builds no graph and holds no node state of its own — the non-duplication
 * law means there is exactly one DAG owner, and a CLI handler is not it.
 */
class DagCommandHandler(
    private val dagService: DagService = DagService(),
    private val ingestion: DocumentIngestionService = DocumentIngestionService()
) {
    fun handle(args: List<String>): String = when (args.firstOrNull()) {
        "status" -> status()
        "ingest" -> ingest(args)
        "runnable" -> runnable()
        "cycles" -> cycles()
        "hig" -> hig()
        "snapshot" -> snapshot()
        else -> "usage: /dag status|ingest|runnable|cycles|hig|snapshot"
    }

    private fun status(): String =
        "DAG: ${dagService.getAllNodes().size} nodes, ${dagService.runnableNodes().size} runnable"

    private fun ingest(args: List<String>): String {
        if (args.size < 2) return "usage: /dag ingest <file-path>"
        val result = ingestion.ingestFile(args[1])
        if (!result.success) return "ingestion failed: ${result.errors.joinToString("; ")}"
        return "ingested: ${result.document?.id} (${result.requirements.size} requirements extracted)"
    }

    private fun runnable(): String {
        val nodes = dagService.runnableNodes()
        if (nodes.isEmpty()) return "no runnable DAG nodes"
        return nodes.joinToString("\n") { "  ${it.id}: req=${it.requirementId} state=${it.state}" }
    }

    private fun cycles(): String {
        val cycles = dagService.detectCycles()
        if (cycles.isEmpty()) return "no cycles detected"
        return cycles.joinToString("\n") { "  cycle: ${it.joinToString(" -> ")}" }
    }

    /**
     * Honest Implementation Grade over the current nodes.
     *
     * Only a node in the completed state counts as implemented; everything else
     * — running, blocked, failed — reads as absent. Grading an in-flight node as
     * partially done would let the number drift upward on activity rather than
     * on completion, which is the one thing this metric exists to prevent.
     */
    private fun hig(): String {
        val nodes = dagService.getAllNodes()
        if (nodes.isEmpty()) return "no DAG nodes for HIG computation"

        val requirements = nodes.map { node ->
            ExtractedRequirement(
                canonicalWording = node.requirementId,
                implementationState = if (node.state.name == COMPLETED_STATE) {
                    ImplementationState.IMPLEMENTED
                } else {
                    ImplementationState.ABSENT
                }
            )
        }

        val hig = ingestion.computeHIG(requirements)
        return "${hig.higFormatted} (${hig.absent} absent, ${hig.partial} partial, " +
            "${hig.implemented} implemented, ${hig.verified} verified / ${hig.total} total)"
    }

    private fun snapshot(): String {
        val snapshot = dagService.dagSnapshot()
        return "DAG snapshot: ${snapshot.nodes.size} nodes, " +
            "${snapshot.sourceDocumentIds.size} documents, version ${snapshot.version}"
    }

    private companion object {
        const val COMPLETED_STATE = "COMPLETED"
    }
}
