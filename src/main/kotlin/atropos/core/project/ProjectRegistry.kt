package atropos.core.project

import atropos.core.AtroposRepoRootLocator
import atropos.core.policy.BoundedProcessRunner
import atropos.core.security.RedactionFilter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant

class ProjectRegistry(
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val clock: () -> Instant = { Instant.now() },
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    private val processRunner = BoundedProcessRunner()
    private val root = repoRoot.resolve(".atropos/projects").normalize()
    private val index = root.resolve("projects.jsonl")
    private val eventsDir = root.resolve("events").normalize()

    fun register(
        name: String,
        kind: String = "app-factory",
        binding: RepositoryBinding = currentBinding(),
        objective: String = ""
    ): ProjectRegistrationResult {
        val cleanName = redactionFilter.redact(name.trim()).ifBlank { "unnamed-project" }
        val existing = list().firstOrNull { it.name == cleanName && it.binding.repoRoot == binding.repoRoot }
        if (existing != null) return ProjectRegistrationResult(false, existing)
        val now = clock()
        val record = ProjectRecord(
            name = cleanName,
            kind = kind.trim().ifBlank { "project" },
            binding = binding.copy(repoRoot = redactionFilter.redact(binding.repoRoot)),
            createdAt = now,
            updatedAt = now,
            objective = redactionFilter.redact(objective.trim())
        )
        writeAll(list() + record)
        appendEvent(record.id, "created", "operator", "project created: $cleanName")
        return ProjectRegistrationResult(true, record)
    }

    /**
     * Replaces one record and appends a history entry.
     *
     * §4.0: "No approval action permanently hides previous history." The same
     * rule is applied to every mutation — the index holds current state, the
     * event log holds what happened, and the log is only ever appended to.
     */
    fun update(
        record: ProjectRecord,
        event: String = "updated",
        actor: String = "operator",
        message: String = event
    ): ProjectRecord {
        val updated = record.copy(updatedAt = clock())
        writeAll(list().map { if (it.id == updated.id) updated else it })
        appendEvent(updated.id, event, actor, message)
        return updated
    }

    fun setStatus(record: ProjectRecord, status: ProjectStatus, actor: String = "operator"): ProjectRecord =
        update(
            record.copy(status = status),
            event = "status_changed",
            actor = actor,
            message = "${record.status.canonical} -> ${status.canonical}"
        )

    fun linkWorkItem(record: ProjectRecord, workItemId: String, actor: String = "operator"): ProjectRecord =
        if (record.workItemIds.contains(workItemId)) record
        else update(
            record.copy(workItemIds = record.workItemIds + workItemId),
            event = "work_linked",
            actor = actor,
            message = "work item linked: $workItemId"
        )

    fun linkEvidence(record: ProjectRecord, evidenceId: String, actor: String = "operator"): ProjectRecord =
        if (record.evidenceIds.contains(evidenceId)) record
        else update(
            record.copy(evidenceIds = record.evidenceIds + evidenceId),
            event = "evidence_linked",
            actor = actor,
            message = "evidence linked: $evidenceId"
        )

    /** §2.9: history is permanent and searchable. Newest first. */
    fun history(projectId: String, limit: Int = 100): List<ProjectEvent> {
        val log = eventsDir.resolve("${safeId(projectId)}.log").normalize()
        if (!log.startsWith(eventsDir) || !Files.isRegularFile(log)) return emptyList()
        return Files.readAllLines(log, StandardCharsets.UTF_8)
            .asReversed()
            .take(limit.coerceAtLeast(0))
            .mapNotNull(::parseEvent)
    }

    private fun appendEvent(projectId: String, event: String, actor: String, message: String) {
        Files.createDirectories(eventsDir)
        val line = listOf(
            clock().toString(),
            safeAtom(event),
            safeAtom(actor),
            // History outlives the view that wrote it, so it is redacted at
            // rest rather than only on display (§13).
            encode(redactionFilter.compact(message, 240))
        ).joinToString("\t")
        Files.writeString(
            eventsDir.resolve("${safeId(projectId)}.log"),
            line + System.lineSeparator(),
            StandardCharsets.UTF_8,
            java.nio.file.StandardOpenOption.CREATE,
            java.nio.file.StandardOpenOption.APPEND
        )
    }

    private fun parseEvent(line: String): ProjectEvent? {
        val p = line.split("\t")
        if (p.size < 4) return null
        val timestamp = runCatching { Instant.parse(p[0]) }.getOrNull() ?: return null
        return ProjectEvent(timestamp = timestamp, event = p[1], actor = p[2], message = decode(p[3]))
    }

    private fun safeId(value: String): String = value.replace(Regex("[^A-Za-z0-9._-]"), "_")

    private fun safeAtom(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "event" }

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
            val result = processRunner.run(
                command = command,
                directory = repoRoot,
                timeoutMillis = 5_000L,
                maxOutputBytes = 64 * 1024,
                maxOutputLines = 1_000
            )
            val out = (result.stdout + result.stderr).trim()
            if (!result.timedOut && result.launchError == null && result.exitCode == 0 && !result.outputTruncated) {
                redactionFilter.redact(out)
            } else ""
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
            record.updatedAt.toString(),
            encode(record.objective),
            record.status.name,
            record.workItemIds.joinToString(","),
            record.evidenceIds.joinToString(",")
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
                updatedAt = Instant.parse(p[8]),
                // Columns added after the first release: a record written by
                // an earlier build simply has none, and takes the defaults.
                objective = p.getOrNull(9)?.let(::decode).orEmpty(),
                status = p.getOrNull(10)
                    ?.let { raw -> ProjectStatus.entries.firstOrNull { it.name == raw } }
                    ?: ProjectStatus.IDLE,
                workItemIds = splitIds(p.getOrNull(11)),
                evidenceIds = splitIds(p.getOrNull(12))
            )
        }.getOrNull()
    }

    private fun splitIds(value: String?): List<String> =
        value?.split(',')?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()

    private fun encode(text: String): String =
        java.util.Base64.getEncoder().encodeToString(text.toByteArray(StandardCharsets.UTF_8))

    private fun decode(value: String): String =
        runCatching { String(java.util.Base64.getDecoder().decode(value), StandardCharsets.UTF_8) }.getOrDefault("")
}
