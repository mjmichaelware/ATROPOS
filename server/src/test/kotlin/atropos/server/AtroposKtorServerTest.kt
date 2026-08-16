package atropos.server

import atropos.bridge.BridgeRoutes
import io.ktor.server.application.Application
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AtroposKtorServerTest {
    @Test
    fun ktor_module_delegates_to_canonical_bridge_routes() {
        val routes = BridgeRoutes().table().describe()

        assertTrue(routes.contains("/v1/health"))
        // Qualified by receiver: `::atroposBridgeModule` inside a class binds
        // the reference to the test instance, which is not an Application.
        assertNotNull(Application::atroposBridgeModule)
    }
}
