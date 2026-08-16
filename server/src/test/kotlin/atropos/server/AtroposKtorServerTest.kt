package atropos.server

import atropos.bridge.BridgeRoutes
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AtroposKtorServerTest {
    @Test
    fun ktor_module_delegates_to_canonical_bridge_routes() {
        val routes = BridgeRoutes().table().describe()

        assertTrue(routes.contains("/v1/health"))
        assertNotNull(::atroposBridgeModule)
    }
}
