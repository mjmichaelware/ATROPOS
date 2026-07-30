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
    private val root = repoRoot.resolve(".atropos/hr").normalize()
    private val auditFile = root.resolve("router-audit.tsv")

    fun append(entry: HrRouterAuditEntry) {
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
        if (!Files.isRegularFile(auditFile)) return emptyList()
        return Files.readAllLines(auditFile, StandardCharsets.UTF_8)
            .mapNotNull(::parse)
            .takeLast(limit.coerceIn(1, 5000))
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
            encode(entry.requestedPaths.joinToString("|"))
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
                    ?: emptyList()
            )
        }.getOrNull()
    }

    private fun encode(value: String): String =
        Base64.getEncoder().encodeToString(redactionFilter.redact(value).toByteArray(StandardCharsets.UTF_8))

    private fun decode(value: String): String =
        runCatching { String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8) }.getOrDefault("")
}
