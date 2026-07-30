package atropos.dloi

import atropos.core.AtroposRepoRootLocator
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

class DloiService(
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val taskResolver: DloiTaskResolver = DloiTaskResolver(),
    /** Keeps the derived index in step with the committed authority set. */
    private val indexer: DloiSourceIndexer = DloiSourceIndexer(repoRoot),
    private val aliases: DloiAliasResolver = DloiAliasResolver(),
    private val indexedDocumentLoader: DloiIndexedDocumentLoader = DloiIndexedDocumentLoader(aliases),
    private val addressParser: DloiAddressParser = DloiAddressParser(),
    private val lineIndexer: DloiLineIndexer = DloiLineIndexer()
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
        val parsed = addressParser.parse(address)
        val document = loadDocuments().firstOrNull { parsed.documentId in aliases.documentAliases(it) }
            ?: error("unknown DLOI document: ${parsed.documentId}")
        val section = parsed.sectionId?.let { sectionId ->
            document.sections.firstOrNull { aliases.sectionAliases(it).contains(sectionId) }
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
        if (!extractedRoot.exists()) return emptyList()
        val docs = Files.walk(extractedRoot).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".json") }
                .sorted()
                .map(indexedDocumentLoader::load)
                .toList()
        }
        // Deduplicate by primary alias: prefer text kind over docx/doc.
        return docs.groupBy { aliases.documentAliases(it.sourceId, it.originalFilename).first() }
            .values.map { group ->
                group.firstOrNull { it.kind == "text" } ?: group.first()
            }
    }

    private fun indexedLines(document: DloiDocument): List<DloiLineRecord> {
        val lines = document.path.readLines(StandardCharsets.UTF_8)
        return lineIndexer.index(document, lines)
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

}
