package atropos.core.verification

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

internal class FactorySha256Hasher {
    fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    fun sha256File(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { input ->
            val buffer = ByteArray(8 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count > 0) digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun proposalSha256(project: Path, files: List<String>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        files.sorted().forEachIndexed { index, relative ->
            if (index > 0) digest.update('\n'.code.toByte())
            digest.update(relative.toByteArray(StandardCharsets.UTF_8))
            digest.update(0.toByte())
            Files.newInputStream(project.resolve(relative).normalize()).use { input ->
                val buffer = ByteArray(8 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (count > 0) digest.update(buffer, 0, count)
                }
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
