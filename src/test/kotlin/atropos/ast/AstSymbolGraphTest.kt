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

        val fileSymbol = impacted.first { it.kind == AstSymbolKind.FILE }
        val service = impacted.first { it.name == "ProviderActivationService" }
        assertTrue(fileSymbol.packagePathInvariantHolds)
        assertEquals(
            "atropos/core/provider/ProviderActivationService.kt",
            fileSymbol.expectedPathSuffix
        )
        assertTrue(fileSymbol.dependencyRefs.contains("atropos.core.AtroposConfig"))
        assertEquals("atropos.core.provider", service.packageName)
        assertTrue(service.file.endsWith("src/main/kotlin/atropos/core/provider/ProviderActivationService.kt"))
        assertTrue(service.imports.contains("atropos.core.AtroposConfig"))
        assertTrue(service.line > 0)
        assertTrue(service.column > 0)
        assertTrue(service.offset >= 0)
        assertTrue(impacted.any { it.kind == AstSymbolKind.FUNCTION && it.name == "verifyAll" })
    }

    @Test
    fun reconcile_imports_reports_local_exact_and_external_dependencies() {
        val graph = AstSymbolGraph(Path.of(".").toAbsolutePath().normalize())
        val result = graph.reconcileImports("src/main/kotlin/atropos/core/provider/ProviderActivationService.kt")

        assertTrue(result.packagePathInvariantHolds)
        val local = result.resolutions.first { it.importPath == "atropos.core.AtroposConfig" }
        assertEquals(AstImportStatus.LOCAL_EXACT, local.status)
        assertTrue(local.matches.contains("atropos.core.AtroposConfig"))
        assertTrue(local.expectedPathSuffixes.contains("atropos/core/AtroposConfig.kt"))

        val external = result.resolutions.first { it.importPath == "java.io.File" }
        assertEquals(AstImportStatus.EXTERNAL, external.status)
    }
}
