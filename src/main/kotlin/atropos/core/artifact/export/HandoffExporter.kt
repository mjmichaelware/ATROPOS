/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.artifact.export

import atropos.core.security.RedactionFilter
import atropos.core.storage.FreeSpaceDecision
import atropos.core.storage.StorageSupervisor
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Writes an artifact out of the system.
 *
 * `SUP.ART.HANDOFF-EXPORT`: "Artifacts leave the system only through an
 * explicit, territory-bounded, redacted channel. Competitors dump files with
 * unclear provenance."
 *
 * Four gates, in this order, and the order is the design:
 *
 * 1. **Landing** is resolved and territory-checked by
 *    [ArtifactLandingResolver]. A refusal here costs nothing — no content has
 *    been gathered yet, so the operator learns the destination is wrong before
 *    anything is rendered.
 * 2. **Content** is produced by the supplier the caller passed in. The exporter
 *    never reaches into a subsystem to find it; a writer that knew how to build
 *    an evidence summary would have to change every time evidence did.
 * 3. **Redaction** runs over the rendered text, unconditionally. This is the
 *    last point before bytes leave the process, and an export is exactly the
 *    kind of file that ends up in a chat window.
 * 4. **Space** is checked last, against the real size of what will be written,
 *    because until the content exists the size is a guess.
 */
class HandoffExporter(
    private val resolver: ArtifactLandingResolver,
    private val redactionFilter: RedactionFilter = RedactionFilter(),
    private val storage: StorageSupervisor? = null,
    private val clock: () -> Instant = { Instant.now() }
) {
    /**
     * @param grantedTerritory the paths this export is permitted to write to.
     *   Passed in rather than looked up, so the caller's authority is the one
     *   that applies rather than whatever the exporter could find.
     * @param content produces the artifact body. Called only once the landing
     *   zone is known to be permitted.
     */
    fun export(
        type: HandoffType,
        landing: ArtifactLanding,
        grantedTerritory: List<Path>,
        content: (HandoffType) -> String
    ): ExportResult {
        val resolved = when (val resolution = resolver.resolve(landing, grantedTerritory)) {
            is LandingResolution.Refused ->
                return ExportResult.Refused(resolution.reason, resolution.remedy)

            is LandingResolution.Resolved -> resolution
        }

        val body = redactionFilter.redact(render(type, content(type)))
        val bytes = body.toByteArray(StandardCharsets.UTF_8)

        storage?.let { supervisor ->
            val decision = supervisor.admit(bytes.size.toLong())
            if (decision is FreeSpaceDecision.Refused) {
                return ExportResult.Refused(
                    decision.reason,
                    "Free space with '/storage gc --apply', or export somewhere else."
                )
            }
        }

        val target = resolved.directory.resolve(filename(type))
        return runCatching {
            Files.createDirectories(resolved.directory)
            Files.write(target, bytes)
            ExportResult.Written(target, resolved.zone, bytes.size.toLong())
        }.getOrElse { failure ->
            ExportResult.Refused(
                "Writing $target failed (${failure.javaClass.simpleName}).",
                "Check that ${resolved.directory} exists and is writable."
            )
        }
    }

    /**
     * The filename. Stem from the type, timestamp from the clock, nothing from
     * the operator — so the destination directory is the only thing a caller
     * chooses, and it has already been territory-checked.
     */
    private fun filename(type: HandoffType): String =
        "${type.filenameStem}-${STAMP.format(clock().atOffset(ZoneOffset.UTC))}.md"

    /**
     * Wraps the body in provenance.
     *
     * The atom's complaint about competitors is "unclear provenance", so an
     * exported file states what it is and when it was produced. Without that a
     * handoff read a week later is indistinguishable from a draft.
     */
    private fun render(type: HandoffType, body: String): String = buildString {
        appendLine("# ATROPOS ${type.canonical}")
        appendLine()
        appendLine("_${type.description}_")
        appendLine()
        appendLine("Exported ${clock()}. Secrets are redacted at export.")
        appendLine()
        appendLine("---")
        appendLine()
        append(body.trimEnd())
        appendLine()
    }

    private companion object {
        val STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
    }
}

sealed class ExportResult {
    data class Written(val path: Path, val zone: String, val bytes: Long) : ExportResult() {
        fun render(): String = "wrote ${bytes}B to $path ($zone)"
    }

    data class Refused(val reason: String, val remedy: String) : ExportResult()

    val ok: Boolean get() = this is Written
}
