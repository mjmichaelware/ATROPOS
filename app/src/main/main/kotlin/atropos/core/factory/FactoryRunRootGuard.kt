/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.factory

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/**
 * Proves a factory run root is safe to create, before it exists.
 *
 * The obvious check — compare `toRealPath()` against the normalized path — cannot
 * be used before the directory exists. `toRealPath()` throws
 * `NoSuchFileException` on a path that is not there yet, and "not there yet" is
 * the normal first run for any new project. Calling it as a pre-creation guard
 * therefore aborted the first `/factory run` in every fresh repository.
 *
 * The guard's intent is still worth keeping: refuse a run root whose parent has
 * been redirected through a symlink, so nothing is written outside the
 * repository. This resolves the deepest ancestor that *does* exist, refuses any
 * symlinked component between the root and that ancestor, and proves the
 * ancestor still lands inside the repository. The caller re-proves the leaf
 * after creation, where `toRealPath()` is meaningful.
 *
 * Paths are normalized rather than assumed absolute, so a relative repository
 * root stays portable across sandboxes.
 */
class FactoryRunRootGuard {

    /** True when [target] may be created without escaping [root]. */
    fun isSafeToCreate(target: Path, root: Path): Boolean = runCatching {
        val normalizedTarget = target.toAbsolutePath().normalize()
        val normalizedRoot = root.toAbsolutePath().normalize()
        if (!normalizedTarget.startsWith(normalizedRoot)) return@runCatching false

        val existingAncestor = deepestExisting(normalizedTarget) ?: return@runCatching false
        if (!existingAncestor.startsWith(normalizedRoot)) return@runCatching false
        if (hasSymbolicComponent(normalizedRoot, existingAncestor)) return@runCatching false

        existingAncestor.toRealPath().startsWith(normalizedRoot.toRealPath())
    }.getOrDefault(false)

    /**
     * The deepest component of [path] that is present on disk. Returns null when
     * nothing on the chain exists, which means the repository root itself is
     * missing and no factory run can be prepared.
     */
    private fun deepestExisting(path: Path): Path? {
        var current: Path? = path
        while (current != null && !Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
            current = current.parent
        }
        return current
    }

    /**
     * True when any component from [root] down to [leaf] is a symbolic link.
     * Walking the chain explicitly is what makes the check meaningful before
     * creation: a redirected parent is refused even though the leaf is absent.
     */
    private fun hasSymbolicComponent(root: Path, leaf: Path): Boolean {
        var current: Path? = leaf
        while (current != null && current.startsWith(root) && current != root) {
            if (Files.isSymbolicLink(current)) return true
            current = current.parent
        }
        return false
    }
}
