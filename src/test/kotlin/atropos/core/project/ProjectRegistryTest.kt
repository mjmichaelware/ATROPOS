package atropos.core.project

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProjectRegistryTest {
    @Test
    fun registersRepositoryBindingDurablyAndDeduplicatesByNameAndRoot() {
        val root = Files.createTempDirectory("atropos-project-registry-")
        val registry = ProjectRegistry(root)

        val first = registry.register(
            "tiny-cli",
            binding = RepositoryBinding(repoRoot = root.toString(), branch = "main", baselineCommit = "abc", dirtyFingerprint = "dirty")
        )
        val second = ProjectRegistry(root).register(
            "tiny-cli",
            binding = RepositoryBinding(repoRoot = root.toString(), branch = "main", baselineCommit = "abc", dirtyFingerprint = "dirty")
        )

        assertTrue(first.created)
        assertFalse(second.created)
        assertEquals(first.record.id, second.record.id)
        assertEquals(root.toString(), second.record.binding.repoRoot)
    }
}
