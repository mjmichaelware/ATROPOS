package atropos.bridge

import atropos.bridge.http.HttpRequest
import atropos.core.territory.TerritoryService
import atropos.core.territory.TerritoryStore
import atropos.core.territory.TerritoryAssignment
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BridgeWorkspaceRoutesTest {
    @Test
    fun workspace_tree_and_file_routes_read_write_through_one_bridge_table() {
        val root = Files.createTempDirectory("bridge-workspace")
        try {
            Files.createDirectories(root.resolve("src"))
            Files.writeString(root.resolve("src/Example.kt"), "class Example")
            val table = BridgeRoutes(repoRoot = root).table()

            val tree = table.resolve(request("GET", "/v1/workspace/tree"))
            assertEquals(200, tree.status)
            assertTrue(tree.body.contains("src/Example.kt"))

            val read = table.resolve(request("GET", "/v1/workspace/file", mapOf("path" to "src/Example.kt")))
            assertEquals(200, read.status)
            assertTrue(read.body.contains("class Example"))

            val write = table.resolve(request("PUT", "/v1/workspace/file", mapOf("path" to "src/Example.kt", "content" to "class Updated")))
            assertEquals(200, write.status)
            assertEquals("class Updated", Files.readString(root.resolve("src/Example.kt")))

            val membership = table.resolve(request("GET", "/v1/workspace/territory", mapOf("path" to "src/Example.kt", "surface" to "workbench")))
            assertEquals(200, membership.status)
            assertTrue(membership.body.contains("\"surface\":\"workbench\""))
            assertTrue(membership.body.contains("\"allowed\":true"))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun workspace_write_denies_traversal_and_explicit_read_only_territory() {
            val root = Files.createTempDirectory("bridge-workspace-deny")
        try {
            val store = TerritoryStore(root)
            val territory = TerritoryService(store)
            store.saveAssignment(TerritoryAssignment(ownerId = "web", ownerRole = "surface", allowedPrefix = "src", readOnly = true))
            val table = BridgeRoutes(repoRoot = root, workspaceTerritory = territory).table()

            val traversal = table.resolve(request("PUT", "/v1/workspace/file", mapOf("path" to "../escape.txt", "content" to "no")))
            assertEquals(403, traversal.status)

            val readOnly = table.resolve(request("PUT", "/v1/workspace/file", mapOf("path" to "src/Example.kt", "content" to "no")))
            assertEquals(403, readOnly.status)
            assertFalse(Files.exists(root.resolve("../escape.txt")))
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    private fun request(method: String, path: String, query: Map<String, String> = emptyMap()) =
        HttpRequest(method, path, query, emptyMap(), "")
}
