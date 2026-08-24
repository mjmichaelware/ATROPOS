package atropos.cli

import atropos.cli.ui.HomeStateProvider
import atropos.core.ApiKeys
import atropos.core.AtroposConfig
import atropos.core.LakehouseConfig
import atropos.core.RuntimeConfig
import atropos.core.integration.McpHostManager
import atropos.core.provider.ProviderOnboardingService
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

class FirstRunDoctorRendererTest {
    @Test
    fun first_run_report_reads_all_six_answers_and_backend_truth() {
        val root = Files.createTempDirectory("first-run-doctor")
        val config = AtroposConfig(
            ApiKeys("", "", "", ""),
            LakehouseConfig(root.toString(), root.resolve("db").toString()),
            RuntimeConfig("local", 0.2, localOnly = true)
        )
        val report = FirstRunDoctorRenderer(
            backendDoctor = BackendDoctor(
                config,
                providers = ProviderOnboardingService(root, emptyMap()),
                mcp = McpHostManager(root)
            ),
            homeState = HomeStateProvider(repoRoot = root)
        ).render("local").joinToString("\n")

        assertTrue(report.contains("ATROPOS FIRST-RUN DOCTOR"))
        listOf("objective=", "doing=", "why=", "progress=", "next=", "evidence=").forEach {
            assertTrue(report.contains(it), "missing six-answer field $it")
        }
        assertTrue(report.contains("health=process-ready"))
        assertTrue(!report.contains("API_KEY="))
    }
}
