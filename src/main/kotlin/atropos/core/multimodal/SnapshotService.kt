package atropos.core.multimodal

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant

class SnapshotService(
    private val store: SnapshotStore = SnapshotStore(),
    private val repoRoot: Path = Path.of(System.getProperty("user.dir"))
) {
    fun captureTerminal(content: String, source: String = "terminal"): SnapshotReference {
        val hash = SnapshotReference.hash(content.toByteArray(StandardCharsets.UTF_8))
        val ref = SnapshotReference(
            kind = SnapshotKind.TERMINAL_BUFFER,
            source = source,
            contentHash = hash,
            byteSize = content.length
        )
        store.saveSnapshot(ref)
        store.saveRaw(ref.id, content.toByteArray(StandardCharsets.UTF_8))
        return ref
    }

    fun captureFile(filePath: String): SnapshotReference {
        val path = repoRoot.resolve(filePath)
        if (!Files.isRegularFile(path)) throw IllegalArgumentException("file not found: $filePath")
        val bytes = Files.readAllBytes(path)
        val hash = SnapshotReference.hash(bytes)
        val ref = SnapshotReference(
            kind = SnapshotKind.FILE_SNAPSHOT,
            source = filePath,
            contentHash = hash,
            byteSize = bytes.size
        )
        store.saveSnapshot(ref)
        store.saveRaw(ref.id, bytes)
        return ref
    }

    fun captureViewport(viewport: ViewportCapture, source: String? = null): SnapshotReference {
        val content = viewport.content
        val hash = SnapshotReference.hash(content.toByteArray(StandardCharsets.UTF_8))
        val ref = SnapshotReference(
            kind = SnapshotKind.COMPOSITE_VIEWPORT,
            source = source ?: "viewport:${viewport.width}x${viewport.height}",
            contentHash = hash,
            byteSize = content.length,
            metadata = mapOf(
                "width" to viewport.width.toString(),
                "height" to viewport.height.toString(),
                "cursorLine" to viewport.cursorLine.toString(),
                "cursorCol" to viewport.cursorCol.toString()
            )
        )
        store.saveSnapshot(ref)
        store.saveRaw(ref.id, content.toByteArray(StandardCharsets.UTF_8))
        return ref
    }

    fun captureComposeFrame(frame: ComposeFrameCapture): SnapshotReference {
        val content = frame.componentTree
        val hash = SnapshotReference.hash(content.toByteArray(StandardCharsets.UTF_8))
        val ref = SnapshotReference(
            kind = SnapshotKind.COMPOSE_FRAME,
            source = "compose:${frame.focusComponent ?: "root"}",
            contentHash = hash,
            byteSize = content.length,
            metadata = mapOf(
                "renderTimeMs" to frame.renderTimeMs.toString(),
                "focusComponent" to (frame.focusComponent ?: "none"),
                "layoutNodes" to frame.layoutNodes.size.toString()
            )
        )
        store.saveSnapshot(ref)
        store.saveRaw(ref.id, content.toByteArray(StandardCharsets.UTF_8))
        return ref
    }

    fun getSnapshot(id: String): SnapshotReference? = store.loadSnapshot(id)

    fun getRaw(id: String): ByteArray? = store.loadRaw(id)

    fun compareSnapshots(leftId: String, rightId: String): MultimodalInspection {
        val left = store.loadSnapshot(leftId) ?: return failedInspection("LEFT_SNAPSHOT_NOT_FOUND", leftId)
        val right = store.loadSnapshot(rightId) ?: return failedInspection("RIGHT_SNAPSHOT_NOT_FOUND", rightId)
        val match = left.contentHash == right.contentHash
        return MultimodalInspection(
            kind = InspectionKind.SCREENSHOT_COMPARE,
            severity = if (match) InspectionSeverity.INFO else InspectionSeverity.WARNING,
            sourceSnapshotId = leftId,
            referenceSnapshotId = rightId,
            findings = if (match) listOf("snapshots match") else listOf("snapshots differ: hash ${left.contentHash} != ${right.contentHash}"),
            matchScore = if (match) 1.0 else 0.0,
            passed = match
        )
    }

    fun listSnapshots(limit: Int = 50): List<SnapshotReference> = store.listSnapshots().takeLast(limit)

    fun recentSnapshots(kind: SnapshotKind? = null, limit: Int = 10): List<SnapshotReference> {
        val all = store.listSnapshots()
        return (if (kind != null) all.filter { it.kind == kind } else all).takeLast(limit)
    }

    private fun failedInspection(reason: String, snapshotId: String): MultimodalInspection {
        return MultimodalInspection(
            kind = InspectionKind.STATE_VERIFICATION,
            severity = InspectionSeverity.CRITICAL,
            sourceSnapshotId = snapshotId,
            findings = listOf(reason),
            passed = false
        )
    }
}

class SnapshotStore(private val root: Path = Path.of(System.getProperty("user.dir"))) {
    private val snapDir = root.resolve(".atropos/multimodal")
    private val indexFile = snapDir.resolve("snapshots.jsonl")

    fun saveSnapshot(ref: SnapshotReference) {
        Files.createDirectories(snapDir)
        val existing = listSnapshots().toMutableList()
        val idx = existing.indexOfFirst { it.id == ref.id }
        if (idx >= 0) existing[idx] = ref else existing += ref
        writeLines(indexFile, existing.map { snapshotToLine(it) })
    }

    fun listSnapshots(): List<SnapshotReference> {
        return readLines(indexFile).mapNotNull { lineToSnapshot(it) }
    }

    fun loadSnapshot(id: String): SnapshotReference? = listSnapshots().firstOrNull { it.id == id }

    fun saveRaw(id: String, data: ByteArray) {
        Files.createDirectories(snapDir)
        val path = snapDir.resolve("$id.raw")
        Files.write(path, data)
    }

    fun loadRaw(id: String): ByteArray? {
        val path = snapDir.resolve("$id.raw")
        return if (Files.isRegularFile(path)) Files.readAllBytes(path) else null
    }

    private fun snapshotToLine(ref: SnapshotReference): String {
        val meta = ref.metadata.entries.joinToString("&") { "${it.key}=${it.value}" }
        return listOf(ref.id, ref.kind.name, ref.source, ref.contentHash, ref.byteSize.toString(), ref.capturedAt.toString(), meta)
            .joinToString("\t") { it.replace('\t', ' ').replace('\n', ' ') }
    }

    private fun lineToSnapshot(line: String): SnapshotReference? {
        val parts = line.split("\t")
        if (parts.size < 6) return null
        return try {
            val meta = if (parts.size > 6) parseMetadata(parts[6]) else emptyMap()
            SnapshotReference(
                id = parts[0], kind = SnapshotKind.valueOf(parts[1]),
                source = parts[2], contentHash = parts[3],
                byteSize = parts[4].toInt(), capturedAt = Instant.parse(parts[5]),
                metadata = meta
            )
        } catch (_: Exception) { null }
    }

    private fun parseMetadata(raw: String): Map<String, String> {
        return raw.split("&").mapNotNull { kv ->
            val eq = kv.indexOf('=')
            if (eq < 0) null else kv.substring(0, eq) to kv.substring(eq + 1)
        }.toMap()
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
}
