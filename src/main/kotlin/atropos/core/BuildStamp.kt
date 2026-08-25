/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * What this binary is, read from the jar it was built into.
 *
 * An operator running from a phone cannot rebuild — every install comes from a
 * release asset — so "is the binary I am running the one I just pulled?" was
 * answerable only by hashing the jar against a published URL. When it was not
 * the same, the symptom was a fix that appeared not to work, and an evening
 * spent looking for the bug in the wrong place.
 *
 * Absent rather than guessed when the stamp is missing: a build with no
 * provenance should say so, not report the version the source tree happens to
 * declare, which is exactly the number that would be wrong in the case this
 * exists to catch.
 */
object BuildStamp {

    private val properties: Map<String, String> by lazy {
        val stream = BuildStamp::class.java.classLoader
            .getResourceAsStream(RESOURCE) ?: return@lazy emptyMap()
        stream.use { input ->
            input.bufferedReader().readLines()
                .mapNotNull { line ->
                    val parts = line.split('=', limit = 2)
                    if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
                }
                .toMap()
        }
    }

    val version: String get() = properties["version"] ?: UNKNOWN
    val commit: String get() = properties["commit"] ?: UNKNOWN

    /** SHA-256 of the running release jar, or unknown while running from classes. */
    val artifactSha256: String
        get() {
            val location = BuildStamp::class.java.protectionDomain?.codeSource?.location ?: return UNKNOWN
            val path = runCatching { Path.of(location.toURI()) }.getOrNull() ?: return UNKNOWN
            if (!Files.isRegularFile(path) || !path.fileName.toString().endsWith(".jar")) return UNKNOWN
            return runCatching {
                val digest = MessageDigest.getInstance("SHA-256")
                Files.newInputStream(path).use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count > 0) digest.update(buffer, 0, count)
                    }
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }.getOrElse { UNKNOWN }
        }

    /** One line, for `--version` and for anywhere a build has to identify itself. */
    fun line(): String = "ATROPOS $version ($commit) sha256=$artifactSha256"

    const val UNKNOWN = "unknown"
    private const val RESOURCE = "atropos-build.properties"
}
