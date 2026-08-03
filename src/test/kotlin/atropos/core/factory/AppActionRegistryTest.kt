package atropos.core.factory

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppActionRegistryTest {
    @Test
    fun recognizes_general_app_requests_without_product_names() {
        val registry = AppActionRegistry()

        assertTrue(registry.isAppRequest(listOf("build", "weather", "tracker", "for", "commuters")))
        assertTrue(registry.isAppRequest(listOf("create", "notes")))
        assertTrue(registry.isAppRequest(listOf("create", "a", "todo", "app")))
        assertFalse(registry.isAppRequest(listOf("what", "is", "ATROPOS")))
        assertFalse(registry.isAppRequest(listOf("build")))
    }
}
