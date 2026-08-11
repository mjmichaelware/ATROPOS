package atropos.core.planning

class InternalAtomExtractor {
    private val keywordMap = mapOf(
        AtomDimension.FUNCTIONAL_CONTRACT to listOf("must", "shall", "required", "command", "feature", "behavior"),
        AtomDimension.DEPENDENCY_CONTRACT to listOf("depends", "dependency", "requires", "before", "after"),
        AtomDimension.DATA_LIFECYCLE to listOf("persist", "store", "load", "save", "cache", "journal"),
        AtomDimension.STATE_MODEL to listOf("state", "status", "transition", "lifecycle"),
        AtomDimension.ERROR_MODEL to listOf("error", "fail", "failure", "blocked", "retry"),
        AtomDimension.SECURITY_SECRETS to listOf("secret", "credential", "token", "redact", "redaction"),
        AtomDimension.TERRITORY_CAPABILITIES to listOf("territory", "scope", "write path", "read path"),
        AtomDimension.OBSERVABILITY_PROVENANCE to listOf("event", "evidence", "audit", "provenance", "observe"),
        AtomDimension.RESTART_RECOVERY to listOf("resume", "restart", "recover", "lease", "heartbeat"),
        AtomDimension.PERFORMANCE_RESOURCES to listOf("performance", "resource", "budget", "cadence"),
        AtomDimension.PLATFORM_ENVIRONMENT to listOf("termux", "android", "jvm", "platform", "environment"),
        AtomDimension.ACCESSIBILITY_UX to listOf("ui", "ux", "screen", "render", "width", "no_color"),
        AtomDimension.TESTS_ACCEPTANCE to listOf("test", "acceptance", "verify", "proof", "gate"),
        AtomDimension.INTEGRATION_CALL_SITES to listOf("call", "registration", "endpoint", "command registry"),
        AtomDimension.MIGRATION_COMPATIBILITY to listOf("migrate", "compatibility", "fallback", "legacy"),
        AtomDimension.ROLLBACK_FAILURE_EVIDENCE to listOf("rollback", "revert", "failure evidence", "recoverable")
    )

    fun extract(document: IngestedDocument): List<InternalAtom> {
        val atoms = mutableListOf<InternalAtom>()
        document.sections.forEach { section ->
            val haystack = "${section.heading}\n${section.content}".lowercase()
            val matchedDimensions = keywordMap
                .filterValues { keywords -> keywords.any { keyword -> haystack.contains(keyword) } }
                .keys
                .ifEmpty { setOf(AtomDimension.FUNCTIONAL_CONTRACT) }

            val dependencies = extractDependencies(section.content)
            val territory = extractTerritory(section.content)
            matchedDimensions.forEach { dimension ->
                atoms += InternalAtom(
                    id = stableAtomId(document, section, dimension),
                    projectId = document.projectId,
                    documentId = document.documentId,
                    sectionId = section.id,
                    dimension = dimension,
                    statement = section.content.trim().ifBlank { section.heading },
                    sourceCoordinates = section.coordinates,
                    dependencies = dependencies,
                    territory = territory,
                    sourceDocumentSha256 = document.sha256
                )
            }
        }
        return atoms
    }

    private fun stableAtomId(
        document: IngestedDocument,
        section: IngestedSection,
        dimension: AtomDimension
    ): String = "atom-" + sha256(
        listOf(document.documentId, section.id, dimension.name).joinToString("|")
    ).take(12)

    private fun extractDependencies(content: String): List<String> =
        Regex("""(?:depends on|after|requires)\s+([A-Za-z0-9._:-]+)""", RegexOption.IGNORE_CASE)
            .findAll(content)
            .map { it.groupValues[1] }
            .distinct()
            .toList()

    private fun extractTerritory(content: String): List<String> =
        Regex("""(?:src|docs|scripts|ops|\.atropos|build|/tmp)/[A-Za-z0-9_./-]+""")
            .findAll(content)
            .map { it.value.removeSuffix(".") }
            .distinct()
            .toList()

    private fun sha256(value: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
