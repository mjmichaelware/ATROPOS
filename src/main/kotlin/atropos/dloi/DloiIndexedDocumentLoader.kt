package atropos.dloi

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

class DloiIndexedDocumentLoader(
    private val aliases: DloiAliasResolver = DloiAliasResolver(),
    private val json: DloiJsonReader = DloiJsonReader()
) {
    fun load(path: Path): DloiDocument {
        val body = Files.readString(path, StandardCharsets.UTF_8)
        val sourceId = json.string(body, "source_id")
        val originalFilename = json.string(body, "original_filename")
        val kind = json.string(body, "kind")
        val normalizedPath = Path.of(json.string(body, "normalized_path"))
        val lineCount = json.int(body, "line_count")
        val pageCount = json.intOrNull(body, "page_count")
        val paragraphCount = json.intOrNull(body, "paragraph_count")
        val sections = json.sectionObjects(json.sectionsBlock(body)).map { obj ->
            DloiSection(
                id = json.string(obj, "section_id"),
                title = json.stringOrNull(obj, "heading") ?: "",
                lineStart = json.int(obj, "start_line"),
                lineEnd = json.int(obj, "end_line"),
                pageStart = json.intOrNull(obj, "start_page"),
                pageEnd = json.intOrNull(obj, "end_page"),
                paragraphStart = json.intOrNull(obj, "start_paragraph"),
                paragraphEnd = json.intOrNull(obj, "end_paragraph")
            )
        }.toList()
        return DloiDocument(
            id = aliases.documentAliases(sourceId, originalFilename).first(),
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
}
