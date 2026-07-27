package atropos.core.dag

import java.security.MessageDigest
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class DagService(
    private val store: DagStore = DagStore(),
    private val repoRoot: Path = Path.of(System.getProperty("user.dir"))
) {
    private val nodeMap = mutableMapOf<String, DAGNode>()

    fun addNode(node: DAGNode): DAGNode {
        val hashed = node.copy(hash = hashNode(node))
        nodeMap[hashed.id] = hashed
        store.saveNode(hashed)
        return hashed
    }

    fun getNode(id: String): DAGNode? = nodeMap[id] ?: store.loadNodes().firstOrNull { it.id == id }

    fun getAllNodes(): List<DAGNode> {
        if (nodeMap.isEmpty()) {
            store.loadNodes().forEach { nodeMap[it.id] = it }
        }
        return nodeMap.values.toList()
    }

    fun updateState(id: String, state: DAGNodeState) {
        val node = getNode(id) ?: return
        val updated = node.copy(state = state)
        nodeMap[id] = updated
        store.saveNode(updated)
    }

    fun runnableNodes(): List<DAGNode> {
        val all = getAllNodes()
        return all.filter { node ->
            node.isRunnable() && node.dependencies.all { depId ->
                all.firstOrNull { it.id == depId }?.state == DAGNodeState.COMPLETED
            }
        }
    }

    fun detectCycles(): List<List<String>> {
        val all = getAllNodes()
        val cycles = mutableListOf<List<String>>()
        val visited = mutableSetOf<String>()
        val inStack = mutableSetOf<String>()

        fun dfs(nodeId: String, path: MutableList<String>) {
            if (nodeId in inStack) {
                val cycleStart = path.indexOf(nodeId)
                if (cycleStart >= 0) {
                    cycles += path.subList(cycleStart, path.size) + nodeId
                }
                return
            }
            if (nodeId in visited) return
            visited += nodeId
            inStack += nodeId
            path += nodeId
            val node = all.firstOrNull { it.id == nodeId }
            if (node != null) {
                for (dep in node.dependencies) {
                    dfs(dep, path)
                }
            }
            path.removeAt(path.lastIndex)
            inStack.remove(nodeId)
        }

        for (node in all) {
            if (node.id !in visited) dfs(node.id, mutableListOf())
        }
        return cycles.distinct()
    }

    fun dagSnapshot(): DAG {
        val all = getAllNodes()
        return DAG(
            nodes = all.associateBy { it.id },
            sourceDocumentIds = store.loadDocuments().map { it.id },
            version = (store.loadDocuments().maxOfOrNull { it.version } ?: 0) + 1,
            sourceFingerprint = DAG.fingerprint(*all.map { it.hash }.toTypedArray())
        )
    }

    fun addRequirementToDAG(req: ExtractedRequirement): DAGNode {
        val node = DAGNode(
            requirementId = req.id,
            parentIds = req.parentIds,
            dependencies = req.dependencies,
            implementationFiles = req.permittedFiles,
            hash = ""
        )
        return addNode(node)
    }

    private fun hashNode(node: DAGNode): String {
        val input = node.requirementId + node.dependencies.joinToString(",") + node.state.name + node.implementationFiles.joinToString(",")
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }.take(16)
    }
}
