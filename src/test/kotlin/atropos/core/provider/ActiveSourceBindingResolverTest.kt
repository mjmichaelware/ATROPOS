package atropos.core.provider

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ActiveSourceBindingResolverTest {
    @Test
    fun defaultsToRepoLocalPathWhenNoBindingIsConfigured() {
        val root = Files.createTempDirectory("atropos-active-binding-local-")

        val selection = ActiveSourceBindingResolver(root, env = emptyMap()).resolve()

        val binding = assertNotNull(selection.binding)
        assertEquals(SourceBindingKind.LOCAL_PATH, binding.kind)
        assertEquals(root.toAbsolutePath().normalize().toString(), binding.uri)
        assertTrue(selection.accepted)
    }

    @Test
    fun parsesOriginAgnosticBindingsAndRefusesInvalidConfiguration() {
        val root = Files.createTempDirectory("atropos-active-binding-env-")

        val git = ActiveSourceBindingResolver(
            root,
            env = mapOf(
                "ATROPOS_SOURCE_BINDING_KIND" to "git",
                "ATROPOS_SOURCE_BINDING_URI" to "https://example.invalid/repo.git",
                "ATROPOS_SOURCE_BINDING_REF" to "main"
            )
        ).resolve()
        assertEquals(SourceBindingKind.GIT, assertNotNull(git.binding).kind)
        assertEquals("main", git.binding.ref)

        val http = ActiveSourceBindingResolver(
            root,
            env = mapOf(
                "ATROPOS_SOURCE_BINDING_KIND" to "http_bundle",
                "ATROPOS_SOURCE_BINDING_URI" to "https://example.invalid/bundle.zip"
            )
        ).resolve()
        assertFalse(http.accepted)
        assertTrue(http.refusalReason.orEmpty().contains("SHA256"), http.refusalReason.orEmpty())

        val unknown = ActiveSourceBindingResolver(
            root,
            env = mapOf("ATROPOS_SOURCE_BINDING_KIND" to "github_only")
        ).resolve()
        assertFalse(unknown.accepted)
        assertTrue(unknown.refusalReason.orEmpty().contains("unsupported source binding kind"), unknown.refusalReason.orEmpty())
    }
}
