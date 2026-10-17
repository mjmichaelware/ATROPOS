package atropos.core.supremacy

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import java.util.TreeMap

data class MerkleLeaf(val relativePath: String, val sha256Hex: String, val sizeBytes: Long)

data class MerkleTree(val rootHashHex: String, val leafCount: Int, val leaves: List<MerkleLeaf>)

data class ZeroEntropyCertificate(
    val schemaVersion: String = "1.0",
    val rootHash: String,
    val sourceTreeHash: String,
    val environmentHash: String,
    val issuedAt: Instant,
    val leafManifest: List<MerkleLeaf>
)

class ReproducibilityCertificateEngine {
    private fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    fun computeLeaf(baseDir: Path, file: Path): MerkleLeaf {
        val relPath = baseDir.relativize(file).toString().replace('\\', '/')
        val bytes = Files.readAllBytes(file)
        val contentHash = bytesToHex(sha256(bytes))
        return MerkleLeaf(relPath, contentHash, bytes.size.toLong())
    }

    fun buildMerkleTree(leaves: List<MerkleLeaf>): MerkleTree {
        require(leaves.isNotEmpty()) { "Merkle tree requires at least one leaf node" }
        val sortedLeaves = leaves.sortedBy { it.relativePath }
        var currentLevel: List<ByteArray> = sortedLeaves.map { leaf ->
            val payload = "${leaf.relativePath}:${leaf.sizeBytes}:${leaf.sha256Hex}"
            sha256(payload.toByteArray(StandardCharsets.UTF_8))
        }

        while (currentLevel.size > 1) {
            val nextLevel = mutableListOf<ByteArray>()
            var i = 0
            while (i < currentLevel.size) {
                if (i + 1 < currentLevel.size) {
                    val left = currentLevel[i]
                    val right = currentLevel[i + 1]
                    val combined = if (compareByteArrays(left, right) <= 0) left + right else right + left
                    nextLevel.add(sha256(combined))
                } else {
                    nextLevel.add(sha256(currentLevel[i] + currentLevel[i]))
                }
                i += 2
            }
            currentLevel = nextLevel
        }

        return MerkleTree(
            rootHashHex = bytesToHex(currentLevel.first()),
            leafCount = sortedLeaves.size,
            leaves = sortedLeaves
        )
    }

    fun generateCertificate(
        repoRoot: Path,
        relativeFiles: List<String>,
        environmentVariables: Map<String, String>
    ): ZeroEntropyCertificate {
        atropos.core.thinking.Thinking.step("verify", "Generating bit-exact Merkle certificate")
        val leaves = relativeFiles.map { computeLeaf(repoRoot, repoRoot.resolve(it).normalize()) }
        val tree = buildMerkleTree(leaves)

        val envEntries = TreeMap(environmentVariables.filterKeys {
            it.startsWith("ATROPOS_") || it in setOf("JAVA_HOME", "SOURCE_DATE_EPOCH")
        })
        val envPayload = envEntries.entries.joinToString(";") { "${it.key}=${it.value}" }
        val envHash = bytesToHex(sha256(envPayload.toByteArray(StandardCharsets.UTF_8)))

        atropos.core.thinking.Thinking.detail("verify", "Computed Merkle root: ${tree.rootHashHex.take(16)}... across ${tree.leafCount} leaves")
        return ZeroEntropyCertificate(
            rootHash = tree.rootHashHex,
            sourceTreeHash = tree.rootHashHex,
            environmentHash = envHash,
            issuedAt = Instant.now(),
            leafManifest = tree.leaves
        )
    }

    fun verifyCertificate(certificate: ZeroEntropyCertificate, repoRoot: Path): Boolean {
        atropos.core.thinking.Thinking.step("verify", "Verifying ZeroEntropyCertificate against repo root")
        val currentLeaves = certificate.leafManifest.map { leaf ->
            val file = repoRoot.resolve(leaf.relativePath).normalize()
            if (!Files.exists(file)) return false
            computeLeaf(repoRoot, file)
        }
        val reconstructedTree = buildMerkleTree(currentLeaves)
        val matched = reconstructedTree.rootHashHex == certificate.rootHash
        atropos.core.thinking.Thinking.detail("verify", "Certificate verification result: $matched (root=${certificate.rootHash.take(16)}...)")
        return matched
    }

    private fun compareByteArrays(a: ByteArray, b: ByteArray): Int {
        val len = minOf(a.size, b.size)
        for (i in 0 until len) {
            val cmp = (a[i].toInt() and 0xFF).compareTo(b[i].toInt() and 0xFF)
            if (cmp != 0) return cmp
        }
        return a.size.compareTo(b.size)
    }
}
