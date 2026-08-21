package atropos.cli.ui

import atropos.cli.config.ConfigurationManager
import atropos.cli.ui.design.Health
import atropos.cli.ui.design.Role
import atropos.core.memory.LocalMemoryStore
import atropos.core.memory.MemorySearchHit
import atropos.core.security.RedactionFilter

class StatusMemoryRenderer(
    private val store: LocalMemoryStore = LocalMemoryStore(),
    private val redactionFilter: RedactionFilter = RedactionFilter(),
    private val theme: TerminalTheme = TerminalTheme(ConfigurationManager())
) {
    private val surface get() = theme.surface

    fun render(): String = render(80).joinToString("\n")

    fun render(width: Int): List<String> {
        val status = store.status()
        val body = listOf(
            surface.statusRow("local root", "ready", Health.VERIFIED, width),
            surface.row("records", status.totalRecords.toString(), width),
            surface.row("jsonl", status.jsonlFile.name, width),
            surface.statusRow(
                "sqlite",
                if (status.sqliteAvailable) "available" else "unavailable",
                if (status.sqliteAvailable) Health.VERIFIED else Health.ERROR,
                width
            ),
            surface.statusRow(
                "sqlite-vec",
                if (status.sqliteVecAvailable) "available" else "optional/unavailable",
                if (status.sqliteVecAvailable) Health.VERIFIED else Health.PENDING,
                width
            ),
            surface.statusRow(
                "pinecone",
                if (status.pineconeConfigured) "configured" else "off",
                if (status.pineconeConfigured) Health.VERIFIED else Health.UNKNOWN,
                width
            ),
            surface.statusRow(
                "supabase",
                if (status.supabaseConfigured) "configured" else "off",
                if (status.supabaseConfigured) Health.VERIFIED else Health.UNKNOWN,
                width
            ),
            surface.statusRow(
                "google metadata",
                if (status.googleMetadataConfigured) "configured" else "off",
                if (status.googleMetadataConfigured) Health.VERIFIED else Health.UNKNOWN,
                width
            ),
            surface.hint("policy: local first · remote failure never blocks progress", width)
        )
        return surface.block("PERSISTENT MEMORY", body, width, Role.BRAND)
    }

    fun renderSearch(hits: List<MemorySearchHit>): String = renderSearch(hits, 80).joinToString("\n")

    fun renderSearch(hits: List<MemorySearchHit>, width: Int): List<String> {
        val body = if (hits.isEmpty()) {
            listOf(surface.hint("  no hits found", width))
        } else {
            hits.map { hit ->
                val kind = theme.paint(Role.CODE, hit.record.kind.name.lowercase())
                val id = theme.paint(Role.TEXT_SECONDARY, hit.record.id)
                val score = hit.score.toString().padEnd(5)
                val title = redactionFilter.redact(hit.record.title)
                TerminalText.ellipsize("  $score $kind $id $title", width)
            }
        }
        return surface.block("MEMORY SEARCH RESULTS", body, width, Role.BRAND)
    }
}
