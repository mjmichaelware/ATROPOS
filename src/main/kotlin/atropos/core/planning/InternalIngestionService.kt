package atropos.core.planning

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

class InternalIngestionService {
    fun ingest(projectId: String, source: Path): IngestedDocument {
        val content = Files.readString(source)
        return ingestText(projectId, source.toString(), content)
    }

    fun ingestText(projectId: String, sourcePath: String, content: String): IngestedDocument {
        val normalized = content.replace("\r\n", "\n")
        val lines = normalized.lines()
        val sections = mutableListOf<IngestedSection>()
        var currentHeading = "Document"
        var currentStart = 1
        val currentLines = mutableListOf<String>()

        fun flush(endLine: Int) {
            if (currentLines.isEmpty()) return
            val index = sections.size + 1
            val contentBlock = currentLines.joinToString("\n").trimEnd()
            sections += IngestedSection(
                id = "sec-$index",
                heading = currentHeading,
                startLine = currentStart,
                endLine = endLine,
                content = contentBlock,
                coordinates = "$sourcePath:$currentStart-$endLine"
            )
            currentLines.clear()
        }

        lines.forEachIndexed { index, line ->
            if (line.startsWith("#")) {
                flush(index)
                currentHeading = line.trimStart('#', ' ').ifBlank { "Section ${sections.size + 1}" }
                currentStart = index + 1
            }
            currentLines += line
        }
        flush(lines.size)

        val finalSections = if (sections.isEmpty()) {
            listOf(
                IngestedSection(
                    id = "sec-1",
                    heading = "Document",
                    startLine = 1,
                    endLine = lines.size.coerceAtLeast(1),
                    content = normalized,
                    coordinates = "$sourcePath:1-${lines.size.coerceAtLeast(1)}"
                )
            )
        } else sections.toList()

        return IngestedDocument(
            documentId = "doc-" + sha256("$projectId:$sourcePath").take(12),
            projectId = projectId,
            sourcePath = sourcePath,
            sha256 = sha256(normalized),
            content = normalized,
            sections = finalSections
        )
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
