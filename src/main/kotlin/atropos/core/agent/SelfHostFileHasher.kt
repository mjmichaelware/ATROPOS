package atropos.core.agent

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

class SelfHostFileHasher {
    fun sha256(path: Path): String? {
        val normalized = path.toAbsolutePath().normalize()
        if (!Files.isRegularFile(normalized)) return null
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(normalized).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
