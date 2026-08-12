package atropos.core.factory

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppDatabaseSecurityPlannerTest {

    private val planner = AppDatabaseSecurityPlanner()

    @Test
    fun `should plan schema`() {
        val result = planner.planSchema("TestProject")
        assertTrue(result.contains("TestProject"))
    }

    @Test
    fun `should generate migration safety rules`() {
        val rules = planner.generateMigrationSafetyRules()
        assertEquals(3, rules.size)
    }

    @Test
    fun `should validate security constraints`() {
        assertTrue(planner.validateSecurityConstraints("CREATE TABLE test;"))
    }
}
