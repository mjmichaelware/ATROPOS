/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.dloi

import atropos.data.storage.CloudLakehouseSyncEngine
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.MessageDigest

/** What one `ensureIndexed` pass did. */
data class DloiIndexResult(
    val indexed: List<String>,
    val alreadyCurrent: List<String>,
    val pruned: List<String>
) {
    val changed: Boolean get() = indexed.isNotEmpty() || pruned.isNotEmpty()
}

/**
 * Builds the source index that [DloiService] reads.
 *
 * The index was a read-only contract: `loadDocuments()` walked
 * `.atropos/context-cache/source-index/v1/extracted` and returned nothing when
 * it was absent, and nothing in the tree ever wrote it. Exact source authority
 * therefore resolved nothing at all.
 *
 * Entries are **content-addressed**: the identity of a document is the SHA-256
 * of its bytes, and `source_id` is the first 16 hex characters of that digest.
 * Nothing here invents an identity — change a byte and the document becomes a
 * different document, which is what makes an address provable.
 */
class DloiSourceIndexer(
    private val repoRoot: Path = Path.of(".").toAbsolutePath().normalize(),
    /**
     * The authority set. Only documents committed here are addressable — an
     * index built from anything else would be asserting authority the
     * repository never granted.
     */
    private val sourceDirectory: Path = repoRoot.resolve("docs/source"),
    /** Optional repository authority manifest for hash and path validation. */
    private val authorityManifest: Path = repoRoot.resolve("docs/authority/AUTHORITY_MANIFEST.tsv"),
    /**
     * Seeds the canonical CAS while indexing authority bytes. CAS is an
     * optional acceleration/replication layer; DLOI indexing remains usable
     * when its local storage cannot be initialized.
     */
    private val casSync: CloudLakehouseSyncEngine? = runCatching {
        CloudLakehouseSyncEngine(repoRoot.resolve(".atropos/cas").toFile())
    }.getOrNull()
) {
    private val indexRoot = repoRoot.toAbsolutePath().normalize()
        .resolve(".atropos/context-cache/source-index/v1")

    /**
     * Brings the index in line with the authority set.
     *
     * Missing documents are indexed; entries whose digest no longer corresponds
     * to a present document are pruned, so a superseded revision cannot keep
     * answering lookups beside the document that replaced it.
     */
    fun ensureIndexed(): DloiIndexResult {
        if (!Files.isDirectory(sourceDirectory, LinkOption.NOFOLLOW_LINKS)) {
            return DloiIndexResult(emptyList(), emptyList(), emptyList())
        }
        val manifestedSources = runCatching { readAuthorityManifest() }
            .getOrElse { return DloiIndexResult(emptyList(), emptyList(), emptyList()) }
        if (!prepareIndexRoot()) {
            return DloiIndexResult(emptyList(), emptyList(), emptyList())
        }

        val indexed = mutableListOf<String>()
        val current = mutableListOf<String>()
        val liveDigests = mutableSetOf<String>()

        sourceDocuments(manifestedSources).forEach { file ->
            val bytes = Files.readAllBytes(file)
            val digest = sha256(bytes)
            liveDigests += digest
            val casResult = seedCas(bytes)

            val extracted = extractedPath(digest)
            if (isCurrentEntry(file, bytes, digest, casResult)) {
                current += file.fileName.toString()
                return@forEach
            }
            writeEntry(file, bytes, digest, casResult)
            indexed += file.fileName.toString()
        }

        return DloiIndexResult(indexed, current, prune(liveDigests))
    }

    private fun sourceDocuments(manifestedSources: Set<Path>?): List<Path> =
        Files.list(sourceDirectory).use { stream ->
            stream.filter {
                Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) &&
                    (manifestedSources == null ||
                        it.toAbsolutePath().normalize() in manifestedSources ||
                        it.fileName.toString() in LEGACY_AUTHORITY_FILENAMES) &&
                    it.fileName.toString().substringAfterLast('.', "").lowercase() in TEXT_EXTENSIONS
            }
                .sorted()
                .toList()
        }

    /**
     * Validate the optional authority manifest before any source bytes become
     * addressable. A present manifest is authoritative: malformed rows,
     * traversal, symlinks, size drift, or hash drift refuse the whole index
     * pass. Repositories without the optional manifest retain the historical
     * docs/source discovery behavior.
     */
    private fun readAuthorityManifest(): Set<Path>? {
        if (!Files.exists(authorityManifest, LinkOption.NOFOLLOW_LINKS)) return null
        if (!Files.isRegularFile(authorityManifest, LinkOption.NOFOLLOW_LINKS) ||
            hasSymbolicComponent(authorityManifest)
        ) error("authority manifest is not a regular non-symbolic file")

        val rows = Files.readAllLines(authorityManifest, StandardCharsets.UTF_8)
            .filter { it.isNotBlank() }
            .dropWhile { it.trimStart().startsWith("path |") }
        val paths = linkedSetOf<Path>()
        rows.forEach { row ->
            val fields = row.split(" | ")
            if (fields.size < 6) error("authority manifest row is malformed")
            val relative = fields[0].trim()
            val expectedHash = fields[1].trim().lowercase()
            val expectedBytes = fields[2].trim().toLongOrNull()
                ?: error("authority manifest byte count is invalid")
            if (relative.isBlank() || Path.of(relative).isAbsolute() ||
                relative.replace('\\', '/').split('/').any { it == ".." }
            ) error("authority manifest path is not repository-relative: $relative")
            val file = repoRoot.resolve(relative).normalize()
            if (!file.startsWith(repoRoot) || hasSymbolicComponent(file) ||
                !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
            ) error("authority manifest path is not a safe regular file: $relative")
            if (!paths.add(file)) error("authority manifest contains duplicate path: $relative")
            if (Files.size(file) != expectedBytes || sha256(Files.readAllBytes(file)) != expectedHash) {
                error("authority manifest hash or size mismatch: $relative")
            }
        }
        return paths
    }

    private fun writeEntry(file: Path, bytes: ByteArray, digest: String, casResult: Result<String>?) {
        // The byte-order mark is stripped from the content but the line
        // structure is untouched: an address is a line coordinate, so
        // renumbering lines here would silently move every existing address.
        val text = normalizedText(bytes)
        val lines = text.split("\n")
        val sections = DloiSectionExtractor.extract(lines)
        val casHash = casResult?.getOrNull()
        val casStatus = when {
            casResult == null -> "SKIPPED_SOFT_FAIL:cas_unavailable"
            casResult.isSuccess -> "STORED"
            else -> "SKIPPED_SOFT_FAIL:${casResult.exceptionOrNull()?.javaClass?.simpleName ?: "cas_write_failed"}"
        }

        val normalized = normalizedPath(digest)
        requireSafeIndexPath(normalized)
        Files.createDirectories(normalized.parent)
        Files.writeString(normalized, text, StandardCharsets.UTF_8)

        val sourceId = digest.take(SOURCE_ID_LENGTH)
        val extracted = extractedPath(digest)
        requireSafeIndexPath(extracted)
        Files.createDirectories(extracted.parent)
        Files.writeString(
            extracted,
            renderJson(
                sourceId = sourceId,
                originalFilename = "${sourceId}__${file.fileName}",
                normalizedPath = normalized,
                casHash = casHash,
                casStatus = casStatus,
                lineCount = lines.size,
                pageCount = text.count { it == '' } + 1,
                paragraphCount = sections.maxOfOrNull { it.endParagraph } ?: 0,
                sections = sections
            ),
            StandardCharsets.UTF_8
        )
    }

    /**
     * An extracted entry is a cache, not authority. Reuse it only when both
     * the normalized source bytes and the complete derived metadata still
     * match the current source document. CAS fields are deliberately ignored
     * because CAS is an optional acceleration layer and may be unavailable on
     * a later pass.
     */
    private fun isCurrentEntry(
        file: Path,
        bytes: ByteArray,
        digest: String,
        casResult: Result<String>?
    ): Boolean = runCatching {
        val normalized = normalizedPath(digest)
        val extracted = extractedPath(digest)
        requireSafeIndexPath(normalized)
        requireSafeIndexPath(extracted)
        if (!Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS) ||
            !Files.isRegularFile(extracted, LinkOption.NOFOLLOW_LINKS)
        ) return@runCatching false

        val text = normalizedText(bytes)
        if (Files.readString(normalized, StandardCharsets.UTF_8) != text) return@runCatching false

        val lines = text.split("\n")
        val expected = renderJson(
            sourceId = digest.take(SOURCE_ID_LENGTH),
            originalFilename = "${digest.take(SOURCE_ID_LENGTH)}__${file.fileName}",
            normalizedPath = normalized,
            casHash = casResult?.getOrNull(),
            casStatus = "IGNORED_FOR_CACHE_VALIDATION",
            lineCount = lines.size,
            pageCount = text.count { it == '' } + 1,
            paragraphCount = DloiSectionExtractor.extract(lines).maxOfOrNull { it.endParagraph } ?: 0,
            sections = DloiSectionExtractor.extract(lines)
        )
        val observed = Files.readString(extracted, StandardCharsets.UTF_8)
        stripOptionalCasFields(observed) == stripOptionalCasFields(expected)
    }.getOrDefault(false)

    private fun normalizedText(bytes: ByteArray): String =
        String(bytes, StandardCharsets.UTF_8).removePrefix("﻿").replace("\r\n", "\n")

    private fun stripOptionalCasFields(json: String): String = json.lineSequence()
        .filterNot { line ->
            line.trimStart().startsWith("\"cas_hash\":") ||
                line.trimStart().startsWith("\"cas_status\":")
        }
        .joinToString("\n")

    /** Removes entries whose document is no longer part of the authority set. */
    private fun prune(liveDigests: Set<String>): List<String> {
        val extractedRoot = indexRoot.resolve("extracted")
        if (!Files.isDirectory(extractedRoot)) return emptyList()

        val removed = mutableListOf<String>()
        Files.walk(extractedRoot).use { stream ->
            stream.filter {
                Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) &&
                    it.fileName.toString().endsWith(".v1.json")
            }.toList()
        }.forEach { entry ->
            val digest = entry.fileName.toString().removeSuffix(".v1.json")
            if (digest !in liveDigests) {
                Files.deleteIfExists(entry)
                Files.deleteIfExists(normalizedPath(digest))
                removed += digest.take(SOURCE_ID_LENGTH)
            }
        }
        return removed
    }

    private fun normalizedPath(digest: String): Path =
        indexRoot.resolve("normalized/${digest.take(2)}/$digest.v1.txt")

    private fun extractedPath(digest: String): Path =
        indexRoot.resolve("extracted/${digest.take(2)}/$digest.v1.json")

    private fun prepareIndexRoot(): Boolean = runCatching {
        if (hasSymbolicComponent(indexRoot)) return false
        Files.createDirectories(indexRoot)
        !hasSymbolicComponent(indexRoot) && indexRoot.toRealPath() == indexRoot
    }.getOrDefault(false)

    private fun requireSafeIndexPath(path: Path) {
        val normalized = path.toAbsolutePath().normalize()
        require(normalized.startsWith(indexRoot)) { "DLOI index path escaped its derived root" }
        require(!hasSymbolicComponent(normalized.parent ?: normalized)) {
            "DLOI index path contains a symbolic component"
        }
    }

    private fun hasSymbolicComponent(path: Path): Boolean {
        var current: Path? = path.toAbsolutePath().normalize()
        while (current != null) {
            if (Files.isSymbolicLink(current)) return true
            current = current.parent
        }
        return false
    }

    private fun seedCas(bytes: ByteArray): Result<String>? =
        casSync?.let { sync -> runCatching { sync.storeContentAddressed(bytes) } }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun renderJson(
        sourceId: String,
        originalFilename: String,
        normalizedPath: Path,
        casHash: String?,
        casStatus: String,
        lineCount: Int,
        pageCount: Int,
        paragraphCount: Int,
        sections: List<DloiIndexedSection>
    ): String = buildString {
        val renderedCasHash = casHash?.let { "\"${escape(it)}\"" } ?: "null"
        appendLine("{")
        appendLine("""  "source_id": "$sourceId",""")
        appendLine("""  "original_filename": "${escape(originalFilename)}",""")
        appendLine("""  "kind": "text",""")
        appendLine("""  "normalized_path": "${escape(normalizedPath.toAbsolutePath().normalize().toString())}",""")
        appendLine("""  "cas_hash": $renderedCasHash,""")
        appendLine("""  "cas_status": "${escape(casStatus)}",""")
        appendLine("""  "line_count": $lineCount,""")
        appendLine("""  "page_count": $pageCount,""")
        appendLine("""  "paragraph_count": $paragraphCount,""")
        appendLine("""  "sections": [""")
        sections.forEachIndexed { index, section ->
            appendLine("    {")
            appendLine("""      "section_id": "${section.id}",""")
            appendLine("""      "heading": "${escape(section.heading)}",""")
            appendLine("""      "start_line": ${section.startLine},""")
            appendLine("""      "end_line": ${section.endLine},""")
            appendLine("""      "start_page": 1,""")
            appendLine("""      "end_page": $pageCount,""")
            appendLine("""      "start_paragraph": ${section.startParagraph},""")
            appendLine("""      "end_paragraph": ${section.endParagraph}""")
            append("    }")
            if (index != sections.lastIndex) append(',')
            appendLine()
        }
        appendLine("  ]")
        appendLine("}")
    }

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\t", " ")

    private companion object {
        val TEXT_EXTENSIONS = setOf("txt", "md")
        const val SOURCE_ID_LENGTH = 16
        /** Preserve the pre-manifest authority alias and its stable coordinates. */
        val LEGACY_AUTHORITY_FILENAMES = setOf(
            "ATROPOS_CODEX_CLI_BUILD_BLUEPRINT_OVER_TIME.txt"
        )
    }
}
