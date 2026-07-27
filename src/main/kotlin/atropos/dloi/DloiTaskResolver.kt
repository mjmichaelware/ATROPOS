package atropos.dloi

internal data class DloiTaskMatch(
    val document: DloiDocument,
    val section: DloiSection
)

internal class DloiTaskResolver {
    fun resolve(task: String, documents: List<DloiDocument>): DloiTaskMatch {
        val normalizedTask = task.trim()
        require(normalizedTask.isNotBlank()) { "missing task text for authoritative resolution" }
        require(documents.isNotEmpty()) { "authority document not found" }

        val sluggedTask = dloiSlug(normalizedTask)
        val explicitlyMentionedSectionIds = SECTION_ID_PATTERN.findAll(normalizedTask)
            .map { it.value.uppercase() }
            .toSet()
        val documentScoped = documents.filter { document ->
            documentAliases(document).any { alias -> alias.isNotBlank() && sluggedTask.contains(alias) }
        }
        val candidates = if (documentScoped.isNotEmpty()) documentScoped else documents
        val matches = candidates.flatMap { document ->
            document.sections
                .filter { section -> matchesSection(section, sluggedTask, explicitlyMentionedSectionIds) }
                .map { section -> DloiTaskMatch(document, section) }
        }.distinctBy { it.document.sourceId to it.section.id }

        require(matches.isNotEmpty()) { "unable to prove authoritative source section for task" }
        require(matches.size == 1) {
            "ambiguous authoritative source section for task: " +
                matches.joinToString(", ") { "${it.document.sourceId}#${it.section.id}" }
        }
        return matches.single()
    }

    private fun matchesSection(
        section: DloiSection,
        sluggedTask: String,
        explicitSectionIds: Set<String>
    ): Boolean {
        if (explicitSectionIds.isNotEmpty()) {
            return section.id.uppercase() in explicitSectionIds
        }
        return sectionAliases(section).any { alias ->
            alias.length >= 4 && sluggedTask.contains(alias)
        }
    }

    private fun documentAliases(document: DloiDocument): Set<String> {
        val stem = document.originalFilename.substringBeforeLast('.')
        val normalized = dloiSlug(stem)
        return when {
            normalized.contains("canonical_phases_1_11_authority") ||
                normalized.contains("codex_cli_build_blueprint_over_time") ->
                setOf("authority", dloiSlug(document.sourceId), normalized)
            normalized.contains("canonical_phases_1_11_closure") ->
                setOf("closure", dloiSlug(document.sourceId), normalized)
            else -> setOf(dloiSlug(document.sourceId), normalized)
        }
    }

    private fun sectionAliases(section: DloiSection): Set<String> {
        val titleSlug = dloiSlug(section.title)
        val shortAlias = section.title.split(":").firstOrNull()?.let(::dloiSlug)
        return setOfNotNull(section.id.lowercase(), dloiSlug(section.id), titleSlug, shortAlias)
    }

    private companion object {
        val SECTION_ID_PATTERN = Regex("""\bS\d{4}\b""", RegexOption.IGNORE_CASE)
    }
}

internal fun dloiSlug(value: String): String =
    value.lowercase()
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')
