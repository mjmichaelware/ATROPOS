/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.endpoint

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * J009-wire: the join that makes an undeclared-but-invocable operation visible.
 * Each registry looks healthy alone; only the join shows the gap.
 */
class EndpointManifestRegistryTest {

    private class FakeOperations(private val ids: List<String>) : OperationRegistry {
        override fun getAll(): List<OperationEndpoint> =
            ids.map { OperationEndpoint(it, EndpointKind.CLI_COMMAND, "operation $it") }

        override fun getById(id: String): OperationEndpoint? = getAll().find { it.id == id }
        override fun getByKind(kind: EndpointKind): List<OperationEndpoint> =
            getAll().filter { it.kind == kind }
    }

    private fun manifest(id: String, tests: List<String> = listOf("SomeTest")) = EndpointManifest(
        id = id,
        owner = "atropos.cli.CommandRegistry",
        input = "tokens",
        output = "rendered output",
        errors = listOf("UNKNOWN_COMMAND"),
        auth = EndpointAuth.NONE,
        sideEffects = EndpointSideEffect.NONE,
        timeoutMillis = 5_000,
        retry = EndpointRetryPolicy.NONE,
        tests = tests
    )

    @Test
    fun a_registry_where_every_operation_is_declared_is_complete() {
        val registry = EndpointManifestRegistry(
            operations = FakeOperations(listOf("cli.help", "cli.status")),
            manifests = listOf(manifest("cli.help"), manifest("cli.status"))
        )

        val coverage = registry.coverage()

        assertTrue(coverage.complete, coverage.evidenceLine())
        assertEquals(listOf("cli.help", "cli.status"), coverage.declaredIds)
    }

    @Test
    fun an_invocable_operation_with_no_manifest_is_named() {
        // The failure the join exists to catch: callable, undeclared, invisible
        // to either registry on its own.
        val registry = EndpointManifestRegistry(
            operations = FakeOperations(listOf("cli.help", "cli.status", "cli.exit")),
            manifests = listOf(manifest("cli.help"))
        )

        val coverage = registry.coverage()

        assertFalse(coverage.complete)
        assertEquals(listOf("cli.exit", "cli.status"), coverage.unmanifestedIds)
        assertTrue(coverage.evidenceLine().contains("unmanifested=cli.exit,cli.status"), coverage.evidenceLine())
    }

    @Test
    fun a_manifest_for_an_operation_that_no_longer_exists_is_named() {
        val registry = EndpointManifestRegistry(
            operations = FakeOperations(listOf("cli.help")),
            manifests = listOf(manifest("cli.help"), manifest("cli.removed"))
        )

        val coverage = registry.coverage()

        assertFalse(coverage.complete)
        assertEquals(listOf("cli.removed"), coverage.orphanedManifestIds)
    }

    @Test
    fun an_incomplete_manifest_fails_coverage_even_when_every_id_lines_up() {
        // Full id coverage with a hollow contract is the exact shape J009 was
        // blocked on: everything declared, nothing constrained.
        val registry = EndpointManifestRegistry(
            operations = FakeOperations(listOf("cli.help")),
            manifests = listOf(manifest("cli.help", tests = emptyList()))
        )

        val coverage = registry.coverage()

        assertFalse(coverage.complete)
        assertTrue(coverage.unmanifestedIds.isEmpty())
        assertEquals(listOf("cli.help"), coverage.invalid.map { it.id })
    }

    @Test
    fun manifest_lookup_returns_the_contract_for_a_known_id_and_null_otherwise() {
        val registry = EndpointManifestRegistry(
            operations = FakeOperations(listOf("cli.help")),
            manifests = listOf(manifest("cli.help"))
        )

        assertEquals("cli.help", registry.manifestFor("cli.help")?.id)
        assertEquals(null, registry.manifestFor("cli.missing"))
        assertEquals(1, registry.all().size)
    }

    @Test
    fun an_empty_manifest_set_reports_every_operation_as_unmanifested() {
        val registry = EndpointManifestRegistry(
            operations = FakeOperations(listOf("cli.help", "cli.status")),
            manifests = emptyList()
        )

        val coverage = registry.coverage()

        assertFalse(coverage.complete)
        assertEquals(listOf("cli.help", "cli.status"), coverage.unmanifestedIds)
    }
}
