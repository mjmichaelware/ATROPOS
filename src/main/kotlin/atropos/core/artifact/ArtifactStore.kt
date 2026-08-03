package atropos.core.artifact

import atropos.core.AtroposRepoRootLocator
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant

class ArtifactStore(private val root: Path = AtroposRepoRootLocator.resolve()) {
    private val artDir = root.resolve(".atropos/artifacts")
    private val artFile = artDir.resolve("artifacts.jsonl")
    private val verFile = artDir.resolve("verifications.jsonl")
    private val proofFile = artDir.resolve("install_proofs.jsonl")
    private val commitFile = artDir.resolve("commit_candidates.jsonl")

    fun saveArtifacts(artifacts: List<Artifact>) {
        Files.createDirectories(artDir)
        val existing = loadArtifacts().toMutableList()
        artifacts.forEach { a ->
            val idx = existing.indexOfFirst { it.id == a.id }
            if (idx >= 0) existing[idx] = a else existing += a
        }
        writeLines(artFile, existing.map { artifactToLine(it) })
    }

    fun loadArtifacts(): List<Artifact> = readLines(artFile).mapNotNull { lineToArtifact(it) }
    fun loadArtifact(id: String): Artifact? = loadArtifacts().firstOrNull { it.id == id }

    fun saveVerifications(verifications: List<VerificationEvidence>) {
        Files.createDirectories(artDir)
        val existing = loadVerifications().toMutableList()
        verifications.forEach { v ->
            val idx = existing.indexOfFirst { it.id == v.id }
            if (idx >= 0) existing[idx] = v else existing += v
        }
        writeLines(verFile, existing.map { verificationToLine(it) })
    }

    fun loadVerifications(): List<VerificationEvidence> = readLines(verFile).mapNotNull { lineToVerification(it) }

    fun saveInstallProofs(proofs: List<InstallProof>) {
        Files.createDirectories(artDir)
        val existing = loadInstallProofs().toMutableList()
        proofs.forEach { p ->
            val idx = existing.indexOfFirst { it.id == p.id }
            if (idx >= 0) existing[idx] = p else existing += p
        }
        writeLines(proofFile, existing.map { proofToLine(it) })
    }

    fun loadInstallProofs(): List<InstallProof> = readLines(proofFile).mapNotNull { lineToProof(it) }

    fun saveCommitCandidates(candidates: List<CommitCandidate>) {
        Files.createDirectories(artDir)
        val existing = loadCommitCandidates().toMutableList()
        candidates.forEach { c ->
            val idx = existing.indexOfFirst { it.id == c.id }
            if (idx >= 0) existing[idx] = c else existing += c
        }
        writeLines(commitFile, existing.map { candidateToLine(it) })
    }

    fun loadCommitCandidates(): List<CommitCandidate> = readLines(commitFile).mapNotNull { lineToCandidate(it) }

    private fun artifactToLine(a: Artifact): String {
        val meta = a.metadata.entries.joinToString("&") { "${it.key}=${it.value}" }
        return listOf(a.id, a.kind.name, a.name, a.filePath, a.sha256, a.byteSize.toString(),
            a.state.name, a.buildCommand, a.buildDurationMs.toString(), a.createdAt.toString(), meta)
            .joinToString("\t") { it.replace('\t', ' ').replace('\n', ' ') }
    }

    private fun lineToArtifact(line: String): Artifact? {
        val parts = line.split("\t")
        if (parts.size < 10) return null
        return try {
            val meta = if (parts.size > 10) parseMeta(parts[10]) else emptyMap()
            Artifact(id = parts[0], kind = ArtifactKind.valueOf(parts[1]), name = parts[2],
                filePath = parts[3], sha256 = parts[4], byteSize = parts[5].toLong(),
                state = ArtifactState.valueOf(parts[6]), buildCommand = parts[7],
                buildDurationMs = parts[8].toLong(), createdAt = Instant.parse(parts[9]), metadata = meta)
        } catch (_: Exception) { null }
    }

    private fun verificationToLine(v: VerificationEvidence): String =
        listOf(v.id, v.artifactId, v.kind.name, v.passed.toString(), v.evidence.replace('\n', ' '), v.timestamp.toString())
            .joinToString("\t")

    private fun lineToVerification(line: String): VerificationEvidence? {
        val parts = line.split("\t"); if (parts.size < 6) return null
        return try { VerificationEvidence(id = parts[0], artifactId = parts[1], kind = VerificationKind.valueOf(parts[2]),
            passed = parts[3].toBoolean(), evidence = parts[4], timestamp = Instant.parse(parts[5])) }
        catch (_: Exception) { null }
    }

    private fun proofToLine(p: InstallProof): String =
        listOf(p.id, p.artifactId, p.targetPath, p.installedAt.toString(), p.verified.toString(),
            p.runOutput.replace('\n', ' '), p.durationMs.toString(), p.screenshots.joinToString("|"))
            .joinToString("\t")

    private fun lineToProof(line: String): InstallProof? {
        val parts = line.split("\t"); if (parts.size < 7) return null
        return try { InstallProof(id = parts[0], artifactId = parts[1], targetPath = parts[2],
            installedAt = Instant.parse(parts[3]), verified = parts[4].toBoolean(),
            runOutput = parts[5], durationMs = parts[6].toLong(), screenshots = parts.getOrNull(7)?.split("|")?.filter { it.isNotBlank() }.orEmpty()) }
        catch (_: Exception) { null }
    }

    private fun candidateToLine(c: CommitCandidate): String =
        listOf(c.id, c.message.replace('\n', ' '), c.files.joinToString("|"), c.artifactIds.joinToString("|"),
            c.proofIds.joinToString("|"), c.preparedAt.toString(), c.territoryChecked.toString(),
            c.secretScanned.toString(), c.readyForCommit.toString()).joinToString("\t")

    private fun lineToCandidate(line: String): CommitCandidate? {
        val parts = line.split("\t"); if (parts.size < 9) return null
        return try { CommitCandidate(id = parts[0], message = parts[1],
            files = parts[2].split("|").filter { it.isNotBlank() },
            artifactIds = parts[3].split("|").filter { it.isNotBlank() },
            proofIds = parts[4].split("|").filter { it.isNotBlank() },
            preparedAt = Instant.parse(parts[5]), territoryChecked = parts[6].toBoolean(),
            secretScanned = parts[7].toBoolean(), readyForCommit = parts[8].toBoolean()) }
        catch (_: Exception) { null }
    }

    private fun parseMeta(raw: String): Map<String, String> =
        raw.split("&").mapNotNull { kv ->
            val eq = kv.indexOf('='); if (eq < 0) null else kv.substring(0, eq) to kv.substring(eq + 1)
        }.toMap()

    private fun readLines(path: Path): List<String> {
        if (!Files.isRegularFile(path)) return emptyList()
        return Files.readAllLines(path, StandardCharsets.UTF_8).map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("#") }
    }

    private fun writeLines(path: Path, lines: List<String>) {
        Files.createDirectories(path.parent)
        val tmp = path.resolveSibling("${path.fileName}.${System.nanoTime()}.tmp")
        Files.writeString(tmp, lines.joinToString("\n") + "\n", StandardCharsets.UTF_8)
        try { Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
        catch (_: Exception) { Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING) }
    }
}
