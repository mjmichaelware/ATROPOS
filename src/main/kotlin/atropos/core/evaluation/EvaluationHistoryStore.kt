package atropos.core.evaluation

import atropos.core.security.RedactionFilter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class EvaluationHistoryStore(
    private val repoRoot: Path = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize(),
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    private val root = repoRoot.resolve(".atropos/evaluation").normalize()
    private val reports = root.resolve("reports.jsonl")

    fun append(report: EvaluationReport) {
        Files.createDirectories(root)
        val existing = readLines(reports)
        writeLines(reports, existing + render(report))
    }

    fun list(limit: Int = 100): List<EvaluationReport> =
        readLines(reports).mapNotNull(::parse).takeLast(limit.coerceIn(1, 5000))

    fun latestFor(subjectId: String): EvaluationReport? =
        list(5000).lastOrNull { it.subjectId == subjectId }

    private fun render(report: EvaluationReport): String {
        val metrics = report.metrics.joinToString(";;") {
            listOf(it.kind.name, it.passed, it.severity.name, encode(redactionFilter.redact(it.evidence))).joinToString(",")
        }
        return listOf(
            report.id,
            report.subjectId,
            report.runId.orEmpty(),
            report.artifactIds.joinToString("|"),
            report.createdAt.toString(),
            metrics
        ).joinToString("\t")
    }

    private fun parse(line: String): EvaluationReport? {
        val p = line.split("\t")
        if (p.size < 6) return null
        return runCatching {
            EvaluationReport(
                id = p[0],
                subjectId = p[1],
                runId = p[2].ifBlank { null },
                artifactIds = p[3].split("|").filter { it.isNotBlank() },
                createdAt = java.time.Instant.parse(p[4]),
                metrics = p[5].split(";;").filter { it.isNotBlank() }.mapNotNull(::parseMetric)
            )
        }.getOrNull()
    }

    private fun parseMetric(raw: String): EvaluationMetric? {
        val p = raw.split(",", limit = 4)
        if (p.size < 4) return null
        return runCatching {
            EvaluationMetric(
                kind = EvaluationMetricKind.valueOf(p[0]),
                passed = p[1].toBoolean(),
                severity = EvaluationSeverity.valueOf(p[2]),
                evidence = decode(p[3])
            )
        }.getOrNull()
    }

    private fun readLines(path: Path): List<String> {
        if (!Files.isRegularFile(path)) return emptyList()
        return Files.readAllLines(path, StandardCharsets.UTF_8).filter { it.isNotBlank() }
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

    private fun encode(text: String): String =
        java.util.Base64.getEncoder().encodeToString(text.toByteArray(StandardCharsets.UTF_8))

    private fun decode(value: String): String =
        runCatching { String(java.util.Base64.getDecoder().decode(value), StandardCharsets.UTF_8) }.getOrDefault("")
}
