/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.ingest

import java.nio.file.Files
import java.nio.file.Path

/**
 * Resolving an `@path` mention into an ingestible file.
 *
 * `SUP.ART.AT-MENTION-UPLOAD`: "File ingestion is territory-bounded,
 * size-bounded, and attested; no arbitrary file read. Competitors allow broader
 * or unattested uploads."
 *
 * Every refusal below closes a way the operator's own convenience becomes an
 * exfiltration path. `@../../.ssh/id_rsa` is a plausible typo and a complete
 * attack; the resolver treats both the same way because it cannot tell them
 * apart and must not try.
 */
class MentionResolver(
    private val territoryRoots: List<Path>,
    private val maxBytes: Long = 8L * 1024 * 1024,
    private val allowedExtensions: Set<String> = DEFAULT_EXTENSIONS,
    /**
     * Whether the resolved path is a readable regular file.
     *
     * Injected so the resolver stays testable without a filesystem, and named
     * as a predicate rather than read from a size, because size cannot answer
     * it: a missing file reports `-1`, which is under every ceiling, so a
     * mistyped name inside a granted territory resolved clean and attached
     * nothing. The operator was told "attached" for a file that was not there.
     */
    private val isReadableFile: (Path) -> Boolean = { path ->
        Files.isRegularFile(path) && Files.isReadable(path)
    },
    /**
     * What the boundary is, for the one refusal that is about the boundary.
     *
     * "resolves outside every granted territory" told an operator that they
     * had hit a wall and nothing about where the wall was, so the next thing
     * they tried was another guess. Naming the roots turns a refusal into an
     * instruction.
     */
    private val describeTerritory: () -> String = {
        territoryRoots.joinToString("\n") { "  $it" }
    }
) {
    fun resolve(mention: String, sizeBytes: Long): MentionResolution {
        val raw = mention.removePrefix("@").trim()
        if (raw.isEmpty()) {
            return MentionResolution.Refused("an empty mention names no file", "@<path>")
        }

        val candidate = try {
            Path.of(raw).normalize()
        } catch (_: Exception) {
            return MentionResolution.Refused("'$raw' is not a usable path", "check the path and retry")
        }

        val extension = candidate.fileName?.toString()?.substringAfterLast('.', "")?.lowercase().orEmpty()
        if (extension !in allowedExtensions) {
            return MentionResolution.Refused(
                "'$extension' files are not ingestible",
                "allowed: ${allowedExtensions.sorted().joinToString(", ")}"
            )
        }

        if (sizeBytes > maxBytes) {
            return MentionResolution.Refused(
                "$raw is $sizeBytes bytes, over the $maxBytes byte ingest ceiling",
                "trim the file or raise the ceiling deliberately"
            )
        }

        // Territory last, so a refusal names the most specific reason rather
        // than always blaming the grant.
        val resolved = territoryRoots.asSequence()
            .map { root -> root.resolve(candidate).normalize() to root.normalize() }
            .firstOrNull { (target, root) -> target.startsWith(root) }
            ?: return MentionResolution.Refused(
                "$raw resolves outside every granted territory",
                "granted:\n" + describeTerritory() +
                    "\nadd one with ATROPOS_INGEST_ROOTS=<path> or a line in .atropos/ingest-roots"
            )

        // Existence last, and only inside territory. A path that failed the
        // grant is never stat-ed, so the refusal cannot be used to probe for
        // files the operator was not allowed to name in the first place.
        if (!isReadableFile(resolved.first)) {
            return MentionResolution.Refused(
                "$raw is not a readable file",
                "check the spelling and that the file exists"
            )
        }

        return MentionResolution.Resolved(resolved.first, extension)
    }

    private companion object {
        val DEFAULT_EXTENSIONS = setOf("txt", "md", "docx", "pdf", "png", "jpg", "jpeg")
    }
}

sealed class MentionResolution {
    data class Resolved(val path: Path, val extension: String) : MentionResolution()
    data class Refused(val reason: String, val remedy: String) : MentionResolution()

    val ingestible: Boolean get() = this is Resolved
}
