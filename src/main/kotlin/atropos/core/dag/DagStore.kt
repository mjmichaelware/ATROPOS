package atropos.core.dag

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.util.UUID

class DagStore(private val root: Path = Path.of(System.getProperty("user.dir"))) {
    private val authorityDagDir = root.resolve(".atropos/dag")
    private val nodesPath = authorityDagDir.resolve("nodes.jsonl")
    private val docPath = authorityDagDir.resolve("documents.jsonl")
    private val executionRoot = authorityDagDir.resolve("execution")
    private val executionDefinitionsDir = executionRoot.resolve("definitions")
    private val executionLockFile = executionRoot.resolve("dag.lock")

    fun saveNode(node: DAGNode) {
        Files.createDirectories(authorityDagDir)
        val existing = loadNodes().toMutableList()
        val idx = existing.indexOfFirst { it.id == node.id }
        if (idx >= 0) existing[idx] = node else existing += node
        writeLines(nodesPath, existing.map { nodeToLine(it) })
    }

    fun loadNodes(): List<DAGNode> {
        return readLines(nodesPath).mapNotNull { lineToNode(it) }
    }

    fun saveDocument(doc: SourceDocument) {
        Files.createDirectories(authorityDagDir)
        val existing = loadDocuments().toMutableList()
        val idx = existing.indexOfFirst { it.id == doc.id }
        if (idx >= 0) existing[idx] = doc else existing += doc
        writeLines(docPath, existing.map { docToLine(it) })
    }

    fun loadDocuments(): List<SourceDocument> {
        return readLines(docPath).mapNotNull { lineToDoc(it) }
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
        writeDefinition(definition)
        return definition
    }

    fun listDags(): List<DagDefinition> {
        if (!Files.isDirectory(executionDefinitionsDir)) return emptyList()
        return Files.list(executionDefinitionsDir).use { stream ->
            stream.filter { Files.isDirectory(it) }
                .toList()
                .mapNotNull { readDefinition(it.resolve("dag.meta")) }
                .sortedByDescending { it.createdAt }
        }
    }

    fun readDag(dagId: String): DagDefinition? {
        val file = executionDefinitionsDir.resolve(dagId).resolve("dag.meta").normalize()
        if (!file.startsWith(executionDefinitionsDir) || !Files.isRegularFile(file)) return null
        return readDefinition(file)
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
                .map { readNodeFile(it) }
                .orElse(null)
        }
    }

    fun writeNode(node: DagNode): DagNode {
        Files.createDirectories(node.metaFile.parent)
        val updated = node.copy(updatedAt = Instant.now())
        val tmp = Files.createTempFile(node.metaFile.parent, node.id, ".tmp")
        val bytes = renderExecutionNode(updated).toByteArray(StandardCharsets.UTF_8)
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

    private fun nodeToLine(n: DAGNode): String {
        val parts = listOf(
            n.id, n.requirementId, n.parentIds.joinToString(","), n.children.joinToString(","),
            n.dependencies.joinToString(","), n.state.name, n.implementationFiles.joinToString("|"),
            n.testFiles.joinToString("|"), n.hash
        )
        return parts.joinToString("\t") { it.replace('\t', ' ').replace('\n', ' ') }
    }

    private fun lineToNode(line: String): DAGNode? {
        val parts = line.split("\t")
        if (parts.size < 9) return null
        return try {
            DAGNode(
                id = parts[0], requirementId = parts[1],
                parentIds = parts[2].split(",").filter { it.isNotBlank() },
                children = parts[3].split(",").filter { it.isNotBlank() },
                dependencies = parts[4].split(",").filter { it.isNotBlank() },
                state = DAGNodeState.valueOf(parts[5]),
                implementationFiles = parts[6].split("|").filter { it.isNotBlank() },
                testFiles = parts[7].split("|").filter { it.isNotBlank() },
                hash = parts[8]
            )
        } catch (_: Exception) { null }
    }

    private fun docToLine(d: SourceDocument): String {
        val secs = d.sections.joinToString(";") { "${it.sectionId}:${it.startLine}:${it.endLine}" }
        return listOf(d.id, d.sha256, d.size.toString(), d.format, d.originalPath, d.ingestionTime.toString(), d.version.toString(), secs).joinToString("\t")
    }

    private fun lineToDoc(line: String): SourceDocument? {
        val parts = line.split("\t")
        if (parts.size < 8) return null
        return try {
            SourceDocument(
                id = parts[0], sha256 = parts[1], size = parts[2].toLong(), format = parts[3],
                originalPath = parts[4], ingestionTime = java.time.Instant.parse(parts[5]),
                version = parts[6].toInt(),
                sections = parts[7].split(";").filter { it.isNotBlank() }.map { sec ->
                    val sp = sec.split(":")
                    SourceSection(sectionId = sp[0], heading = sp[0], startLine = sp.getOrNull(1)?.toInt() ?: 0, endLine = sp.getOrNull(2)?.toInt() ?: 0, content = "", coordinates = sec)
                }
            )
        } catch (_: Exception) { null }
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

    private fun writeDefinition(definition: DagDefinition) {
        Files.createDirectories(definition.metaFile.parent)
        val tmp = Files.createTempFile(definition.metaFile.parent, definition.id, ".tmp")
        val content = buildString {
            appendLine("id=${definition.id}")
            appendLine("labelB64=${encode(definition.label)}")
            appendLine("projectId=${definition.projectId ?: ""}")
            appendLine("nodeIds=${definition.nodes.joinToString(",") { it.id }}")
            appendLine("createdAt=${definition.createdAt}")
            appendLine("updatedAt=${definition.updatedAt}")
        }
        Files.writeString(tmp, content, StandardCharsets.UTF_8)
        try {
            Files.move(tmp, definition.metaFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(tmp, definition.metaFile, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun readDefinition(file: Path): DagDefinition? {
        val fields = runCatching {
            Files.readAllLines(file, StandardCharsets.UTF_8).mapNotNull { line ->
                val index = line.indexOf('=')
                if (index <= 0) null else line.substring(0, index) to line.substring(index + 1)
            }.toMap()
        }.getOrNull() ?: return null
        val id = fields["id"].orEmpty()
        val nodeIds = fields["nodeIds"]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        val nodes = nodeIds.mapNotNull { readExecutionNode(id, it) }
        return DagDefinition(
            id = id,
            label = decode(fields["labelB64"]),
            projectId = fields["projectId"]?.takeIf { it.isNotBlank() },
            nodes = nodes,
            createdAt = parseInstant(fields["createdAt"]) ?: Instant.EPOCH,
            updatedAt = parseInstant(fields["updatedAt"]) ?: Instant.EPOCH,
            metaFile = file
        )
    }

    private fun readExecutionNode(dagId: String, nodeId: String): DagNode? {
        val file = executionDefinitionsDir.resolve(dagId).resolve("$nodeId.meta").normalize()
        if (!file.startsWith(executionDefinitionsDir) || !Files.isRegularFile(file)) return null
        return readNodeFile(file)
    }

    private fun readNodeFile(file: Path): DagNode? {
        val fields = runCatching {
            Files.readAllLines(file, StandardCharsets.UTF_8).mapNotNull { line ->
                val index = line.indexOf('=')
                if (index <= 0) null else line.substring(0, index) to line.substring(index + 1)
            }.toMap()
        }.getOrNull() ?: return null
        return runCatching {
            DagNode(
                id = fields["id"].orEmpty(),
                dagId = fields["dagId"]?.takeIf { it.isNotBlank() },
                label = decode(fields["labelB64"]),
                dependencies = fields["dependencies"]?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
                territory = fields["territory"]?.split("|")?.filter { it.isNotBlank() } ?: emptyList(),
                action = DagNodeAction.valueOf(fields["action"].orEmpty()),
                actionPayload = decode(fields["actionPayloadB64"]).takeIf { it.isNotBlank() },
                expectedOutputs = fields["expectedOutputs"]?.split("|")?.filter { it.isNotBlank() } ?: emptyList(),
                // Absent on nodes written before fail-closed verification: they
                // opted out of nothing.
                optionalChecks = fields["optionalChecks"]?.split("|")?.filter { it.isNotBlank() }?.toSet() ?: emptySet(),
                maxAttempts = fields["maxAttempts"]?.toIntOrNull() ?: 2,
                retryDelaySeconds = fields["retryDelaySeconds"]?.toLongOrNull() ?: 15L,
                state = DagNodeState.valueOf(fields["state"].orEmpty()),
                claimToken = fields["claimToken"]?.takeIf { it.isNotBlank() },
                claimOwner = fields["claimOwner"]?.takeIf { it.isNotBlank() },
                claimExpiresAt = parseInstant(fields["claimExpiresAt"]),
                attempts = fields["attempts"]?.toIntOrNull() ?: 0,
                result = decode(fields["resultB64"]).takeIf { it.isNotBlank() },
                failureReason = decode(fields["failureReasonB64"]).takeIf { it.isNotBlank() },
                childJobId = fields["childJobId"]?.takeIf { it.isNotBlank() },
                lastMessage = decode(fields["lastMessageB64"]).takeIf { it.isNotBlank() },
                createdAt = parseInstant(fields["createdAt"]) ?: Instant.EPOCH,
                updatedAt = parseInstant(fields["updatedAt"]) ?: Instant.EPOCH,
                finishedAt = parseInstant(fields["finishedAt"]),
                metaFile = file
            )
        }.getOrNull()
    }

    private fun renderExecutionNode(node: DagNode): String = buildString {
        appendLine("id=${node.id}")
        appendLine("dagId=${node.dagId ?: ""}")
        appendLine("labelB64=${encode(node.label)}")
        appendLine("dependencies=${node.dependencies.joinToString(",")}")
        appendLine("territory=${node.territory.joinToString("|")}")
        appendLine("action=${node.action}")
        appendLine("actionPayloadB64=${encode(node.actionPayload.orEmpty())}")
        appendLine("expectedOutputs=${node.expectedOutputs.joinToString("|")}")
        appendLine("optionalChecks=${node.optionalChecks.joinToString("|")}")
        appendLine("maxAttempts=${node.maxAttempts}")
        appendLine("retryDelaySeconds=${node.retryDelaySeconds}")
        appendLine("state=${node.state}")
        appendLine("claimToken=${node.claimToken ?: ""}")
        appendLine("claimOwner=${node.claimOwner ?: ""}")
        appendLine("claimExpiresAt=${node.claimExpiresAt ?: ""}")
        appendLine("attempts=${node.attempts}")
        appendLine("resultB64=${encode(node.result.orEmpty())}")
        appendLine("failureReasonB64=${encode(node.failureReason.orEmpty())}")
        appendLine("childJobId=${node.childJobId ?: ""}")
        appendLine("lastMessageB64=${encode(node.lastMessage.orEmpty())}")
        appendLine("createdAt=${node.createdAt}")
        appendLine("updatedAt=${node.updatedAt}")
        appendLine("finishedAt=${node.finishedAt ?: ""}")
    }

    private fun touchDag(dagId: String) {
        val definition = readDag(dagId) ?: return
        writeDefinition(definition.copy(updatedAt = Instant.now()))
    }

    private fun parseInstant(value: String?): Instant? =
        value?.takeIf { it.isNotBlank() }?.let { runCatching { Instant.parse(it) }.getOrNull() }

    private fun encode(value: String): String =
        java.util.Base64.getEncoder().encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decode(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return runCatching {
            String(java.util.Base64.getDecoder().decode(value), StandardCharsets.UTF_8)
        }.getOrDefault("")
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
