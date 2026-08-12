package atropos.core.agent

import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Produces a deterministic root over the sanitized evidence and artifacts in a
 * self-host bundle. This is provenance metadata, not a promotion decision.
 */
data class SelfHostProvenanceArtifact(
    val path: String,
    val sha256: String
)

class SelfHostEvidenceProvenance {
    fun chainSha256(
        evidence: List<String>,
        artifacts: List<SelfHostProvenanceArtifact>,
        snapshotId: String?
    ): String {
        val canonical = buildString {
            append("snapshot=")
            append(snapshotId ?: "none")
            append('\n')
            evidence.forEachIndexed { index, value ->
                append("evidence[")
                append(index)
                append("]=")
                append(value)
                append('\n')
            }
            artifacts.sortedBy { it.path }.forEach { artifact ->
                append("artifact=")
                append(artifact.path)
                append(':')
                append(artifact.sha256)
                append('\n')
            }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
