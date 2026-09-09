/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * B-SUP-039: Bit-Level Audit Log of Superiority Claims
 *
 * Implements an immutable audit log that records all
 * superiority claims at the bit level for verification.
 */
package atropos.core.supremacy

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant

object BitLevelAuditLog {
    data class Entry(
        val id: String = java.util.UUID.randomUUID().toString(),
        val claim: String,
        val evidenceHash: String,      // Hash of supporting evidence
        val claimHash: String,         // Hash of the claim itself
        val previousHash: String,      // Hash of previous entry (chain)
        val timestamp: Instant = Instant.now()
    ) {
        val chainHash: String = computeChainHash()

        private fun computeChainHash(): String {
            val data = "$claimHash|$previousHash|$timestamp"
            return MessageDigest.getInstance("SHA-256").digest(data.toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }

    private val log = mutableListOf<Entry>()
    private var lastHash = "0".repeat(64) // Genesis hash

    /**
     * Records a superiority claim in the audit log.
     */
    fun record(claim: String, evidence: String): Entry {
        val evidenceHash = hash(evidence)
        val claimHash = hash(claim)
        val entry = Entry(
            claim = claim,
            evidenceHash = evidenceHash,
            claimHash = claimHash,
            previousHash = lastHash
        )
        log.add(entry)
        lastHash = entry.chainHash
        return entry
    }

    /**
     * Verifies the integrity of the audit log chain.
     */
    fun verifyChain(): Boolean {
        var prevHash = "0".repeat(64)
        for (entry in log) {
            val expectedChainHash = computeChainHash(entry.claimHash, prevHash, entry.timestamp.toEpochMilli())
            if (entry.chainHash != expectedChainHash) return false
            if (entry.previousHash != prevHash) return false
            prevHash = entry.chainHash
        }
        return true
    }

    private fun computeChainHash(claimHash: String, prevHash: String, timestamp: Long): String {
        val data = "$claimHash|$prevHash|$timestamp"
        return MessageDigest.getInstance("SHA-256").digest(data.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    /**
     * Hashes a string with SHA-256.
     */
    fun hash(input: String): String {
        return MessageDigest.getInstance("SHA-256").digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    /**
     * Gets the current head hash.
     */
    fun headHash(): String = lastHash

    /**
     * Gets all entries.
     */
    fun entries(): List<Entry> = log.toList()

    /**
     * Persists the audit log to CAS.
     */
    fun persistLog(casDir: Path): Path {
        Files.createDirectories(casDir)
        val file = casDir.resolve("bit-level-audit-log.json")
        val json = log.map { e ->
            """
                {
                    "id": "${e.id}",
                    "claim": "${e.claim.replace("\"", "\\\"")}",
                    "evidenceHash": "${e.evidenceHash}",
                    "claimHash": "${e.claimHash}",
                    "previousHash": "${e.previousHash}",
                    "chainHash": "${e.chainHash}",
                    "timestamp": "${e.timestamp}"
                }
            """.trimIndent()
        }.joinToString(",\n", "[\n", "\n]")
        Files.writeString(file, json)
        return file
    }

    /**
     * CLI command to show audit log.
     */
    fun showLog(): String {
        return log.joinToString("\n") { "${it.id}: ${it.claim.take(80)}... (chain: ${it.chainHash.take(16)}...)" }
    }
}