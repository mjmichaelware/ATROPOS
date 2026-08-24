/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core

import atropos.core.security.SecretSinkKind
import atropos.core.security.SecretSinkMatrix
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration

/**
 * Replacing the jar you are running, from the release the build published.
 *
 * The operator's install is a jar on a phone with no build toolchain. Every
 * update was a curl to a URL they had to remember, into a path they had to
 * find, followed by hashing both sides by hand to see whether it had worked --
 * and when it silently had not, the symptom was a fix that appeared not to
 * exist. That happened twice in one evening.
 *
 * ## What it refuses to do
 *
 * It never installs a jar it could not verify. The published checksum is
 * fetched first and the download is checked against it before anything is
 * moved; a missing checksum is a refusal, not a shrug, because "the server did
 * not offer one" is indistinguishable from "someone removed it".
 *
 * The previous jar is kept beside the new one. A replacement that boots into
 * something broken is a device with no working binary and no way to build one,
 * which is the one failure this must not be able to cause.
 */
class SelfUpdate(
    private val repository: String = DEFAULT_REPOSITORY,
    private val channel: String = DEFAULT_CHANNEL,
    private val download: (String) -> ByteArray = ::fetch,
    private val installedJar: () -> Path? = ::runningJar
) {

    sealed interface Outcome {
        /** Already current. Nothing was written. */
        data class UpToDate(val version: String, val sha256: String) : Outcome

        data class Installed(
            val from: String,
            val toSha256: String,
            val jar: Path,
            val backup: Path
        ) : Outcome

        data class Refused(val reason: String, val remedy: String) : Outcome
    }

    fun update(): Outcome {
        val jar = installedJar()
            ?: return Outcome.Refused(
                "cannot tell which jar is running",
                "point ATROPOS_JAR at the jar you want replaced, or download it by hand"
            )
        if (!Files.isWritable(jar)) {
            return Outcome.Refused(
                "$jar is not writable",
                "run the update as the user that owns the install"
            )
        }

        val expected = runCatching { String(download("$base/ATROPOS.jar.sha256")).trim() }
            .getOrElse {
                return Outcome.Refused(
                    "no published checksum for the $channel build",
                    "the release is incomplete; try again once its build finishes"
                )
            }
        if (!expected.matches(SHA256_PATTERN)) {
            return Outcome.Refused(
                "the published checksum is not a sha256: ${expected.take(32)}",
                "nothing was installed"
            )
        }

        val current = runCatching { sha256(Files.readAllBytes(jar)) }.getOrNull()
        if (current == expected) return Outcome.UpToDate(BuildStamp.version, expected)

        val bytes = runCatching { download("$base/ATROPOS.jar") }
            .getOrElse { return Outcome.Refused("download failed: ${it.message}", "nothing was installed") }

        val observed = sha256(bytes)
        if (observed != expected) {
            return Outcome.Refused(
                "checksum mismatch\n  expected $expected\n  observed $observed",
                "nothing was installed"
            )
        }

        // Written beside the target and moved into place, so a failure part way
        // through leaves the working jar untouched rather than half of a new one.
        val staging = jar.resolveSibling("${jar.fileName}.incoming")
        val backup = jar.resolveSibling("${jar.fileName}.previous")
        return runCatching {
            Files.write(staging, bytes)
            Files.copy(jar, backup, StandardCopyOption.REPLACE_EXISTING)
            Files.move(staging, jar, StandardCopyOption.REPLACE_EXISTING)
            Outcome.Installed(BuildStamp.version, expected, jar, backup)
        }.getOrElse {
            runCatching { Files.deleteIfExists(staging) }
            Outcome.Refused("could not replace $jar: ${it.message}", "the existing jar is untouched")
        }
    }

    private val base: String
        get() = "https://github.com/$repository/releases/download/$channel"

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        const val DEFAULT_REPOSITORY = "mjmichaelware/ATROPOS"

        /** The moving tag the release workflow rebuilds on every push to main. */
        const val DEFAULT_CHANNEL = "latest"

        private val SHA256_PATTERN = Regex("^[0-9a-f]{64}$")
    }
}

/** The jar this process is running from, or null when it is not running from one. */
private fun runningJar(): Path? {
    System.getenv("ATROPOS_JAR")?.takeIf(String::isNotBlank)?.let { return Paths.get(it) }
    val source = SelfUpdate::class.java.protectionDomain?.codeSource?.location ?: return null
    val path = runCatching { Paths.get(source.toURI()) }.getOrNull() ?: return null
    return path.takeIf { it.toString().endsWith(".jar") }
}

private fun fetch(url: String): ByteArray {
    require(URI.create(url).scheme.equals("https", ignoreCase = true)) {
        "self-update requires an HTTPS URL"
    }
    check(SecretSinkMatrix.isEgressPermitted(SecretSinkKind.EGRESS_URL)) {
        "self-update network egress is not permitted by SecretSinkMatrix"
    }
    val response = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .connectTimeout(Duration.ofSeconds(30))
        .build()
        .send(
            HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(5)).GET().build(),
            HttpResponse.BodyHandlers.ofInputStream()
        )
    require(response.statusCode() == 200) { "HTTP ${response.statusCode()} for $url" }
    return response.body().use { input ->
        input.readNBytes(MAX_DOWNLOAD_BYTES + 1).also {
            require(it.size <= MAX_DOWNLOAD_BYTES) {
                "self-update download exceeded $MAX_DOWNLOAD_BYTES bytes"
            }
        }
    }
}

private const val MAX_DOWNLOAD_BYTES = 128 * 1024 * 1024
