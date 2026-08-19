/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core

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

    /** One line, for `--version` and for anywhere a build has to identify itself. */
    fun line(): String = "ATROPOS $version ($commit)"

    const val UNKNOWN = "unknown"
    private const val RESOURCE = "atropos-build.properties"
}
