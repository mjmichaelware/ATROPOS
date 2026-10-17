package atropos.core.integration

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.time.Instant

data class CasEvidencePointer(val casHash: String, val ledgerOffset: Long, val recordedAt: Instant)

class McpEvidenceAndAuthorityPolicy(private val repoRoot: Path) {
    private val casDir = repoRoot.resolve(".atropos/cas").normalize()
    private val ledgerFile = repoRoot.resolve(".atropos/ledger/events.jsonl").normalize()
    private val agentsMd = repoRoot.resolve("AGENTS.md").normalize()

    init {
        Files.createDirectories(casDir)
        Files.createDirectories(ledgerFile.parent)
    }

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun assertAuthorityProtection(targetFile: String) {
        val normalized = targetFile.replace('\\', '/').trim().trimStart('/')
        if (normalized == "AGENTS.md" || normalized == "SWARM.md" ||
            normalized.startsWith(".atropos/ledger") || normalized.startsWith(".git")
        ) {
            atropos.core.thinking.Thinking.step("security", "Authority check blocked modification to $normalized")
            throw SecurityException("Authority Violation: Protected sovereign path: $normalized")
        }
        atropos.core.thinking.Thinking.detail("security", "Authority path validation passed for $normalized")
    }

    fun appendToLedger(toolName: String, callerId: String, payload: String): CasEvidencePointer {
        val bytes = payload.toByteArray(StandardCharsets.UTF_8)
        val hash = sha256Hex(bytes)
        val casTarget = casDir.resolve(hash).toFile()
        if (!casTarget.exists()) {
            casTarget.writeBytes(bytes)
        }

        val now = Instant.now()
        val ledgerLine = """{"timestamp":"$now","caller":"$callerId","tool":"$toolName","casHash":"$hash","bytes":${bytes.size}}""" + "\n"

        synchronized(this) {
            val offset = if (ledgerFile.toFile().exists()) ledgerFile.toFile().length() else 0L
            Files.writeString(
                ledgerFile,
                ledgerLine,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            )
            atropos.core.thinking.Thinking.step("mcp", "Recording CAS evidence for $toolName")
            atropos.core.thinking.Thinking.detail("mcp", "CAS evidence anchored: hash=${hash.take(12)}... offset=$offset")
            return CasEvidencePointer(hash, offset, now)
        }
    }

    fun verifyAgentsMdIntegrity(): Boolean {
        if (!Files.exists(agentsMd)) return true
        val lockFile = repoRoot.resolve(".atropos/authority/agents.md.sha256").toFile()
        if (!lockFile.exists()) return true
        val expected = lockFile.readText(StandardCharsets.UTF_8).trim()
        val actual = sha256Hex(Files.readAllBytes(agentsMd))
        val matched = expected.equals(actual, ignoreCase = true)
        atropos.core.thinking.Thinking.step("security", "Verifying AGENTS.md authority integrity")
        atropos.core.thinking.Thinking.detail("security", "AGENTS.md hash integrity verified: $matched")
        return matched
    }
}
