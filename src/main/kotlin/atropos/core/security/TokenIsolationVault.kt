package atropos.core.security

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission

data class TokenIsolationReport(
    val secretName: String,
    val path: Path,
    val isolated: Boolean,
    val findings: List<String>
)

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
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return null
        val report = inspectSecret(name)
        if (!report.isolated) return null
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
        val report = inspectSecret(name)
        require(report.isolated) { "secret is not isolated: ${report.findings.joinToString("; ")}" }
        return target
    }

    fun inspectSecret(name: String): TokenIsolationReport {
        val path = secretPath(name)
        val findings = isolationFindings(path)
        return TokenIsolationReport(
            secretName = sanitizeName(name),
            path = path,
            isolated = findings.isEmpty(),
            findings = findings
        )
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

    private fun isolationFindings(path: Path): List<String> {
        val base = rootPath()
        val normalized = path.toAbsolutePath().normalize()
        val findings = mutableListOf<String>()
        if (!normalized.startsWith(base)) {
            findings += "path escaped vault root"
        }
        if (Files.isSymbolicLink(normalized)) {
            findings += "secret path is a symbolic link"
        }
        if (!Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)) {
            findings += "secret path is not a regular file"
        }
        findings += broadPermissionFindings(normalized)
        return findings
    }

    private fun broadPermissionFindings(path: Path): List<String> {
        val permissions = runCatching { Files.getPosixFilePermissions(path) }.getOrNull() ?: return emptyList()
        val findings = mutableListOf<String>()
        if (PosixFilePermission.GROUP_READ in permissions || PosixFilePermission.OTHERS_READ in permissions) {
            findings += "secret file is readable outside owner"
        }
        if (PosixFilePermission.GROUP_WRITE in permissions || PosixFilePermission.OTHERS_WRITE in permissions) {
            findings += "secret file is writable outside owner"
        }
        if (PosixFilePermission.GROUP_EXECUTE in permissions || PosixFilePermission.OTHERS_EXECUTE in permissions ||
            PosixFilePermission.OWNER_EXECUTE in permissions
        ) {
            findings += "secret file has execute permission"
        }
        return findings
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
