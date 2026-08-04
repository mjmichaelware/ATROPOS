package atropos.core.endpoint

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OperationEndpointManifestTest {
    @Test
    fun every_registered_operation_exposes_a_complete_manifest() {
        val endpoints = StaticOperationRegistry().getAll()

        assertTrue(endpoints.isNotEmpty())
        endpoints.forEach { endpoint ->
            val manifest = endpoint.manifest
            assertFalse(manifest.owner.isBlank(), endpoint.id)
            assertFalse(manifest.input.isBlank(), endpoint.id)
            assertFalse(manifest.output.isBlank(), endpoint.id)
            assertTrue(manifest.errors.isNotEmpty(), endpoint.id)
            assertFalse(manifest.auth.isBlank(), endpoint.id)
            assertTrue(manifest.timeoutMs > 0, endpoint.id)
            assertFalse(manifest.retryPolicy.isBlank(), endpoint.id)
            assertTrue(manifest.testIds.isNotEmpty(), endpoint.id)
        }
    }

    @Test
    fun registry_lookup_preserves_manifest_identity() {
        val endpoint = StaticOperationRegistry().getById("tool.git.status")
            ?: error("missing git status endpoint")

        assertEquals("tool.git.status", endpoint.id)
        assertTrue(endpoint.manifest.sideEffects.contains("read-git-state"))
    }
}
