package atropos.core.dag

import atropos.core.AtroposRepoRootLocator
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.util.UUID

class DagStore(private val root: Path = AtroposRepoRootLocator.resolve()) {
    private val authorityDagDir = root.resolve(".atropos/dag")
    private val nodesPath = authorityDagDir.resolve("nodes.jsonl")
    private val docPath = authorityDagDir.resolve("documents.jsonl")
    private val executionRoot = authorityDagDir.resolve("execution")
    private val executionDefinitionsDir = executionRoot.resolve("definitions")
    private val executionLockFile = executionRoot.resolve("dag.lock")
    private val executionCodec = DagExecutionCodec(executionDefinitionsDir)

    fun saveNode(node: DAGNode) {
        Files.createDirectories(authorityDagDir)
        val existing = loadNodes().toMutableList()
        val idx = existing.indexOfFirst { it.id == node.id }
        if (idx >= 0) existing[idx] = node else existing += node
        writeLines(nodesPath, existing.map { DagAuthorityLineCodec.nodeToLine(it) })
    }

    fun loadNodes(): List<DAGNode> {
        return readLines(nodesPath).mapNotNull { DagAuthorityLineCodec.lineToNode(it) }
    }

    fun saveDocument(doc: SourceDocument) {
        Files.createDirectories(authorityDagDir)
        val existing = loadDocuments().toMutableList()
        val idx = existing.indexOfFirst { it.id == doc.id }
        if (idx >= 0) existing[idx] = doc else existing += doc
        writeLines(docPath, existing.map { DagAuthorityLineCodec.docToLine(it) })
    }

    fun loadDocuments(): List<SourceDocument> {
        return readLines(docPath).mapNotNull { DagAuthorityLineCodec.lineToDoc(it) }
    }

    fun loadDocument(id: String): SourceDocument? = loadDocuments().firstOrNull { it.id == id }

    fun nodeCount(): Int {
        if (!Files.isRegularFile(nodesPath)) return 0
        return Files.readAllLines(nodesPath, StandardCharsets.UTF_8).count { it.isNotBlank() && !it.startsWith("#") }
    }

    fun dagDir(): Path = executionDefinitionsDir

    fun createDag(label: String, nodes: List<DagNode>, projectId: String? = null): DagDefinition {
        Files.createDirectories(executionDefinitionsDir)
        require(nodes.isNotEmpty()) { "execution DAG requires at least one node" }
        require(nodes.map { it.id }.distinct().size == nodes.size) { "execution DAG node ids must be unique" }
        nodes.forEach { node ->
            val missing = node.dependencies.filterNot { dep -> nodes.any { it.id == dep } }
            require(missing.isEmpty()) { "node ${node.id} depends on unknown nodes: ${missing.joinToString(", ")}" }
        }
        rejectCycles(nodes)

        val now = Instant.now()
        val dagId = "dag-" + UUID.randomUUID().toString().take(12)
        val dagDir = executionDefinitionsDir.resolve(dagId)
        Files.createDirectories(dagDir)
        val normalizedNodes = nodes.map { node ->
            val initialState = if (node.dependencies.isEmpty() && node.state == DagNodeState.PENDING) DagNodeState.READY else node.state
            node.copy(
                dagId = dagId,
                state = initialState,
                createdAt = now,
                updatedAt = now,
                metaFile = dagDir.resolve("${node.id}.meta")
            )
        }
        val definition = DagDefinition(
            id = dagId,
            label = label.trim(),
            projectId = projectId,
            nodes = normalizedNodes,
            createdAt = now,
            updatedAt = now,
            metaFile = dagDir.resolve("dag.meta")
        )
        normalizedNodes.forEach(::writeNode)
        executionCodec.writeDefinition(definition)
        return definition
    }

    fun listDags(): List<DagDefinition> {
        if (!Files.isDirectory(executionDefinitionsDir)) return emptyList()
        return Files.list(executionDefinitionsDir).use { stream ->
            stream.filter { Files.isDirectory(it) }
                .toList()
                .mapNotNull { executionCodec.readDefinition(it.resolve("dag.meta")) }
                .sortedByDescending { it.createdAt }
        }
    }

    fun readDag(dagId: String): DagDefinition? {
        val file = executionDefinitionsDir.resolve(dagId).resolve("dag.meta").normalize()
        if (!file.startsWith(executionDefinitionsDir) || !Files.isRegularFile(file)) return null
        return executionCodec.readDefinition(file)
    }

    fun deleteDag(dagId: String) {
        val dagDir = executionDefinitionsDir.resolve(dagId).normalize()
        if (!dagDir.startsWith(executionDefinitionsDir) || !Files.exists(dagDir)) return
        Files.walk(dagDir)
            .sorted(Comparator.reverseOrder())
            .forEach { path -> Files.deleteIfExists(path) }
    }

    fun readNode(nodeId: String): DagNode? {
        if (!Files.isDirectory(executionDefinitionsDir)) return null
        return Files.walk(executionDefinitionsDir, 2).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.fileName.toString() == "$nodeId.meta" }
                .findFirst()
                .map { executionCodec.readNodeFile(it) }
                .orElse(null)
        }
    }

    fun writeNode(node: DagNode): DagNode {
        Files.createDirectories(node.metaFile.parent)
        val updated = node.copy(updatedAt = Instant.now())
        val tmp = Files.createTempFile(node.metaFile.parent, node.id, ".tmp")
        val bytes = executionCodec.renderNode(updated).toByteArray(StandardCharsets.UTF_8)
        FileChannel.open(tmp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING).use { channel ->
            channel.write(ByteBuffer.wrap(bytes))
            channel.force(true)
        }
        try {
            Files.move(tmp, updated.metaFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(tmp, updated.metaFile, StandardCopyOption.REPLACE_EXISTING)
        }
        updated.dagId?.let { dagId -> touchDag(dagId) }
        return updated
    }

    fun claimNode(nodeId: String, owner: String = "dag-executor", leaseDurationSeconds: Long = 300L): DagNode? {
        val node = readNode(nodeId) ?: return null
        if (node.state != DagNodeState.READY && node.state != DagNodeState.PENDING) return null
        val now = Instant.now()
        return writeNode(
            node.copy(
                state = DagNodeState.CLAIMED,
                claimToken = UUID.randomUUID().toString(),
                claimOwner = owner,
                claimExpiresAt = now.plusSeconds(leaseDurationSeconds),
                attempts = node.attempts + 1,
                updatedAt = now
            )
        )
    }

    fun tryLock(): DagLock? {
        Files.createDirectories(executionRoot)
        val channel = FileChannel.open(executionLockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE)
        val lock = try {
            channel.tryLock()
        } catch (_: java.nio.channels.OverlappingFileLockException) {
            null
        }
        if (lock == null) {
            channel.close()
            return null
        }
        return DagLock(channel, lock)
    }

    private fun readLines(path: Path): List<String> {
        if (!Files.isRegularFile(path)) return emptyList()
        return Files.readAllLines(path, StandardCharsets.UTF_8).map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("#") }
    }

    private fun writeLines(path: Path, lines: List<String>) {
        Files.createDirectories(path.parent)
        val tmp = path.resolveSibling("${path.fileName}.${System.nanoTime()}.tmp")
        Files.writeString(tmp, lines.joinToString("\n") + "\n", StandardCharsets.UTF_8)
        try {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun rejectCycles(nodes: List<DagNode>) {
        val incomingCounts = nodes.associate { node -> node.id to node.dependencies.size }.toMutableMap()
        val reverseEdges = mutableMapOf<String, MutableList<String>>()
        nodes.forEach { node ->
            node.dependencies.forEach { dep ->
                reverseEdges.getOrPut(dep) { mutableListOf() }.add(node.id)
            }
        }
        val queue = ArrayDeque(nodes.filter { it.dependencies.isEmpty() }.map { it.id })
        var visited = 0
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            visited++
            reverseEdges[current].orEmpty().forEach { dependent ->
                val remaining = (incomingCounts[dependent] ?: 0) - 1
                incomingCounts[dependent] = remaining
                if (remaining == 0) queue.addLast(dependent)
            }
        }
        require(visited == nodes.size) {
            val cyclic = incomingCounts.filterValues { it > 0 }.keys.sorted()
            "execution DAG must be acyclic; cycle detected involving: ${cyclic.joinToString(", ")}"
        }
    }

    private fun touchDag(dagId: String) {
        val definition = readDag(dagId) ?: return
        executionCodec.writeDefinition(definition.copy(updatedAt = Instant.now()))
    }
}

class DagLock(
    private val channel: FileChannel,
    private val lock: java.nio.channels.FileLock
) : AutoCloseable {
    override fun close() {
        lock.release()
        channel.close()
    }
}
