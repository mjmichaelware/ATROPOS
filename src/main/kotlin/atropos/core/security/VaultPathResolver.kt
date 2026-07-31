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
        ensureDirectoryTreeWithoutSymlinks(it)
    }

    private fun ensureDirectoryTreeWithoutSymlinks(root: Path) {
        var current = root.root ?: error("vault root has no filesystem root")
        for (index in 0 until root.nameCount) {
            current = current.resolve(root.getName(index))
            if (Files.exists(current, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                require(!Files.isSymbolicLink(current)) { "vault root contains a symbolic link" }
                require(Files.isDirectory(current, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                    "vault root component is not a directory"
                }
            } else {
                Files.createDirectory(current)
                require(!Files.isSymbolicLink(current)) { "vault root component became a symbolic link" }
            }
        }
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
