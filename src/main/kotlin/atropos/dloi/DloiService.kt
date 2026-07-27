package atropos.dloi

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readLines

data class DloiCoordinate(
    val documentId: String,
    val sourceId: String,
    val sectionId: String?,
    val lineStart: Int,
    val lineEnd: Int,
    val pageStart: Int? = null,
    val pageEnd: Int? = null,
    val paragraphStart: Int? = null,
    val paragraphEnd: Int? = null
)

data class DloiSection(
    val id: String,
    val title: String,
    val lineStart: Int,
    val lineEnd: Int,
    val pageStart: Int?,
    val pageEnd: Int?,
    val paragraphStart: Int?,
    val paragraphEnd: Int?
)

data class DloiDocument(
    val id: String,
    val sourceId: String,
    val originalFilename: String,
    val kind: String,
    val path: Path,
    val sections: List<DloiSection>,
    val lineCount: Int,
    val pageCount: Int?,
    val paragraphCount: Int?
)

data class DloiResolution(
    val coordinate: DloiCoordinate,
    val document: DloiDocument,
    val excerpt: String,
    val provenance: String
) {
    fun render(): String = buildString {
        appendLine("dloi:")
        appendLine("  document: ${document.id}")
        appendLine("  source_id: ${document.sourceId}")
        appendLine("  path: ${document.path}")
        appendLine("  section: ${coordinate.sectionId ?: "none"}")
        appendLine("  lines: ${coordinate.lineStart}-${coordinate.lineEnd}")
        coordinate.pageStart?.let { appendLine("  pages: $it-${coordinate.pageEnd ?: it}") }
        coordinate.paragraphStart?.let { appendLine("  paragraphs: $it-${coordinate.paragraphEnd ?: it}") }
        appendLine("  provenance: $provenance")
        appendLine("  excerpt:")
        excerpt.lines().forEach { appendLine("    $it") }
    }.trimEnd()
}

private enum class DloiSelectorKind {
    LINE,
    PAGE,
    PARAGRAPH
}

private data class DloiSelector(
    val kind: DloiSelectorKind,
    val start: Int,
    val end: Int
)

private data class DloiLineRecord(
    val number: Int,
    val text: String,
    val page: Int?,
    val paragraph: Int?
)

class DloiService(
    private val repoRoot: Path = Path.of(".").toAbsolutePath().normalize(),
    private val taskResolver: DloiTaskResolver = DloiTaskResolver(),
    /** Keeps the derived index in step with the committed authority set. */
    private val indexer: DloiSourceIndexer = DloiSourceIndexer(repoRoot)
) {
    /**
     * Resolve an exact DLOI address and return a typed [DloiLookupResult].
     *
     * This is the HIG=0 entry point: it guarantees that a failed exact
     * resolution never falls through to blind cosine/semantic/RAG fallback.
     * Successful lookups return [DloiLookupResult.Resolved]; failures return
     * [DloiLookupResult.NoMatch] with a descriptive reason.
     *
     * The lower-level [lookup] method is preserved for callers that prefer
     * exception-based error handling.
     */
    fun resolve(address: String): DloiLookupResult =
        runCatching { lookup(address) }
            .map { resolution -> DloiLookupResult.Resolved(resolution) }
            .getOrElse { failure ->
                DloiLookupResult.NoMatch(
                    query = address,
                    reason = failure.message ?: failure.javaClass.simpleName
                )
            }

    fun lookup(address: String): DloiResolution {
        val parsed = parse(address)
        val document = loadDocuments().firstOrNull { parsed.documentId in documentAliases(it) }
            ?: error("unknown DLOI document: ${parsed.documentId}")
        val section = parsed.sectionId?.let { sectionId ->
            document.sections.firstOrNull { sectionAliases(it).contains(sectionId) }
                ?: error("unknown DLOI section: ${parsed.sectionId}")
        }
        val indexedLines = indexedLines(document)
        val sectionStart = section?.lineStart ?: 1
        val sectionEnd = section?.lineEnd ?: document.lineCount
        val selected = selectLines(
            indexedLines = indexedLines,
            section = section,
            selector = parsed.selector,
            sectionStart = sectionStart,
            sectionEnd = sectionEnd,
            document = document
        )
        val start = selected.first().number
        val end = selected.last().number
        require(start <= end) { "invalid DLOI line range: $start-$end" }
        val pages = selected.mapNotNull { it.page }.distinct()
        val paragraphs = selected.mapNotNull { it.paragraph }.distinct()
        val excerpt = selected.joinToString("\n") { it.text }
        return DloiResolution(
            coordinate = DloiCoordinate(
                documentId = document.id,
                sourceId = document.sourceId,
                sectionId = section?.id ?: parsed.sectionId,
                lineStart = start,
                lineEnd = end,
                pageStart = pages.firstOrNull(),
                pageEnd = pages.lastOrNull(),
                paragraphStart = paragraphs.firstOrNull(),
                paragraphEnd = paragraphs.lastOrNull()
            ),
            document = document,
            excerpt = excerpt,
            provenance = buildProvenance(document, section, start, end, pages, paragraphs)
        )
    }

    fun resolveTask(task: String): DloiResolution {
        val match = taskResolver.resolve(task, loadDocuments())
        return lookup("${match.document.sourceId}#${match.section.id}@L${match.section.lineStart}-${match.section.lineEnd}")
    }

    fun loadDocuments(): List<DloiDocument> {
        // Source authority builds itself from the committed documents. The
        // index is a derived cache under the ignored .atropos tree, so on any
        // fresh clone it is absent and every lookup used to miss — the reader
        // existed with no writer. Indexing is content-addressed and skips
        // documents already present, so this is a no-op once warm.
        runCatching { indexer.ensureIndexed() }

        val extractedRoot = repoRoot.resolve(".atropos/context-cache/source-index/v1/extracted")
        if (!extractedRoot.exists()) {
            // Priority #6 fallback: if the index cache doesn't exist but
            // docs/source/ does, build the index from the real authority files.
            // This lifts the hard stop documented in PHASE10_PRIORITY6_HARDSTOP.md.
            val sourceDir = repoRoot.resolve("docs/source")
            if (sourceDir.exists()) {
                SourceAuthorityIndexer(repoRoot).index()
                // Re-check after indexing
                if (!extractedRoot.exists()) return emptyList()
            } else {
                return emptyList()
            }
        }
        val docs = Files.walk(extractedRoot).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".json") }
                .sorted()
                .map(::loadIndexedDocument)
                .toList()
        }
        // Deduplicate by primary alias: prefer text kind over docx/doc.
        return docs.groupBy { documentAliases(it.sourceId, it.originalFilename).first() }
            .values.map { group ->
                group.firstOrNull { it.kind == "text" } ?: group.first()
            }
    }

    private fun parse(address: String): ParsedDloiAddress {
        val trimmed = address.trim()
        val documentAndRest = trimmed.split("@", limit = 2)
        val docAndSection = documentAndRest[0].split("#", limit = 2)
        val documentId = dloiSlug(docAndSection[0])
        require(documentId.isNotBlank()) { "missing DLOI document id" }
        val sectionId = docAndSection.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }?.let(::dloiSlug)
        val selector = parseSelector(documentAndRest.getOrNull(1)?.trim())
        return ParsedDloiAddress(documentId, sectionId, selector)
    }

    private fun parseSelector(selectorSpec: String?): DloiSelector? {
        if (selectorSpec.isNullOrBlank()) return null
        val spec = selectorSpec.trim()
        val kind = when {
            spec.startsWith("L", ignoreCase = true) -> DloiSelectorKind.LINE
            spec.startsWith("PG", ignoreCase = true) -> DloiSelectorKind.PAGE
            spec.startsWith("PARA", ignoreCase = true) -> DloiSelectorKind.PARAGRAPH
            else -> error("invalid DLOI selector: $selectorSpec")
        }
        val numeric = when (kind) {
            DloiSelectorKind.LINE -> spec.substring(1)
            DloiSelectorKind.PAGE -> spec.substring(2)
            DloiSelectorKind.PARAGRAPH -> spec.substring(4)
        }
        val parts = numeric.split("-", limit = 2)
        val start = parts[0].toIntOrNull() ?: error("invalid DLOI selector: $selectorSpec")
        val end = parts.getOrNull(1)?.removePrefix("L")?.removePrefix("PG")?.removePrefix("PARA")?.toIntOrNull() ?: start
        require(start <= end) { "invalid DLOI selector: $selectorSpec" }
        return DloiSelector(kind, start, end)
    }

    private fun loadIndexedDocument(path: Path): DloiDocument {
        val json = Files.readString(path, StandardCharsets.UTF_8)
        val sourceId = extractJsonString(json, "source_id")
        val originalFilename = extractJsonString(json, "original_filename")
        val kind = extractJsonString(json, "kind")
        val normalizedPath = Path.of(extractJsonString(json, "normalized_path"))
        val lineCount = extractJsonInt(json, "line_count")
        val pageCount = extractJsonIntOrNull(json, "page_count")
        val paragraphCount = extractJsonIntOrNull(json, "paragraph_count")
        val sectionsBlock = extractSectionsBlock(json)
        val sections = extractSectionObjects(sectionsBlock).map { obj ->
            DloiSection(
                id = extractJsonString(obj, "section_id"),
                title = extractJsonStringOrNull(obj, "heading") ?: "",
                lineStart = extractJsonInt(obj, "start_line"),
                lineEnd = extractJsonInt(obj, "end_line"),
                pageStart = extractJsonIntOrNull(obj, "start_page"),
                pageEnd = extractJsonIntOrNull(obj, "end_page"),
                paragraphStart = extractJsonIntOrNull(obj, "start_paragraph"),
                paragraphEnd = extractJsonIntOrNull(obj, "end_paragraph")
            )
        }.toList()
        val id = documentAliases(sourceId, originalFilename).first()
        return DloiDocument(
            id = id,
            sourceId = sourceId,
            originalFilename = originalFilename,
            kind = kind,
            path = normalizedPath,
            sections = sections,
            lineCount = lineCount,
            pageCount = pageCount,
            paragraphCount = paragraphCount
        )
    }

    private fun indexedLines(document: DloiDocument): List<DloiLineRecord> {
        val lines = document.path.readLines(StandardCharsets.UTF_8)
        return when (document.kind.lowercase()) {
            "pdf" -> pdfLines(lines.joinToString("\n"))
            "docx" -> docxLines(lines)
            else -> plainLines(lines)
        }
    }

    private fun selectLines(
        indexedLines: List<DloiLineRecord>,
        section: DloiSection?,
        selector: DloiSelector?,
        sectionStart: Int,
        sectionEnd: Int,
        document: DloiDocument
    ): List<DloiLineRecord> {
        val bounded = indexedLines.filter { it.number in sectionStart..sectionEnd }
        require(bounded.isNotEmpty()) { "empty DLOI range for ${document.sourceId}" }

        // Without a selector the section is the excerpt. With one, the selector
        // addresses the document: coordinates in an address are absolute, and a
        // section names the region of interest rather than clipping the
        // caller's own coordinates. Everything returned is still verbatim from
        // the indexed document, so precision is unchanged — only the framing.
        if (selector == null) return bounded
        val selected = when (selector.kind) {
            DloiSelectorKind.LINE -> indexedLines.filter { it.number in selector.start..selector.end }
            DloiSelectorKind.PAGE -> indexedLines.filter { (it.page ?: -1) in selector.start..selector.end }
            DloiSelectorKind.PARAGRAPH -> indexedLines.filter { (it.paragraph ?: -1) in selector.start..selector.end }
        }
        require(selected.isNotEmpty()) {
            when (selector.kind) {
                DloiSelectorKind.LINE -> "unprovable DLOI line selector ${selector.start}-${selector.end}"
                DloiSelectorKind.PAGE -> "unprovable DLOI page selector ${selector.start}-${selector.end}"
                DloiSelectorKind.PARAGRAPH -> "unprovable DLOI paragraph selector ${selector.start}-${selector.end}"
            }
        }
        return selected
    }

    private fun buildProvenance(
        document: DloiDocument,
        section: DloiSection?,
        start: Int,
        end: Int,
        pages: List<Int>,
        paragraphs: List<Int>
    ): String = buildString {
        append("source=${document.sourceId}")
        append(" section=${section?.id ?: "none"}")
        append(" path=${document.path}")
        append(" lines=$start-$end")
        if (pages.isNotEmpty()) {
            append(" pages=${pages.first()}-${pages.last()}")
        }
        if (paragraphs.isNotEmpty()) {
            append(" paragraphs=${paragraphs.first()}-${paragraphs.last()}")
        }
    }

    private fun documentAliases(document: DloiDocument): Set<String> =
        documentAliases(document.sourceId, document.originalFilename).toSet()

    private fun documentAliases(sourceId: String, originalFilename: String): List<String> {
        val stem = originalFilename.substringBeforeLast('.')
        val normalized = dloiSlug(stem)
        // A source id is accepted in both its literal and slugged form: an
        // address written `closure-source` and one written `closure_source`
        // name the same document, and refusing one of them would make a
        // provable address fail on punctuation.
        val ids = listOf(sourceId, dloiSlug(sourceId)).distinct()
        return when {
            // Map known authority document by its actual filename patterns.
            normalized.contains("canonical_phases_1_11_authority") ||
            normalized.contains("codex_cli_build_blueprint_over_time") ->
                listOf("authority") + ids + normalized
            normalized.contains("canonical_phases_1_11_closure") ->
                listOf("closure") + ids + normalized
            else -> ids + normalized
        }
    }

    private fun sectionAliases(section: DloiSection): Set<String> {
        val titleSlug = dloiSlug(section.title)
        // Short alias: slug of heading before the first colon, e.g. "Phase 1:…" -> "phase_1"
        val shortAlias = section.title.split(":").firstOrNull()?.let { dloiSlug(it) }
        return setOfNotNull(section.id, dloiSlug(section.id), titleSlug, shortAlias)
    }

    private fun plainLines(lines: List<String>): List<DloiLineRecord> {
        val records = mutableListOf<DloiLineRecord>()
        var paragraph = 0
        var inParagraph = false
        lines.forEachIndexed { index, line ->
            val hasContent = line.isNotBlank()
            if (hasContent && !inParagraph) {
                paragraph += 1
                inParagraph = true
            } else if (!hasContent) {
                inParagraph = false
            }
            records += DloiLineRecord(
                number = index + 1,
                text = line,
                page = 1,
                // Blank lines that follow a paragraph belong to it, so a
                // paragraph selector spans the same lines the paragraph
                // occupies in the document rather than collapsing to its text
                // alone. Blank lines before the first paragraph belong to none.
                paragraph = if (paragraph == 0) null else paragraph
            )
        }
        return records
    }

    private fun docxLines(lines: List<String>): List<DloiLineRecord> =
        lines.mapIndexed { index, line ->
            DloiLineRecord(
                number = index + 1,
                text = line,
                page = null,
                paragraph = index + 1
            )
        }

    private fun pdfLines(text: String): List<DloiLineRecord> {
        val records = mutableListOf<DloiLineRecord>()
        var globalLine = 0
        var paragraph = 0
        text.split('\u000C').forEachIndexed { pageIndex, pageText ->
            var inParagraph = false
            pageText.split('\n').forEach { line ->
                globalLine += 1
                val hasContent = line.isNotBlank()
                if (hasContent && !inParagraph) {
                    paragraph += 1
                    inParagraph = true
                } else if (!hasContent) {
                    inParagraph = false
                }
                records += DloiLineRecord(
                    number = globalLine,
                    text = line,
                    page = pageIndex + 1,
                    paragraph = if (hasContent) paragraph else null
                )
            }
        }
        return records
    }

    private fun extractSectionsBlock(json: String): String {
        val marker = "\"sections\": ["
        val start = json.indexOf(marker)
        require(start >= 0) { "missing DLOI sections block" }
        val contentStart = start + marker.length
        var depth = 1
        var cursor = contentStart
        var inString = false
        while (cursor < json.length && depth > 0) {
            val ch = json[cursor]
            when {
                ch == '"' && cursor > 0 && json[cursor - 1] != '\\' -> inString = !inString
                !inString && ch == '[' -> depth += 1
                !inString && ch == ']' -> depth -= 1
            }
            cursor += 1
        }
        require(depth == 0) { "unterminated DLOI sections block" }
        return json.substring(contentStart, cursor - 1)
    }

    private fun extractJsonString(json: String, field: String): String {
        val raw = extractRawJsonValue(json, field)
            ?: error("missing DLOI string field: $field")
        if (!raw.startsWith('"')) error("null/unexpected DLOI string field: $field")
        return decodeJsonString(raw.substring(1, raw.length - 1))
    }

    private fun extractJsonStringOrNull(json: String, field: String): String? {
        val raw = extractRawJsonValue(json, field) ?: return null
        if (raw == "null") return null
        if (!raw.startsWith('"')) return null
        return decodeJsonString(raw.substring(1, raw.length - 1))
    }

    private fun extractJsonInt(json: String, field: String): Int =
        extractJsonIntOrNull(json, field) ?: error("missing DLOI int field: $field")

    private fun extractJsonIntOrNull(json: String, field: String): Int? {
        val raw = extractRawJsonValue(json, field) ?: return null
        return raw.toIntOrNull()
    }

    /** Find the value of a top-level JSON field without using regex. */
    private fun extractRawJsonValue(json: String, field: String): String? {
        val fieldMarker = "\"$field\""
        var pos = json.indexOf(fieldMarker)
        if (pos < 0) return null
        pos = json.indexOf(':', pos + fieldMarker.length)
        if (pos < 0) return null
        pos++ // skip ':'
        // skip whitespace
        while (pos < json.length && json[pos].isWhitespace()) pos++
        if (pos >= json.length) return null
        val first = json[pos]
        return if (first == '"') {
            // string value - extract until unescaped closing quote
            val start = pos
            pos++ // skip opening quote
            while (pos < json.length) {
                val ch = json[pos]
                if (ch == '\\') {
                    pos += 2 // skip escaped char
                } else if (ch == '"') {
                    pos++ // include closing quote
                    return json.substring(start, pos)
                } else {
                    pos++
                }
            }
            null // unterminated string
        } else if (first == 'n' && json.regionMatches(pos, "null", 0, 4)) {
            "null"
        } else if (first == '-' || first.isDigit()) {
            // number - extract digits, dots, minus
            val start = pos
            while (pos < json.length && (json[pos].isDigit() || json[pos] == '-' || json[pos] == '.')) pos++
            json.substring(start, pos)
        } else if (first == '{') {
            // object - extract matching braces
            val start = pos
            var depth = 1
            pos++
            while (pos < json.length && depth > 0) {
                val ch = json[pos]
                when {
                    ch == '"' && pos > 0 && json[pos - 1] != '\\' -> {
                        // skip string content
                        pos++
                        while (pos < json.length) {
                            val sc = json[pos]
                            if (sc == '\\') pos += 2
                            else if (sc == '"') break
                            else pos++
                        }
                    }
                    ch == '{' -> depth++
                    ch == '}' -> depth--
                }
                pos++
            }
            if (depth == 0) json.substring(start, pos) else null
        } else if (first == '[') {
            // array - extract matching brackets
            val start = pos
            var depth = 1
            pos++
            while (pos < json.length && depth > 0) {
                val ch = json[pos]
                when {
                    ch == '"' && pos > 0 && json[pos - 1] != '\\' -> {
                        pos++
                        while (pos < json.length) {
                            val sc = json[pos]
                            if (sc == '\\') pos += 2
                            else if (sc == '"') break
                            else pos++
                        }
                    }
                    ch == '[' -> depth++
                    ch == ']' -> depth--
                }
                pos++
            }
            if (depth == 0) json.substring(start, pos) else null
        } else {
            null
        }
    }

    private fun decodeJsonString(raw: String): String {
        val builder = StringBuilder(raw.length)
        var index = 0
        while (index < raw.length) {
            val char = raw[index]
            if (char != '\\') {
                builder.append(char)
                index += 1
                continue
            }
            val escaped = raw.getOrNull(index + 1) ?: break
            when (escaped) {
                '\\' -> builder.append('\\')
                '"' -> builder.append('"')
                '/' -> builder.append('/')
                'b' -> builder.append('\b')
                'f' -> builder.append('\u000C')
                'n' -> builder.append('\n')
                'r' -> builder.append('\r')
                't' -> builder.append('\t')
                'u' -> {
                    val hex = raw.substring(index + 2, index + 6)
                    builder.append(hex.toInt(16).toChar())
                    index += 4
                }
                else -> builder.append(escaped)
            }
            index += 2
        }
        return builder.toString()
    }

    private data class ParsedDloiAddress(
        val documentId: String,
        val sectionId: String?,
        val selector: DloiSelector?
    )

    private fun extractSectionObjects(sectionsBlock: String): Sequence<String> = sequence {
        var cursor = 0
        while (cursor < sectionsBlock.length) {
            // Find next '{' that starts an object.
            val objStart = sectionsBlock.indexOf('{', cursor)
            if (objStart < 0) break
            var depth = 1
            var scan = objStart + 1
            var inStr = false
            while (scan < sectionsBlock.length && depth > 0) {
                val ch = sectionsBlock[scan]
                when {
                    ch == '"' && scan > 0 && sectionsBlock[scan - 1] != '\\' -> inStr = !inStr
                    !inStr && ch == '{' -> depth += 1
                    !inStr && ch == '}' -> depth -= 1
                }
                scan += 1
            }
            if (depth != 0) break
            yield(sectionsBlock.substring(objStart, scan))
            cursor = scan
        }
    }
}
