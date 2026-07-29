package atropos.dloi

data class DloiTaskMatch(
    val document: DloiDocument,
    val section: DloiSection
)

class DloiTaskResolver(
    private val aliases: DloiAliasResolver = DloiAliasResolver()
) {
    fun resolve(task: String, documents: List<DloiDocument>): DloiTaskMatch {
        val normalizedTask = task.trim()
        require(normalizedTask.isNotBlank()) { "missing task text for authoritative resolution" }
        require(documents.isNotEmpty()) { "authority document not found" }

        val sluggedTask = dloiSlug(normalizedTask)
        val explicitlyMentionedSectionIds = SECTION_ID_PATTERN.findAll(normalizedTask)
            .map { it.value.uppercase() }
            .toSet()
        val documentScoped = documents.filter { document ->
            aliases.documentAliases(document).any { alias -> alias.isNotBlank() && sluggedTask.contains(alias) }
        }
        val candidates = if (documentScoped.isNotEmpty()) documentScoped else documents
        val matches = candidates.flatMap { document ->
            document.sections
                .filter { section -> matchesSection(section, sluggedTask, explicitlyMentionedSectionIds) }
                .map { section -> DloiTaskMatch(document, section) }
        }.distinctBy { it.document.sourceId to it.section.id }

        require(matches.isNotEmpty()) { "unable to prove authoritative source section for task" }

        // The authority document wins when several documents describe the same
        // phase. The blueprint set deliberately restates the same phases, so
        // without precedence every phase task is ambiguous — and the alias
        // table already names which document is authoritative.
        val authoritative = matches.filter { match ->
            aliases.documentAliases(match.document).contains("authority")
        }
        val resolved = if (authoritative.size == 1) authoritative else matches

        require(resolved.size == 1) {
            "ambiguous authoritative source section for task: " +
                resolved.joinToString(", ") { "${it.document.sourceId}#${it.section.id}" }
        }
        return resolved.single()
    }

    private fun matchesSection(
        section: DloiSection,
        sluggedTask: String,
        explicitSectionIds: Set<String>
    ): Boolean {
        if (explicitSectionIds.isNotEmpty()) {
            return section.id.uppercase() in explicitSectionIds
        }
        return aliases.sectionAliases(section).any { alias ->
            alias.length >= 4 && sluggedTask.contains(alias)
        }
    }

    private companion object {
        val SECTION_ID_PATTERN = Regex("""\bS\d{4}\b""", RegexOption.IGNORE_CASE)
    }
}

fun dloiSlug(value: String): String =
    value.lowercase()
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')
