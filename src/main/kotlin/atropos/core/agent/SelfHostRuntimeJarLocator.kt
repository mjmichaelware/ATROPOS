package atropos.core.agent

import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

data class SelfHostJarPaths(
    val candidateJar: Path,
    val targetJar: Path
)

data class SelfHostJarPathResult(
    val ok: Boolean,
    val message: String,
    val paths: SelfHostJarPaths? = null
)

class SelfHostRuntimeJarLocator(
    private val repoRoot: Path,
    private val env: Map<String, String> = System.getenv()
) {
    fun resolve(): SelfHostJarPathResult {
        val candidate = configuredPath("ATROPOS_SELF_HOST_CANDIDATE_JAR", "atropos.selfHost.candidateJar")
            ?.takeIf { it.isNotBlank() }
            ?.let { Path.of(it) }
            ?: defaultCandidateJar()
        val target = configuredPath("ATROPOS_INSTALLED_JAR", "atropos.installed.jar")
            ?.takeIf { it.isNotBlank() }
            ?.let { Path.of(it) }
            ?: activeRuntimeJar()

        val normalizedCandidate = normalize(candidate)
        if (!Files.isRegularFile(normalizedCandidate)) {
            return SelfHostJarPathResult(false, "candidate jar unavailable: $normalizedCandidate")
        }
        val normalizedTarget = target?.let(::normalize)
            ?: return SelfHostJarPathResult(false, "installed runtime jar unavailable; set ATROPOS_INSTALLED_JAR")
        if (!Files.isRegularFile(normalizedTarget)) {
            return SelfHostJarPathResult(false, "installed runtime jar missing: $normalizedTarget")
        }
        return SelfHostJarPathResult(true, "jar paths resolved", SelfHostJarPaths(normalizedCandidate, normalizedTarget))
    }

    private fun activeRuntimeJar(): Path? {
        val uri = runCatching {
            SelfHostRuntimeJarLocator::class.java.protectionDomain.codeSource.location.toURI()
        }.getOrNull() ?: return null
        val path = runCatching { Path.of(uri) }.getOrNull() ?: uri.filePath()
        return path?.takeIf { it.fileName.toString().endsWith(".jar", ignoreCase = true) }
    }

    private fun URI.filePath(): Path? =
        runCatching { Path.of(path) }.getOrNull()

    private fun normalize(path: Path): Path =
        if (path.isAbsolute) path.normalize() else repoRoot.resolve(path).normalize()

    private fun configuredPath(envKey: String, propertyKey: String): String? =
        env[envKey]?.takeIf { it.isNotBlank() }
            ?: System.getProperty(propertyKey)?.takeIf { it.isNotBlank() }

    private fun defaultCandidateJar(): Path {
        val canonical = repoRoot.resolve("build/libs/ATROPOS.jar")
        if (Files.isRegularFile(canonical)) return canonical
        val libs = repoRoot.resolve("build/libs")
        if (!Files.isDirectory(libs)) return canonical
        return Files.list(libs).use { stream ->
            stream
                .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".jar", ignoreCase = true) }
                .toList()
                .maxByOrNull { Files.getLastModifiedTime(it).toMillis() }
                ?: canonical
        }
    }
}
