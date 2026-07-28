package atropos.core.project

import atropos.core.security.RedactionFilter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant

class ProjectRegistry(
    private val repoRoot: Path = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize(),
    private val clock: () -> Instant = { Instant.now() },
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    private val root = repoRoot.resolve(".atropos/projects").normalize()
    private val index = root.resolve("projects.jsonl")

    fun register(name: String, kind: String = "app-factory", binding: RepositoryBinding = currentBinding()): ProjectRegistrationResult {
        val cleanName = redactionFilter.redact(name.trim()).ifBlank { "unnamed-project" }
        val existing = list().firstOrNull { it.name == cleanName && it.binding.repoRoot == binding.repoRoot }
        if (existing != null) return ProjectRegistrationResult(false, existing)
        val now = clock()
        val record = ProjectRecord(
            name = cleanName,
            kind = kind.trim().ifBlank { "project" },
            binding = binding.copy(repoRoot = redactionFilter.redact(binding.repoRoot)),
            createdAt = now,
            updatedAt = now
        )
        writeAll(list() + record)
        return ProjectRegistrationResult(true, record)
    }

    fun list(): List<ProjectRecord> {
        if (!Files.isRegularFile(index)) return emptyList()
        return Files.readAllLines(index, StandardCharsets.UTF_8).mapNotNull(::parse)
    }

    fun resolve(id: String): ProjectRecord? = list().firstOrNull { it.id == id }

    fun currentBinding(): RepositoryBinding =
        RepositoryBinding(
            repoRoot = repoRoot.toString(),
            branch = git(listOf("git", "branch", "--show-current")),
            baselineCommit = git(listOf("git", "rev-parse", "HEAD")),
            dirtyFingerprint = dirtyFingerprint()
        )

    private fun dirtyFingerprint(): String {
        val status = git(listOf("git", "status", "--porcelain"))
        return redactionFilter.stableFingerprint(status)
    }

    private fun git(command: List<String>): String =
        runCatching {
            val process = ProcessBuilder(command)
                .directory(repoRoot.toFile())
                .redirectErrorStream(true)
                .start()
            val out = process.inputStream.bufferedReader().readText().trim()
            process.waitFor()
            if (process.exitValue() == 0) redactionFilter.redact(out) else ""
        }.getOrDefault("")

    private fun writeAll(records: List<ProjectRecord>) {
        Files.createDirectories(root)
        val tmp = index.resolveSibling("${index.fileName}.${System.nanoTime()}.tmp")
        Files.writeString(tmp, records.joinToString("\n") { render(it) } + "\n", StandardCharsets.UTF_8)
        try {
            Files.move(tmp, index, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(tmp, index, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun render(record: ProjectRecord): String =
        listOf(
            record.id,
            encode(record.name),
            record.kind,
            encode(record.binding.repoRoot),
            encode(record.binding.branch),
            encode(record.binding.baselineCommit),
            encode(record.binding.dirtyFingerprint),
            record.createdAt.toString(),
            record.updatedAt.toString()
        ).joinToString("\t")

    private fun parse(line: String): ProjectRecord? {
        val p = line.split("\t")
        if (p.size < 9) return null
        return runCatching {
            ProjectRecord(
                id = p[0],
                name = decode(p[1]),
                kind = p[2],
                binding = RepositoryBinding(
                    repoRoot = decode(p[3]),
                    branch = decode(p[4]),
                    baselineCommit = decode(p[5]),
                    dirtyFingerprint = decode(p[6])
                ),
                createdAt = Instant.parse(p[7]),
                updatedAt = Instant.parse(p[8])
            )
        }.getOrNull()
    }

    private fun encode(text: String): String =
        java.util.Base64.getEncoder().encodeToString(text.toByteArray(StandardCharsets.UTF_8))

    private fun decode(value: String): String =
        runCatching { String(java.util.Base64.getDecoder().decode(value), StandardCharsets.UTF_8) }.getOrDefault("")
}
