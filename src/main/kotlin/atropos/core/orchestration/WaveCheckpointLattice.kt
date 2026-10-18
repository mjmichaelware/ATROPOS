/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.orchestration

import atropos.core.dag.DagDefinition
import atropos.core.dag.DagNode
import atropos.core.dag.DagNodeState
import org.json.JSONArray
import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Sub-DAG Checkpoint Lattice for isolating failing leaf atoms and protecting settled nodes.
 * Persists cryptographic acceptance SHA-256 freezes for completed DAG nodes into
 * .atropos/staging/dag_checkpoint.json to prevent wave halts when isolated verifiers fail.
 */
object WaveCheckpointLattice {

    fun checkpointFile(repoRoot: Path): Path {
        val staging = repoRoot.resolve(".atropos/staging")
        if (!Files.exists(staging)) Files.createDirectories(staging)
        return staging.resolve("dag_checkpoint.json")
    }

    fun persistCheckpoint(
        repoRoot: Path,
        dagId: String,
        completedNodes: List<DagNode>,
        freezeSha256: String
    ): Path {
        val file = checkpointFile(repoRoot)
        val rootJson = if (Files.isRegularFile(file)) {
            runCatching { JSONObject(Files.readString(file)) }.getOrDefault(JSONObject())
        } else {
            JSONObject()
        }

        val dagSection = rootJson.optJSONObject(dagId) ?: JSONObject()
        val nodeMap = dagSection.optJSONObject("completed_nodes") ?: JSONObject()

        for (node in completedNodes) {
            if (node.state == DagNodeState.COMPLETE) {
                val nodeHash = computeNodeAcceptanceHash(node, freezeSha256)
                nodeMap.put(node.id, nodeHash)
            }
        }

        dagSection.put("freeze_sha256", freezeSha256)
        dagSection.put("updated_epoch_ms", System.currentTimeMillis())
        dagSection.put("completed_nodes", nodeMap)
        rootJson.put(dagId, dagSection)

        Files.writeString(file, rootJson.toString(2))
        return file
    }

    fun loadCheckpoint(repoRoot: Path, dagId: String): Map<String, String> {
        val file = checkpointFile(repoRoot)
        if (!Files.isRegularFile(file)) return emptyMap()

        return runCatching {
            val root = JSONObject(Files.readString(file))
            val dagSection = root.optJSONObject(dagId) ?: return emptyMap()
            val nodes = dagSection.optJSONObject("completed_nodes") ?: return emptyMap()
            val result = mutableMapOf<String, String>()
            nodes.keys().forEach { k ->
                result[k] = nodes.optString(k)
            }
            result
        }.getOrDefault(emptyMap())
    }

    fun isNodeSettled(repoRoot: Path, dagId: String, nodeId: String): Boolean {
        val checkpoint = loadCheckpoint(repoRoot, dagId)
        return checkpoint.containsKey(nodeId)
    }

    fun isolateSubDag(dag: DagDefinition, failingNodeId: String): List<DagNode> {
        val targetNode = dag.findNode(failingNodeId) ?: return emptyList()
        val subDagNodeIds = mutableSetOf(failingNodeId)
        val queue = ArrayDeque<String>()
        queue.add(failingNodeId)

        while (queue.isNotEmpty()) {
            val currentId = queue.removeFirst()
            val current = dag.findNode(currentId) ?: continue
            for (depId in current.dependencies) {
                val depNode = dag.findNode(depId)
                if (depNode != null && depNode.state != DagNodeState.COMPLETE) {
                    if (subDagNodeIds.add(depId)) {
                        queue.add(depId)
                    }
                }
            }
        }

        return dag.nodes.filter { it.id in subDagNodeIds }
    }

    fun hasProgress(repoRoot: Path, dagId: String, currentNodes: List<DagNode>): Boolean {
        val checkpoint = loadCheckpoint(repoRoot, dagId)
        val settledCount = checkpoint.size
        val currentCompleteCount = currentNodes.count { it.state == DagNodeState.COMPLETE }
        return currentCompleteCount >= settledCount
    }

    private fun computeNodeAcceptanceHash(node: DagNode, freezeSha256: String): String {
        val payload = "${node.id}:${node.action}:${node.actionPayload.orEmpty()}:$freezeSha256"
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(payload.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
