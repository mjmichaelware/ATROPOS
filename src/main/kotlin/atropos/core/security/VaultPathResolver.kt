package atropos.core.security

import java.nio.file.Files
import java.nio.file.Path

/** Resolves secret paths without allowing names to escape the vault root. */
class VaultPathResolver(
    private val configuredRoot: Path
) {
    fun rootPath(): Path = configuredRoot.toAbsolutePath().normalize()

    fun secretPath(name: String): Path {
        val safeName = sanitizeName(name)
        val root = rootPath()
        val candidate = root.resolve("$safeName.secret").normalize()
        require(candidate.parent == root) { "secret path escaped vault root" }
        return candidate
    }

    fun ensureRoot(): Path = rootPath().also {
        require(!Files.isSymbolicLink(it)) { "vault root is a symbolic link" }
        Files.createDirectories(it)
        require(!Files.isSymbolicLink(it)) { "vault root became a symbolic link" }
    }

    private fun sanitizeName(name: String): String {
        val trimmed = name.trim()
        require(trimmed.isNotBlank()) { "secret name must not be blank" }
        val safe = trimmed.replace(INVALID_NAME_CHARACTERS, "_")
        require(safe.isNotBlank()) { "secret name resolved to blank" }
        require(safe != "." && safe != "..") { "secret name is not allowed" }
        return safe
    }

    private companion object {
        val INVALID_NAME_CHARACTERS = Regex("[^A-Za-z0-9_.-]")
    }
}
