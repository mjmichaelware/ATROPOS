/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.governance

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

data class AmendmentRecord(
    val amendmentId: String,
    val timestampEpochMs: Long,
    val parentHash: String,
    val payloadSha256: String,
    val affectedAtomIds: List<String>
)

class CryptographicAmendmentChain(val repoRoot: Path) {
    private val chainFile = repoRoot.resolve(".atropos/governance/amendment_chain.jsonl")

    init {
        Files.createDirectories(chainFile.parent)
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun recordAmendment(affectedAtoms: List<String>, payload: String): AmendmentRecord {
        val payloadHash = sha256(payload.toByteArray(StandardCharsets.UTF_8))
        val parentHash = getLastHash()
        val record = AmendmentRecord(
            amendmentId = "amd-${System.currentTimeMillis()}",
            timestampEpochMs = System.currentTimeMillis(),
            parentHash = parentHash,
            payloadSha256 = payloadHash,
            affectedAtomIds = affectedAtoms
        )

        val json = JSONObject().apply {
            put("amendmentId", record.amendmentId)
            put("timestampEpochMs", record.timestampEpochMs)
            put("parentHash", record.parentHash)
            put("payloadSha256", record.payloadSha256)
            put("affectedAtomIds", JSONArray(record.affectedAtomIds))
        }

        Files.writeString(
            chainFile,
            json.toString() + "\n",
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        )
        return record
    }

    fun verifyChainIntegrity(): Boolean {
        if (!Files.exists(chainFile)) return true
        val lines = Files.readAllLines(chainFile)
        var expectedParent = "0000000000000000000000000000000000000000000000000000000000000000"
        for (line in lines) {
            if (line.isBlank()) continue
            val json = JSONObject(line)
            val parent = json.getString("parentHash")
            if (parent != expectedParent) return false
            expectedParent = sha256(line.toByteArray(StandardCharsets.UTF_8))
        }
        return true
    }

    fun getInvalidatedAtoms(sinceTimestamp: Long): Set<String> {
        if (!Files.exists(chainFile)) return emptySet()
        val invalidated = mutableSetOf<String>()
        val lines = Files.readAllLines(chainFile)
        for (line in lines) {
            if (line.isBlank()) continue
            val json = JSONObject(line)
            val ts = json.getLong("timestampEpochMs")
            if (ts >= sinceTimestamp) {
                val arr = json.getJSONArray("affectedAtomIds")
                for (i in 0 until arr.length()) {
                    invalidated.add(arr.getString(i))
                }
            }
        }
        return invalidated
    }

    private fun getLastHash(): String {
        if (!Files.exists(chainFile)) return "0000000000000000000000000000000000000000000000000000000000000000"
        val lines = Files.readAllLines(chainFile).filter { it.isNotBlank() }
        if (lines.isEmpty()) return "0000000000000000000000000000000000000000000000000000000000000000"
        return sha256(lines.last().toByteArray(StandardCharsets.UTF_8))
    }
}
