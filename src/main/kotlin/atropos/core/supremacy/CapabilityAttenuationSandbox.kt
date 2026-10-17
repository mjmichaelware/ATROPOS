package atropos.core.supremacy

import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant

data class AttenuatedToken(
    val tokenId: String,
    val parentTokenId: String?,
    val allowedOperations: Set<String>,
    val allowedPathPrefixes: List<String>,
    val maxExecutionSeconds: Long,
    val expiresAt: Instant,
    val hmacSignature: String
)

class CapabilityAttenuationSandbox(private val masterSecret: ByteArray) {
    private fun hmac(data: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(masterSecret)
        val hash = md.digest(data.toByteArray(StandardCharsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    fun createRootToken(operations: Set<String>, paths: List<String>, ttlSeconds: Long): AttenuatedToken {
        val id = "tok-root-${System.currentTimeMillis()}"
        val expires = Instant.now().plusSeconds(ttlSeconds)
        val sig = hmac("$id:ROOT:${operations.sorted().joinToString(",")}:${paths.sorted().joinToString(",")}:$expires")
        atropos.core.thinking.Thinking.step("sandbox", "Created root capability token: $id")
        atropos.core.thinking.Thinking.detail("sandbox", "Root token $id: ops=[${operations.joinToString()}], ttl=${ttlSeconds}s")
        return AttenuatedToken(id, null, operations, paths, ttlSeconds, expires, sig)
    }

    fun attenuateToken(
        parent: AttenuatedToken,
        operationsSubset: Set<String>,
        pathSubsets: List<String>,
        reducedTtlSeconds: Long
    ): AttenuatedToken {
        require(operationsSubset.all { it in parent.allowedOperations }) { "Cannot attenuate to an operation parent does not possess" }
        require(pathSubsets.all { childPath -> parent.allowedPathPrefixes.any { parentPath -> childPath.startsWith(parentPath) } }) {
            "Attenuated path must be sub-path of parent territory"
        }
        val childExpires = minOf(parent.expiresAt, Instant.now().plusSeconds(reducedTtlSeconds))
        val childId = "tok-sub-${System.currentTimeMillis()}"
        val sig = hmac("${parent.hmacSignature}:$childId:${operationsSubset.sorted().joinToString(",")}:${pathSubsets.sorted().joinToString(",")}:$childExpires")
        atropos.core.thinking.Thinking.step("sandbox", "Attenuated capability token to $childId")
        atropos.core.thinking.Thinking.detail("sandbox", "Token $childId derived from ${parent.tokenId}: ops=[${operationsSubset.joinToString()}]")
        return AttenuatedToken(childId, parent.tokenId, operationsSubset, pathSubsets, reducedTtlSeconds, childExpires, sig)
    }

    fun validateAction(token: AttenuatedToken, operation: String, targetPath: String): Boolean {
        if (Instant.now().isAfter(token.expiresAt)) return false
        if (operation !in token.allowedOperations) return false
        val normalizedTarget = targetPath.replace('\\', '/')
        val valid = token.allowedPathPrefixes.any { prefix -> normalizedTarget.startsWith(prefix) }
        atropos.core.thinking.Thinking.detail("sandbox", "Capability check for $operation on $targetPath: allowed=$valid")
        return valid
    }
}
