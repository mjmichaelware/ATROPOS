package atropos.core.factory

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class AppBackendIntegrationPlannerTest {

    private val planner = AppBackendIntegrationPlanner()

    @Test
    fun `should plan api endpoints`() {
        val endpoints = planner.planApiEndpoints(listOf("users", "posts"))
        assertEquals(2, endpoints.size)
    }

    @Test
    fun `should plan scheduled tasks`() {
        val tasks = planner.planScheduledTasks(listOf("cleanup", "backup"))
        assertEquals(2, tasks.size)
    }

    @Test
    fun `should configure storage abstractions`() {
        val storage = planner.configureStorageAbstractions("S3")
        assertTrue(storage.contains("S3"))
    }

    @Test
    fun `should setup real time channels`() {
        val channels = planner.setupRealTimeChannels(listOf("notifications"))
        assertEquals(1, channels.size)
    }
}
