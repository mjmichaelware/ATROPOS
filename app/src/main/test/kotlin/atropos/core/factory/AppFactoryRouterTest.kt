package atropos.core.factory

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppFactoryRouterTest {
    @Test
    fun plans_arbitrary_app_identity_without_classifier_names() {
        val root = Files.createTempDirectory("atropos-factory-plan-")
        val plan = AppFactoryRouter(repoRoot = root).plan(
            "Create a weather service for commuters with tests"
        )

        assertEquals("weather", plan.projectSpec.intent.name)
        assertEquals("service", plan.projectSpec.intent.kind)
        assertTrue(plan.planningDagId == null)
        assertTrue(plan.steps.any { it.kind == FactoryStepKind.CODE })
        assertTrue(plan.steps.any { it.kind == FactoryStepKind.VALIDATE })
    }
}
