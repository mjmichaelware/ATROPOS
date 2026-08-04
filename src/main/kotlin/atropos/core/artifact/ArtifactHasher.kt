package atropos.core.artifact

import atropos.core.platform.PlatformAbstraction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

object ArtifactHasher {
    fun sha256File(platform: PlatformAbstraction, filePath: String): String {
        return try {
            val bytes = platform.readFile(filePath).getOrNull()?.toByteArray(StandardCharsets.UTF_8) ?: return ""
            val digest = MessageDigest.getInstance("SHA-256")
            digest.digest(bytes).joinToString("") { "%02x".format(it) }
        } catch (_: Exception) { "" }
    }

    fun sha256Bytes(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
