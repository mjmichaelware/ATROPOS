package atropos.ast

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.nio.file.Path

class AstSymbolGraphTest {
    @Test
    fun lookup_finds_exact_backend_symbol() {
        val graph = AstSymbolGraph(Path.of(".").toAbsolutePath().normalize())
        val result = graph.lookup("ProviderActivationService")
        assertTrue(result.matches.any { it.qualifiedName.contains("ProviderActivationService") })
    }

    @Test
    fun impacted_paths_return_exact_symbols_import_dependencies_and_coordinates() {
        val graph = AstSymbolGraph(Path.of(".").toAbsolutePath().normalize())
        val impacted = graph.impactedByPaths(
            listOf("src/main/kotlin/atropos/core/provider/ProviderActivationService.kt")
        )

        val service = impacted.first { it.name == "ProviderActivationService" }
        assertEquals("atropos.core.provider", service.packageName)
        assertTrue(service.file.endsWith("src/main/kotlin/atropos/core/provider/ProviderActivationService.kt"))
        assertTrue(service.imports.contains("atropos.core.AtroposConfig"))
        assertTrue(service.line > 0)
        assertTrue(service.column > 0)
        assertTrue(service.offset >= 0)
        assertTrue(impacted.any { it.kind == AstSymbolKind.FUNCTION && it.name == "verifyAll" })
    }
}
