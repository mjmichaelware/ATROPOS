package atropos.core.factory

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class AppAuthPlannerTest {

    private val planner = AppAuthPlanner()

    @Test
    fun `should plan auth flow`() {
        val result = planner.planAuthenticationFlow("OAuth2")
        assertTrue(result.contains("OAuth2"))
    }

    @Test
    fun `should generate role permission model`() {
        val model = planner.generateRolePermissionModel(listOf("admin", "user"))
        assertEquals(2, model.size)
        assertTrue(model["admin"]?.contains("write:admin") == true)
    }

    @Test
    fun `should create session rules`() {
        val rules = planner.createSessionManagementRules()
        assertEquals(3, rules.size)
    }
}
