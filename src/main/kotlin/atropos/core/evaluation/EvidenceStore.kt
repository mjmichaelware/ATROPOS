/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.evaluation

import atropos.core.AtroposRepoRootLocator
import atropos.core.security.RedactionFilter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Content-addressed storage for the raw evidence a metric was computed from.
 *
 * Source Doc 3 §4.1: "Each metric must link to raw immutable evidence." §4.2:
 * "no unsupported manual percentages". Part C §7: "EvidenceStore persists raw
 * execution events, receipts, verifier findings, and metric snapshots with
 * cryptographic hashes."
 *
 * Content-addressed, so the link is the hash and the hash is the content. A
 * metric citing `sha256:ab12…` is checkable by anyone with the store, and a
 * metric whose cited evidence is not present is detectably unsupported rather
 * than plausibly supported. That is the whole mechanism behind anti-gaming: you
 * cannot move a number without either moving the evidence or breaking the link.
 *
 * Immutable by construction. Writing content that already exists is a no-op
 * returning the same hash, and there is no update or delete — a store whose
 * evidence could be revised after a metric cited it would let a failing run be
 * made to look passing without changing the metric at all.
 *
 * Redaction runs before the hash. Evidence quotes the things it is evidence
 * about, and one of those is eventually a credential; a secret hashed into
 * immutable storage is a secret that cannot be removed.
 */
class EvidenceStore(
    repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    private val root: Path = repoRoot.resolve(".atropos/evidence").normalize()
    private val memoryEntries = mutableListOf<EvidenceEntry>()

    fun store(entry: EvidenceEntry) {
        memoryEntries.add(entry)
    }

    fun getByMetric(metric: String): List<EvidenceEntry> {
        return memoryEntries.filter { it.metric == metric }
    }

    fun getAll(): List<EvidenceEntry> = memoryEntries.toList()


    /**
     * Stores [content] and returns its hash.
     *
     * @return the SHA-256 of the redacted bytes, which is the identifier a
     *   metric cites. Storing the same content twice yields the same hash and
     *   writes nothing the second time.
     */
    fun put(content: String, kind: EvidenceKind = EvidenceKind.RAW): String {
        val safe = redactionFilter.redact(content)
        val hash = sha256(safe)
        val target = pathFor(hash)
        if (Files.isRegularFile(target)) return hash

        Files.createDirectories(target.parent)
        // Written to a sibling and moved, so a reader never observes a
        // half-written object under a hash that promises complete content.
        val staging = target.resolveSibling(target.fileName.toString() + ".partial")
        Files.writeString(staging, safe, StandardCharsets.UTF_8)
        Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        writeKind(hash, kind)
        return hash
    }

    /** Stores several pieces at once, returning their hashes in order. */
    fun putAll(contents: List<String>, kind: EvidenceKind = EvidenceKind.RAW): List<String> =
        contents.map { put(it, kind) }

    /** The content behind a hash, or null when it is not held. */
    fun get(hash: String): String? {
        val target = pathFor(hash.trim().lowercase())
        if (!Files.isRegularFile(target)) return null
        return runCatching { Files.readString(target, StandardCharsets.UTF_8) }.getOrNull()
    }

    /** True when every cited hash is present and verifies against its content. */
    fun verify(hashes: List<String>): EvidenceVerification {
        val missing = mutableListOf<String>()
        val corrupt = mutableListOf<String>()
        hashes.forEach { cited ->
            val normalized = cited.trim().lowercase()
            val content = get(normalized)
            when {
                content == null -> missing += normalized
                sha256(content) != normalized -> corrupt += normalized
            }
        }
        return EvidenceVerification(
            cited = hashes.size,
            missing = missing,
            corrupt = corrupt
        )
    }

    /** True when [hash] is held. */
    fun has(hash: String): Boolean = Files.isRegularFile(pathFor(hash.trim().lowercase()))

    /** Number of stored objects, for a storage report. */
    fun count(): Int {
        if (!Files.isDirectory(root)) return 0
        return Files.walk(root).use { stream ->
            stream.filter { Files.isRegularFile(it) && !it.fileName.toString().endsWith(".partial") }
                .filter { !it.fileName.toString().endsWith(".kind") }
                .count().toInt()
        }
    }

    /**
     * Two-character fan-out on the hash prefix.
     *
     * A single directory of tens of thousands of files is slow to list on
     * phone-class storage, and evidence accumulates for the life of an install.
     */
    private fun pathFor(hash: String): Path =
        root.resolve(hash.take(2)).resolve(hash)

    private fun writeKind(hash: String, kind: EvidenceKind) {
        runCatching {
            Files.writeString(
                pathFor(hash).resolveSibling("$hash.kind"),
                kind.name,
                StandardCharsets.UTF_8
            )
        }
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}

/** What a piece of evidence is, for a reader deciding whether to open it. */
enum class EvidenceKind {
    RAW,
    EXECUTION_EVENT,
    VERIFIER_FINDING,
    TEST_RESULT,
    METRIC_SNAPSHOT,
    RECEIPT
}

/**
 * Whether cited evidence is actually there.
 *
 * [corrupt] is separate from [missing] because they mean different things: a
 * missing hash is a citation to something never stored, while a corrupt one is
 * content that changed after it was cited — which is tampering, not an omission.
 */
data class EvidenceVerification(
    val cited: Int,
    val missing: List<String>,
    val corrupt: List<String>
) {
    val intact: Boolean get() = missing.isEmpty() && corrupt.isEmpty()

    fun render(): String = when {
        intact -> "$cited evidence object(s) verified"
        corrupt.isNotEmpty() -> "${corrupt.size} corrupt, ${missing.size} missing of $cited cited"
        else -> "${missing.size} missing of $cited cited"
    }
}

data class EvidenceEntry(
    val id: String,
    val metric: String,
    val hash: String,
    val timestamp: Long
)
