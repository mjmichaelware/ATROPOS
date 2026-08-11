package atropos.core.hr

import atropos.core.AtroposRepoRootLocator
import atropos.core.security.RedactionFilter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.Base64

class HrRouterAuditStore(
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    private val normalizedRoot = repoRoot.toAbsolutePath().normalize()
    private val root = normalizedRoot.resolve(".atropos/hr").normalize()
    private val auditFile = root.resolve("router-audit.tsv")

    fun append(entry: HrRouterAuditEntry) {
        require(!hasSymlinkBoundary(auditFile)) { "HR audit store crosses a symbolic link" }
        Files.createDirectories(root)
        val current = if (Files.isRegularFile(auditFile)) Files.readString(auditFile, StandardCharsets.UTF_8) else ""
        val tmp = auditFile.resolveSibling("router-audit.${System.nanoTime()}.tmp")
        Files.writeString(tmp, current + render(entry) + "\n", StandardCharsets.UTF_8)
        try {
            Files.move(tmp, auditFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(tmp, auditFile, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    fun list(limit: Int = 100): List<HrRouterAuditEntry> {
        if (hasSymlinkBoundary(auditFile)) return emptyList()
        if (!Files.isRegularFile(auditFile)) return emptyList()
        val boundedLimit = limit.coerceIn(1, 5000)
        val tail = java.util.ArrayDeque<HrRouterAuditEntry>(boundedLimit)
        Files.newBufferedReader(auditFile, StandardCharsets.UTF_8).useLines { lines ->
            lines.mapNotNull(::parse).forEach { entry ->
                if (tail.size == boundedLimit) tail.removeFirst()
                tail.addLast(entry)
            }
        }
        return tail.toList()
    }

    private fun render(entry: HrRouterAuditEntry): String =
        listOf(
            entry.requestId,
            entry.sourceOwnerId,
            entry.targetOwnerId,
            entry.kind.name,
            entry.risk.name,
            entry.approved.toString(),
            entry.action.name,
            encode(entry.reason),
            entry.timestamp.toString(),
            entry.sourceTerritoryId,
            entry.targetTerritoryId,
            encode(entry.requestedPaths.joinToString("|")),
            encode(entry.taskId),
            encode(entry.sourceCoordinates.joinToString("|")),
            entry.needToKnowSha256.orEmpty(),
            entry.classification.name,
            entry.sourceRole?.name.orEmpty(),
            entry.targetRole?.name.orEmpty()
        ).joinToString("\t")

    private fun parse(line: String): HrRouterAuditEntry? {
        val parts = line.split("\t")
        if (parts.size < 9) return null
        return runCatching {
            HrRouterAuditEntry(
                requestId = parts[0],
                sourceOwnerId = parts[1],
                targetOwnerId = parts[2],
                kind = InformationKind.valueOf(parts[3]),
                risk = CrossBoundaryRisk.valueOf(parts[4]),
                approved = parts[5].toBoolean(),
                action = HrRouteAction.valueOf(parts[6]),
                reason = decode(parts[7]),
                timestamp = Instant.parse(parts[8]),
                sourceTerritoryId = parts.getOrNull(9).orEmpty(),
                targetTerritoryId = parts.getOrNull(10).orEmpty(),
                requestedPaths = parts.getOrNull(11)
                    ?.let(::decode)
                    ?.split("|")
                    ?.filter { it.isNotBlank() }
                    ?: emptyList(),
                taskId = parts.getOrNull(12)?.let(::decode).orEmpty(),
                sourceCoordinates = parts.getOrNull(13)
                    ?.let(::decode)
                    ?.split("|")
                    ?.filter { it.isNotBlank() }
                    ?: emptyList(),
                needToKnowSha256 = parts.getOrNull(14)?.takeIf { it.isNotBlank() },
                classification = parts.getOrNull(15)
                    ?.let { runCatching { InformationClassification.valueOf(it) }.getOrNull() }
                    ?: InformationClassification.INTERNAL,
                sourceRole = parts.getOrNull(16)
                    ?.let { runCatching { atropos.core.hierarchy.HierarchyRole.valueOf(it) }.getOrNull() },
                targetRole = parts.getOrNull(17)
                    ?.let { runCatching { atropos.core.hierarchy.HierarchyRole.valueOf(it) }.getOrNull() }
            )
        }.getOrNull()
    }

    private fun encode(value: String): String =
        Base64.getEncoder().encodeToString(redactionFilter.redact(value).toByteArray(StandardCharsets.UTF_8))

    private fun decode(value: String): String =
        runCatching { String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8) }.getOrDefault("")

    private fun hasSymlinkBoundary(path: Path): Boolean {
        var cursor: Path? = path
        while (cursor != null) {
            if (Files.isSymbolicLink(cursor)) return true
            if (cursor == normalizedRoot) break
            cursor = cursor.parent
        }
        return false
    }
}
