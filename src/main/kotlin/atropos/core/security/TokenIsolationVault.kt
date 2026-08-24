package atropos.core.security

import atropos.core.AtroposConfig
import java.io.File
import java.nio.channels.FileChannel
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
    private val root: Path = AtroposConfig.configRoot().resolve("secrets"),
    /**
     * Device-first by default, operator key still wins.
     *
     * This used to default to [EnvironmentSecretVaultKeyProvider] alone, which meant
     * a fresh install refused every vault operation until a human exported a
     * base64 AES-256 key by hand. [DeviceSecretVaultKeyProvider] keeps that override
     * working and provisions a device key when there is none, so the vault is usable
     * by anyone who simply downloads ATROPOS.
     */
    private val keyProvider: SecretVaultKeyProvider = DeviceSecretVaultKeyProvider(root)
) {
    private val pathResolver = VaultPathResolver(root)

    fun rootPath(): Path {
        val normalized = pathResolver.ensureRoot()
        restrictDirectory(normalized)
        return normalized
    }

    fun readSecret(name: String): String? {
        return (readSecretResult(name) as? VaultReadResult.Available)?.value
    }

    /**
     * Sink-aware read boundary. Plaintext is never returned to a prohibited
     * destination; callers must opt into a sink already admitted by the
     * canonical [SecretSinkMatrix].
     */
    fun readSecretForSink(name: String, sink: SecretSinkKind): VaultReadResult {
        val path = pathResolver.secretPath(name)
        if (!SecretSinkMatrix.isEgressPermitted(sink)) {
            return VaultReadResult.Refused(path, VaultReadRefusalReason.PROHIBITED_SINK)
        }
        return readSecretResult(name)
    }

    fun readSecretResult(name: String): VaultReadResult {
        val path = pathResolver.secretPath(name)
        try {
            rootPath()
        } catch (_: Exception) {
            return VaultReadResult.Refused(path, VaultReadRefusalReason.OUTSIDE_VAULT_ROOT)
        }
        return try {
            if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                return VaultReadResult.Refused(path, VaultReadRefusalReason.MISSING)
            }
            if (Files.isSymbolicLink(path)) {
                return VaultReadResult.Refused(path, VaultReadRefusalReason.SYMBOLIC_LINK)
            }
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                return VaultReadResult.Refused(path, VaultReadRefusalReason.NOT_REGULAR_FILE)
            }
            if (broadPermissionFindings(path).isNotEmpty()) {
                return VaultReadResult.Refused(path, VaultReadRefusalReason.NOT_ISOLATED)
            }
            val key = when (val result = keyProvider.load()) {
                is SecretVaultKeyResult.Available -> result.key
                is SecretVaultKeyResult.Refused -> return VaultReadResult.Refused(path, VaultReadRefusalReason.KEY_UNAVAILABLE)
            }
            val payload = Files.readAllBytes(path)
            val cipher = VaultCipher(key)
            if (!cipher.hasSupportedEnvelope(payload)) {
                return VaultReadResult.Refused(path, VaultReadRefusalReason.UNSUPPORTED_FORMAT)
            }
            val plainText = cipher.decrypt(
                payload,
                VaultCipher.associatedData(path.fileName.toString())
            ) ?: return VaultReadResult.Refused(path, VaultReadRefusalReason.TAMPERED)
            val value = plainText.toString(StandardCharsets.UTF_8)
            if (value.isEmpty()) {
                VaultReadResult.Refused(path, VaultReadRefusalReason.INVALID_CIPHERTEXT)
            } else {
                VaultReadResult.Available(path, value)
            }
        } catch (_: Exception) {
            VaultReadResult.Refused(path, VaultReadRefusalReason.IO_FAILURE)
        }
    }

    fun writeSecret(name: String, value: String): Path {
        require(value.isNotBlank()) { "secret value must not be blank" }
        rootPath()
        val target = pathResolver.secretPath(name)
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw IllegalArgumentException("target path exists and is not a regular file")
        }
        val parent = target.parent ?: rootPath()
        if (Files.exists(parent, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
            throw IllegalArgumentException("parent path exists and is not a directory")
        }
        Files.createDirectories(parent)
        restrictDirectory(parent)

        val key = when (val result = keyProvider.load()) {
            is SecretVaultKeyResult.Available -> result.key
            is SecretVaultKeyResult.Refused -> error("vault key unavailable: ${result.reason}")
        }

        val permissions = setOf(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE
        )
        val tmp = if (parent.fileSystem.supportedFileAttributeViews().contains("posix")) {
            Files.createTempFile(
                parent,
                "${target.fileName}-",
                ".tmp",
                java.nio.file.attribute.PosixFilePermissions.asFileAttribute(permissions)
            )
        } else {
            parent.resolve("${target.fileName}.tmp-${System.nanoTime()}")
        }

        val encrypted = VaultCipher(key).encrypt(
            value.toByteArray(StandardCharsets.UTF_8),
            VaultCipher.associatedData(target.fileName.toString())
        )
        try {
            Files.write(tmp, encrypted)
            restrictFile(tmp)
            FileChannel.open(tmp, java.nio.file.StandardOpenOption.WRITE).use { channel -> channel.force(true) }
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Exception) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            runCatching { Files.deleteIfExists(tmp) }
        }
        restrictFile(target)
        val report = inspectSecret(name)
        require(report.isolated) { "secret is not isolated: ${report.findings.joinToString("; ")}" }
        return target
    }

    fun inspectSecret(name: String): TokenIsolationReport {
        val path = pathResolver.secretPath(name)
        val findings = runCatching { isolationFindings(path) }
            .getOrElse { listOf("vault root unavailable") }
        return TokenIsolationReport(
            secretName = path.fileName.toString().removeSuffix(".secret"),
            path = path,
            isolated = findings.isEmpty(),
            findings = findings
        )
    }

    fun secretFile(name: String): File = pathResolver.secretPath(name).toFile()

    fun listSecrets(): List<String> = runCatching {
        val root = rootPath()
        if (!Files.isDirectory(root)) return emptyList()
        Files.walk(root).use { stream ->
            stream.filter { Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) }
                .map { it.fileName.toString().removeSuffix(".secret") }
                .toList()
        }
    }.getOrElse { emptyList() }

    fun deleteSecret(name: String): Boolean = runCatching {
        val path = pathResolver.secretPath(name)
        if (!Files.exists(path)) return false
        Files.deleteIfExists(path)
    }.getOrDefault(false)

    fun vaultHealth(): Map<String, Any> = runCatching {
        val root = rootPath()
        val exists = Files.exists(root)
        val isDir = Files.isDirectory(root)
        val secretCount = if (isDir) listSecrets().size else 0
        val readable = runCatching { readSecret("_health_") != null }.getOrDefault(false)
        mapOf<String, Any>(
            "vaultExists" to exists,
            "isDirectory" to isDir,
            "secretCount" to secretCount,
            "readable" to readable,
            "healthy" to (exists && isDir && readable)
        )
    }.getOrElse { mapOf<String, Any>("healthy" to false, "error" to "vault inspection failed") }

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
        if (Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS) && !isEncryptedPayload(normalized)) {
            findings += "secret file is not encrypted at rest"
        }
        findings += broadPermissionFindings(normalized)
        return findings
    }

    private fun isEncryptedPayload(path: Path): Boolean = runCatching {
        val key = (keyProvider.load() as? SecretVaultKeyResult.Available)?.key ?: return false
        VaultCipher(key).decrypt(
            Files.readAllBytes(path),
            VaultCipher.associatedData(path.fileName.toString())
        ) != null
    }.getOrDefault(false)

    private fun broadPermissionFindings(path: Path): List<String> {
        val permissions = runCatching { Files.getPosixFilePermissions(path) }.getOrNull()
            ?: return listOf("owner permissions unavailable")
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
