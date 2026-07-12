package atropos.core.dag

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

class DocumentIngestionService(
    private val dagService: DagService = DagService(),
    private val repoRoot: Path = Path.of(System.getProperty("user.dir"))
) {
    private val supportedFormats = setOf("txt", "md", "json", "yaml", "yml", "xml", "csv", "kt", "kts", "java", "gradle", "properties")

    data class IngestionResult(
        val document: SourceDocument?,
        val requirements: List<ExtractedRequirement>,
        val errors: List<String>
    ) {
        val success: Boolean get() = errors.isEmpty()
    }

    fun ingestFile(filePath: String): IngestionResult {
        val file = repoRoot.resolve(filePath).toFile()
        return ingest(file)
    }

    fun ingestText(text: String, format: String, sourceId: String): IngestionResult {
        val errors = mutableListOf<String>()
        val sections = mutableListOf<SourceSection>()

        if (!validateFormat(format, errors)) {
            return IngestionResult(null, emptyList(), errors)
        }

        val sha256 = sha256(text)
        val doc = SourceDocument(
            id = sourceId,
            sha256 = sha256,
            size = text.length.toLong(),
            format = format,
            originalPath = "memory:$sourceId"
        )

        val lines = text.split("\n")
        val extracted = extractSections(lines, format, sections)

        val docWithSections = doc.copy(sections = sections)
        val store = DagStore(repoRoot)
        store.saveDocument(docWithSections)

        val requirements = extractRequirements(text, docWithSections, extracted)
        return IngestionResult(docWithSections, requirements, errors)
    }

    fun computeHIG(requirements: List<ExtractedRequirement>): HIGReport {
        return HIGReport.compute(requirements)
    }

    fun buildDAG(requirements: List<ExtractedRequirement>): DAG {
        for (req in requirements) {
            dagService.addRequirementToDAG(req)
        }

        val cycles = dagService.detectCycles()
        if (cycles.isNotEmpty()) {
            val cycleReport = cycles.joinToString("; ") { it.joinToString(" -> ") }
            val resolved = resolveCycle(cycles, requirements)
            resolved.forEach { dagService.addRequirementToDAG(it) }
        }

        return dagService.dagSnapshot()
    }

    private fun ingest(file: File): IngestionResult {
        val errors = mutableListOf<String>()
        val sections = mutableListOf<SourceSection>()

        if (!file.exists()) {
            return IngestionResult(null, emptyList(), listOf("file not found: ${file.absolutePath}"))
        }

        val format = file.extension.lowercase()
        if (!validateFormat(format, errors)) {
            return IngestionResult(null, emptyList(), errors)
        }

        val content = try {
            file.readText(Charsets.UTF_8)
        } catch (e: Exception) {
            return IngestionResult(null, emptyList(), listOf("unreadable: ${file.absolutePath}: ${e.message}"))
        }

        val sha256 = sha256(content)
        val doc = SourceDocument(
            id = "doc-${sha256.take(16)}",
            sha256 = sha256,
            size = file.length(),
            format = format,
            originalPath = file.absolutePath
        )

        val lines = content.split("\n")
        val headings = extractSections(lines, format, sections)

        val docWithSections = doc.copy(sections = sections)
        val store = DagStore(repoRoot)
        store.saveDocument(docWithSections)

        val requirements = extractRequirements(content, docWithSections, headings)

        return IngestionResult(docWithSections, requirements, errors)
    }

    private fun validateFormat(format: String, errors: MutableList<String>): Boolean {
        if (format !in supportedFormats) {
            errors += "unsupported format: $format (supported: $supportedFormats)"
            return false
        }
        return true
    }

    private fun extractSections(lines: List<String>, format: String, sections: MutableList<SourceSection>): List<String> {
        val headings = mutableListOf<String>()
        var currentHeading = "root"
        var currentStart = 1

        for ((i, line) in lines.withIndex()) {
            val heading = detectHeading(line, format)
            if (heading != null) {
                if (i + 1 > currentStart) {
                    val sectionContent = lines.subList(currentStart - 1, i).joinToString("\n")
                    sections += SourceSection(
                        sectionId = "sec-${sha256(currentHeading).take(8)}",
                        heading = currentHeading,
                        startLine = currentStart,
                        endLine = i,
                        content = sectionContent.take(500),
                        coordinates = "L$currentStart-L$i"
                    )
                }
                headings += heading
                currentHeading = heading
                currentStart = i + 2
            }
        }

        if (currentStart <= lines.size) {
            sections += SourceSection(
                sectionId = "sec-${sha256(currentHeading).take(8)}",
                heading = currentHeading,
                startLine = currentStart,
                endLine = lines.size,
                content = lines.subList(currentStart - 1, lines.size).joinToString("\n").take(500),
                coordinates = "L$currentStart-L${lines.size}"
            )
        }

        return headings
    }

    private fun detectHeading(line: String, format: String): String? {
        return when (format) {
            "md" -> {
                val match = Regex("^(#{1,6})\\s+(.+)$").find(line.trim())
                match?.let { it.groupValues[2].trim() }
            }
            "kt", "kts", "java" -> {
                val match = Regex("^//\\s*#{1,6}\\s+(.+)$").find(line.trim())
                match?.let { it.groupValues[1].trim() }
            }
            else -> {
                val match = Regex("^#{1,6}\\s+(.+)$").find(line.trim())
                match?.let { it.groupValues[1].trim() }
            }
        }
    }

    private fun extractRequirements(content: String, doc: SourceDocument, headings: List<String>): List<ExtractedRequirement> {
        val requirements = mutableListOf<ExtractedRequirement>()
        val lines = content.split("\n")

        for ((sectionIdx, section) in doc.sections.withIndex()) {
            val sectionContent = lines.subList((section.startLine - 1).coerceIn(0, lines.size - 1), section.endLine.coerceIn(1, lines.size)).joinToString("\n")
            val lower = sectionContent.lowercase()

            val terms = listOf(
                "must" to RequirementType.INVARIANT,
                "shall" to RequirementType.CONSTRAINT,
                "require" to RequirementType.FEATURE,
                "need" to RequirementType.FEATURE,
                "implement" to RequirementType.FEATURE,
                "command" to RequirementType.COMMAND,
                "endpoint" to RequirementType.ENDPOINT,
                "function" to RequirementType.FUNCTION,
                "store" to RequirementType.STORE,
                "schema" to RequirementType.SCHEMA,
                "test" to RequirementType.TEST,
                "security" to RequirementType.SECURITY_RULE,
                "error" to RequirementType.ERROR_CONDITION,
                "fallback" to RequirementType.FALLBACK,
                "deploy" to RequirementType.DEPLOYMENT_TARGET,
                "platform" to RequirementType.PLATFORM_REQUIREMENT,
                "accept" to RequirementType.ACCEPTANCE_PROOF,
                "quality" to RequirementType.QUALITY_GATE,
                "permission" to RequirementType.PERMISSION_RULE,
                "state" to RequirementType.STATE,
                "transition" to RequirementType.TRANSITION,
                "ui" to RequirementType.INTERFACE,
                "screen" to RequirementType.SCREEN,
                "component" to RequirementType.COMPONENT,
                "model" to RequirementType.MODEL,
                "field" to RequirementType.FIELD,
                "attribute" to RequirementType.ATTRIBUTE,
                "adapter" to RequirementType.ADAPTER,
                "provider" to RequirementType.PROVIDER,
                "recovery" to RequirementType.RECOVERY
            )

            for ((term, type) in terms) {
                val regex = Regex("(?i).{0,60}$term.{0,60}")
                val matches = regex.findAll(sectionContent)
                for (match in matches) {
                    val wording = match.value.trim()
                    if (wording.length < 10) continue

                    val isInferred = !lower.contains("$term must") && !lower.contains("$term shall")
                    val existingIds = requirements.map { it.canonicalWording }

                    val dedupKey = wording.take(80).lowercase()
                    val isDuplicate = requirements.any { it.canonicalWording.lowercase().take(80) == dedupKey }

                    if (!isDuplicate) {
                        requirements += ExtractedRequirement(
                            sourceDocumentId = doc.id,
                            sourceSectionId = section.sectionId,
                            sourceCoordinates = section.coordinates,
                            canonicalWording = wording,
                            normalizedWording = wording,
                            type = type,
                            classification = if (isInferred) RequirementClassification.INFERRED else RequirementClassification.EXPLICIT,
                            priority = if (type == RequirementType.INVARIANT || type == RequirementType.SECURITY_RULE) 1 else 5,
                            acceptanceCriteria = wording
                        )
                    }
                }
            }
        }

        return requirements.distinctBy { it.canonicalWording.take(80).lowercase() }
    }

    private fun resolveCycle(cycles: List<List<String>>, requirements: List<ExtractedRequirement>): List<ExtractedRequirement> {
        val resolved = mutableListOf<ExtractedRequirement>()
        val cycleNodes = cycles.flatten().toSet()
        for (req in requirements) {
            val cycleDeps = req.dependencies.filter { it in cycleNodes }
            if (cycleDeps.isNotEmpty()) {
                resolved += req.copy(
                    dependencies = req.dependencies - cycleDeps.toSet(),
                    normalizedWording = req.normalizedWording + " [cycle resolved: removed deps ${cycleDeps.joinToString(",")}]"
                )
            }
        }
        return resolved
    }

    private fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(text.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    companion object {
        fun sha256sum(text: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
            return digest.digest(text.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
        }
    }
}
