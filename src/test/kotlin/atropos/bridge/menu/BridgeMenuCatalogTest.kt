/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.menu

import atropos.bridge.BridgeRoutes
import atropos.bridge.http.HttpRequest
import atropos.bridge.projection.CommandMenuProjection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BridgeMenuCatalogTest {

    @Test
    fun test_menu_catalog_and_routing_integrity() {
        val actions = BridgeMenuCatalog.actions()
        assertTrue(actions.isNotEmpty())

        val routesTable = BridgeRoutes().table()

        for (action in actions) {
            val route = BridgeMenuCatalog.routeFor(action.id)
            assertNotNull(route, "Action ${action.id} must have a route defined")
            
            // Resolve the route in the HttpRouteTable to ensure no drift.
            // If the route doesn't exist, resolve() will return 404.
            // If the method is wrong, it will return 405.
            val request = HttpRequest(
                method = route.method,
                path = route.path,
                query = emptyMap(),
                headers = emptyMap(),
                body = ""
            )
            val response = routesTable.resolve(request)
            assertTrue(response.status != 404, "Route ${route.method} ${route.path} for action ${action.id} was not found in BridgeRoutes")
            assertTrue(response.status != 405, "Route ${route.method} ${route.path} for action ${action.id} has incorrect HTTP method in BridgeRoutes")
        }
    }

    @Test
    fun test_command_menu_projection_rendering() {
        val actions = BridgeMenuCatalog.actions()
        val projection = CommandMenuProjection()
        val renderedJson = projection.render(actions)
        
        assertTrue(renderedJson.contains("\"count\":${actions.size}"))
        assertTrue(renderedJson.contains("\"groups\""))
        assertTrue(renderedJson.contains("Conversation"))
        assertTrue(renderedJson.contains("Work"))
        assertTrue(renderedJson.contains("Status"))
        assertTrue(renderedJson.contains("Governance"))
    }
}
