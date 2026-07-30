package atropos.core.security

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VaultPathResolverTest {
    @Test
    fun resolves_normalized_secret_path_inside_root() {
        val root = Files.createTempDirectory("atropos-vault-path-")
        val resolver = VaultPathResolver(root.resolve("nested").resolve(".."))

        val path = resolver.secretPath("OPENAI/API KEY")

        assertEquals(root.toAbsolutePath().normalize().resolve("OPENAI_API_KEY.secret"), path)
        assertTrue(path.startsWith(resolver.rootPath()))
    }

    @Test
    fun rejects_blank_names() {
        val resolver = VaultPathResolver(Files.createTempDirectory("atropos-vault-name-"))

        assertFailsWith<IllegalArgumentException> { resolver.secretPath("   ") }
    }

    @Test
    fun ensure_root_creates_the_normalized_directory() {
        val root = Files.createTempDirectory("atropos-vault-create-").resolve("secrets")
        val resolver = VaultPathResolver(root)

        assertEquals(root.toAbsolutePath().normalize(), resolver.ensureRoot())
        assertTrue(Files.isDirectory(root))
    }
}
