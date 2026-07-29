package atropos.dloi

class DloiAliasResolver {
    fun documentAliases(document: DloiDocument): Set<String> =
        documentAliases(document.sourceId, document.originalFilename).toSet()

    fun documentAliases(sourceId: String, originalFilename: String): List<String> {
        val stem = originalFilename.substringBeforeLast('.')
        val normalized = dloiSlug(stem)
        val ids = listOf(sourceId, dloiSlug(sourceId)).distinct()
        return when {
            normalized.contains("canonical_phases_1_11_authority") ||
                normalized.contains("codex_cli_build_blueprint_over_time") ->
                listOf("authority") + ids + normalized
            normalized.contains("canonical_phases_1_11_closure") ->
                listOf("closure") + ids + normalized
            else -> ids + normalized
        }
    }

    fun sectionAliases(section: DloiSection): Set<String> {
        val titleSlug = dloiSlug(section.title)
        val shortAlias = section.title.split(":").firstOrNull()?.let { dloiSlug(it) }
        return setOfNotNull(section.id, dloiSlug(section.id), titleSlug, shortAlias)
    }
}
