package atropos.core.security

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission

class TokenIsolationVault(
    private val root: Path = Path.of(".atropos/secrets")
) {
    fun rootPath(): Path {
        val normalized = root.toAbsolutePath().normalize()
        Files.createDirectories(normalized)
        restrictDirectory(normalized)
        return normalized
    }

    fun readSecret(name: String): String? {
        val path = secretPath(name)
        if (!Files.isRegularFile(path)) return null
        return Files.readString(path, StandardCharsets.UTF_8)
            .trim()
            .takeIf { it.isNotBlank() }
    }

    fun writeSecret(name: String, value: String): Path {
        require(value.isNotBlank()) { "secret value must not be blank" }
        val target = secretPath(name)
        val parent = target.parent ?: rootPath()
        Files.createDirectories(parent)
        restrictDirectory(parent)

        val tmp = parent.resolve("${target.fileName}.tmp-${System.nanoTime()}")
        Files.writeString(tmp, value.trim() + "\n", StandardCharsets.UTF_8)
        restrictFile(tmp)
        try {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
        }
        restrictFile(target)
        return target
    }

    fun secretFile(name: String): File = secretPath(name).toFile()

    private fun secretPath(name: String): Path {
        val safe = sanitizeName(name)
        val base = rootPath()
        val candidate = base.resolve("$safe.secret").normalize()
        require(candidate.parent == base) { "secret path escaped vault root" }
        return candidate
    }

    private fun sanitizeName(name: String): String {
        val trimmed = name.trim()
        require(trimmed.isNotBlank()) { "secret name must not be blank" }
        val safe = trimmed.replace(Regex("[^A-Za-z0-9_.-]"), "_")
        require(safe.isNotBlank()) { "secret name resolved to blank" }
        require(safe != "." && safe != "..") { "secret name is not allowed" }
        return safe
    }

    private fun restrictDirectory(path: Path) {
        trySetPosix(path, setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE))
        path.toFile().apply {
            setReadable(false, false)
            setWritable(false, false)
            setExecutable(false, false)
            setReadable(true, true)
            setWritable(true, true)
            setExecutable(true, true)
        }
    }

    private fun restrictFile(path: Path) {
        trySetPosix(path, setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE))
        path.toFile().apply {
            setReadable(false, false)
            setWritable(false, false)
            setExecutable(false, false)
            setReadable(true, true)
            setWritable(true, true)
        }
    }

    private fun trySetPosix(path: Path, permissions: Set<PosixFilePermission>) {
        runCatching { Files.setPosixFilePermissions(path, permissions) }
    }
}
