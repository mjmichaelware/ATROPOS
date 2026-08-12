/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.factory

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FactoryRunRootGuardTest {
    private val guard = FactoryRunRootGuard()

    /**
     * The regression this type exists for: the first `/factory run` in a fresh
     * repository targets a run root that does not exist yet. The previous guard
     * called toRealPath() on it, threw NoSuchFileException, and ended the
     * session instead of creating the directory.
     */
    @Test
    fun accepts_a_run_root_that_does_not_exist_yet() {
        val root = Files.createTempDirectory("atropos-guard-fresh-")
        val target = root.resolve(".atropos/research/factory/factory-0123456789abcdef")

        assertFalse(Files.exists(target), "precondition: target must be absent")
        assertTrue(guard.isSafeToCreate(target, root))
    }

    @Test
    fun accepts_a_run_root_whose_parents_already_exist() {
        val root = Files.createTempDirectory("atropos-guard-warm-")
        val target = root.resolve(".atropos/research/factory/factory-0123456789abcdef")
        Files.createDirectories(target.parent)

        assertTrue(guard.isSafeToCreate(target, root))
    }

    @Test
    fun refuses_a_target_outside_the_repository_root() {
        val root = Files.createTempDirectory("atropos-guard-outside-")
        val elsewhere = Files.createTempDirectory("atropos-guard-elsewhere-")

        assertFalse(guard.isSafeToCreate(elsewhere.resolve("factory-abc"), root))
    }

    @Test
    fun refuses_a_target_that_climbs_out_with_dot_dot() {
        val root = Files.createTempDirectory("atropos-guard-climb-")
        val target = root.resolve(".atropos/../../escaped/factory-abc")

        assertFalse(guard.isSafeToCreate(target, root))
    }

    /**
     * The property the original check was protecting. A redirected parent must
     * be refused *before* anything is created, which is why the guard walks the
     * existing ancestors rather than trusting the absent leaf.
     */
    @Test
    fun refuses_when_an_existing_parent_is_a_symbolic_link() {
        val root = Files.createTempDirectory("atropos-guard-symlink-")
        val outside = Files.createTempDirectory("atropos-guard-target-")
        val research = root.resolve(".atropos/research")
        Files.createDirectories(research)

        val linked: Path = research.resolve("factory")
        try {
            Files.createSymbolicLink(linked, outside)
        } catch (_: UnsupportedOperationException) {
            return // filesystem without symlink support; nothing to assert
        } catch (_: java.io.IOException) {
            return
        }

        assertFalse(guard.isSafeToCreate(linked.resolve("factory-abc"), root))
    }
}
