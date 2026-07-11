package atropos.dloi

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.readLines

data class DloiCoordinate(
    val documentId: String,
    val sectionId: String?,
    val lineStart: Int,
    val lineEnd: Int
)

data class DloiSection(
    val id: String,
    val title: String,
    val lineStart: Int,
    val lineEnd: Int
)

data class DloiDocument(
    val id: String,
    val path: Path,
    val sections: List<DloiSection>,
    val lineCount: Int
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
        appendLine("  path: ${document.path}")
        appendLine("  section: ${coordinate.sectionId ?: "none"}")
        appendLine("  lines: ${coordinate.lineStart}-${coordinate.lineEnd}")
        appendLine("  provenance: $provenance")
        appendLine("  excerpt:")
        excerpt.lines().forEach { appendLine("    $it") }
    }.trimEnd()
}

class DloiService(
    private val repoRoot: Path = Path.of(".").toAbsolutePath().normalize()
) {
    fun lookup(address: String): DloiResolution {
        val coordinate = parse(address)
        val document = loadDocuments().firstOrNull { it.id == coordinate.documentId }
            ?: error("unknown DLOI document: ${coordinate.documentId}")
        val section = coordinate.sectionId?.let { sectionId ->
            document.sections.firstOrNull { it.id == slug(sectionId) || slug(it.title) == slug(sectionId) }
                ?: error("unknown DLOI section: ${coordinate.sectionId}")
        }
        val start = coordinate.lineStart.coerceAtLeast(section?.lineStart ?: 1)
        val end = coordinate.lineEnd.coerceAtMost(section?.lineEnd ?: document.lineCount)
        require(start <= end) { "invalid DLOI line range: $start-$end" }
        val excerpt = document.path.readLines(StandardCharsets.UTF_8)
            .subList(start - 1, end)
            .joinToString("\n")
        return DloiResolution(
            coordinate = coordinate.copy(
                sectionId = section?.id ?: coordinate.sectionId,
                lineStart = start,
                lineEnd = end
            ),
            document = document,
            excerpt = excerpt,
            provenance = "${document.path}:${start}-${end}"
        )
    }

    fun resolveTask(task: String): DloiResolution {
        val normalized = task.trim()
        val authority = loadDocuments().firstOrNull { it.id == "authority" }
            ?: error("authority document not found")
        val sections = authority.sections
        val section = sections.firstOrNull { normalized.contains(it.title, ignoreCase = true) }
            ?: error("unable to prove authoritative source section for task")
        return lookup("authority#${section.id}@L${section.lineStart}-${section.lineEnd}")
    }

    fun loadDocuments(): List<DloiDocument> {
        val docsRoot = repoRoot.resolve("docs")
        if (!docsRoot.exists()) return emptyList()
        return Files.list(docsRoot).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.extension.lowercase() == "md" }
                .sorted()
                .map { path ->
                    val lines = path.readLines(StandardCharsets.UTF_8)
                    val sections = mutableListOf<DloiSection>()
                    var currentTitle: String? = null
                    var currentId: String? = null
                    var currentStart = 1
                    lines.forEachIndexed { index, line ->
                        if (line.startsWith("#")) {
                            if (currentTitle != null) {
                                sections += DloiSection(currentId!!, currentTitle!!, currentStart, index)
                            }
                            currentTitle = line.trimStart('#', ' ').trim()
                            currentId = slug(currentTitle!!)
                            currentStart = index + 1
                        }
                    }
                    if (currentTitle != null) {
                        sections += DloiSection(currentId!!, currentTitle!!, currentStart, lines.size)
                    }
                    val aliases = aliases(path)
                    DloiDocument(
                        id = aliases.first(),
                        path = path,
                        sections = sections,
                        lineCount = lines.size
                    )
                }
                .toList()
        }
    }

    private fun parse(address: String): DloiCoordinate {
        val trimmed = address.trim()
        val docAndRest = trimmed.split("#", limit = 2)
        val documentId = slug(docAndRest[0])
        require(documentId.isNotBlank()) { "missing DLOI document id" }
        val sectionAndLines = docAndRest.getOrNull(1)?.split("@", limit = 2)
        val sectionId = sectionAndLines?.getOrNull(0)?.trim()?.takeIf { it.isNotBlank() }?.let(::slug)
        val lineSpec = sectionAndLines?.getOrNull(1)?.trim()
        val (start, end) = parseLineSpec(lineSpec)
        return DloiCoordinate(documentId, sectionId, start, end)
    }

    private fun parseLineSpec(lineSpec: String?): Pair<Int, Int> {
        if (lineSpec.isNullOrBlank()) return 1 to Int.MAX_VALUE
        require(lineSpec.startsWith("L")) { "invalid DLOI line selector: $lineSpec" }
        val parts = lineSpec.removePrefix("L").split("-", limit = 2)
        val start = parts[0].toIntOrNull() ?: error("invalid DLOI line selector: $lineSpec")
        val end = parts.getOrNull(1)?.removePrefix("L")?.toIntOrNull() ?: start
        return start to end
    }

    private fun aliases(path: Path): List<String> {
        val stem = path.name.removeSuffix(".md")
        val normalized = slug(stem)
        return when {
            normalized.contains("canonical_phases_1_11_authority") -> listOf("authority", normalized)
            normalized.contains("canonical_phases_1_11_closure") -> listOf("closure", normalized)
            else -> listOf(normalized)
        }
    }

    private fun slug(value: String): String =
        value.lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
}
