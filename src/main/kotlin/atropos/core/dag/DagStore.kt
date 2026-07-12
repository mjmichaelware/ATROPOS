package atropos.core.dag

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class DagStore(private val root: Path = Path.of(System.getProperty("user.dir"))) {
    private val dagDir = root.resolve(".atropos/dag")
    private val nodesPath = dagDir.resolve("nodes.jsonl")
    private val docPath = dagDir.resolve("documents.jsonl")

    fun saveNode(node: DAGNode) {
        Files.createDirectories(dagDir)
        val existing = loadNodes().toMutableList()
        val idx = existing.indexOfFirst { it.id == node.id }
        if (idx >= 0) existing[idx] = node else existing += node
        writeLines(nodesPath, existing.map { nodeToLine(it) })
    }

    fun loadNodes(): List<DAGNode> {
        return readLines(nodesPath).mapNotNull { lineToNode(it) }
    }

    fun saveDocument(doc: SourceDocument) {
        Files.createDirectories(dagDir)
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
}
