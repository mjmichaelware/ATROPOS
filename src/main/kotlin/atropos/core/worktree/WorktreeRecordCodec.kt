package atropos.core.worktree

import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.time.Instant
import java.util.Base64

/**
 * Reads and writes the `key=value` form of a [WorktreeRecord].
 *
 * The format is deliberately dumb — one field per line, split at the first `=`
 * — so a meta file stays readable with `cat` when something has gone wrong and
 * the tooling is what is under suspicion.
 *
 * ## Two encoding decisions the format depends on
 *
 * **Split at the first `=` only.** Values contain `=`: base64 padding ends in
 * it, and paths can carry it. Splitting on every occurrence would truncate
 * exactly the fields that matter most.
 *
 * **Dirty evidence is base64.** It holds `git status` output, which is
 * multi-line. Written raw, its second line would parse as a new field and
 * everything after it would be silently lost — including, at merge time, the
 * evidence that the tree was dirty to begin with.
 *
 * Every read is total: a truncated or corrupt file yields null rather than
 * throwing, because a damaged meta file must not take down a listing that is
 * probably being run to diagnose that very damage.
 */
class WorktreeRecordCodec {

    /** Serialises to the on-disk form, trailing newline included. */
    fun encode(record: WorktreeRecord): String = buildString {
        appendLine("id=${record.id}")
        appendLine("jobId=${record.jobId}")
        appendLine("worktreePath=${record.worktreePath}")
        appendLine("baselineCommit=${record.baselineCommit ?: ""}")
        appendLine("territory=${record.territory.joinToString(TERRITORY_SEPARATOR)}")
        appendLine("dirtyEvidenceB64=${encodeBase64(record.dirtyEvidence.orEmpty())}")
        appendLine("appliedPatches=${record.appliedPatches.joinToString(PATCH_SEPARATOR)}")
        appendLine("verified=${record.verified}")
        appendLine("rolledBack=${record.rolledBack}")
        appendLine("mergedBack=${record.mergedBack}")
        appendLine("createdAt=${record.createdAt}")
        appendLine("updatedAt=${record.updatedAt}")
    }

    /**
     * Parses the on-disk form.
     *
     * @param metaFile stored on the record so a caller that listed a directory
     *   knows which file each record came from without re-deriving the path.
     * @return null when the content cannot be parsed at all.
     */
    fun decode(content: List<String>, metaFile: Path): WorktreeRecord? {
        val fields = content.mapNotNull { line ->
            val index = line.indexOf('=')
            if (index <= 0) null else line.substring(0, index) to line.substring(index + 1)
        }.toMap()

        return runCatching {
            WorktreeRecord(
                id = fields["id"].orEmpty(),
                jobId = fields["jobId"].orEmpty(),
                worktreePath = Path.of(fields["worktreePath"].orEmpty()),
                baselineCommit = fields["baselineCommit"]?.takeIf { it.isNotBlank() },
                territory = splitList(fields["territory"], TERRITORY_SEPARATOR),
                dirtyEvidence = decodeBase64(fields["dirtyEvidenceB64"]).takeIf { it.isNotBlank() },
                appliedPatches = splitList(fields["appliedPatches"], PATCH_SEPARATOR),
                // A field that is absent or unparseable reads as false. The
                // conservative direction: an unreadable `verified` must never
                // present itself as a verification that happened.
                verified = fields["verified"]?.toBooleanStrictOrNull() ?: false,
                rolledBack = fields["rolledBack"]?.toBooleanStrictOrNull() ?: false,
                mergedBack = fields["mergedBack"]?.toBooleanStrictOrNull() ?: false,
                createdAt = parseInstant(fields["createdAt"]) ?: Instant.EPOCH,
                updatedAt = parseInstant(fields["updatedAt"]) ?: Instant.EPOCH,
                metaFile = metaFile
            )
        }.getOrNull()
    }

    private fun splitList(value: String?, separator: String): List<String> =
        value?.split(separator)?.filter { it.isNotBlank() } ?: emptyList()

    private fun parseInstant(value: String?): Instant? =
        value?.takeIf { it.isNotBlank() }?.let { runCatching { Instant.parse(it) }.getOrNull() }

    private fun encodeBase64(text: String): String =
        Base64.getEncoder().encodeToString(text.toByteArray(StandardCharsets.UTF_8))

    private fun decodeBase64(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return runCatching {
            String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8)
        }.getOrDefault("")
    }

    private companion object {
        const val TERRITORY_SEPARATOR = ","

        /**
         * Patches join on `|` rather than `,` because a stored patch excerpt can
         * itself contain commas.
         */
        const val PATCH_SEPARATOR = "|"
    }
}
