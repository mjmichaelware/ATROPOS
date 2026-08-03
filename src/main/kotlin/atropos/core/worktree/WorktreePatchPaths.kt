package atropos.core.worktree

import java.nio.file.Path

/**
 * Reads the target paths out of a unified diff, and judges whether they are safe.
 *
 * This is what stands between a provider-authored patch and the filesystem. A
 * diff is just text until someone applies it, and the paths inside it are
 * attacker-controlled in the only sense that matters: nothing in the pipeline
 * that produced them was obliged to keep them inside the repository.
 *
 * ## Traversal is refused by shape, not by result
 *
 * [isUnsafeRelativePath] rejects any path containing a `..` segment even when
 * it would normalise to somewhere harmless. `src/../src/Main.kt` resolves
 * inside the tree, so a resolution-based check would allow it — and would then
 * have to be exactly right about every platform's normalisation to stay safe.
 * Refusing the shape outright costs nothing real: no legitimate generated diff
 * needs to traverse upward, and the rule is simple enough to be obviously
 * correct, which a normalisation comparison is not.
 *
 * Backslashes are folded to `/` first so a Windows-style separator cannot smuggle
 * a segment past a check that only looks for the forward-slash form.
 */
object WorktreePatchPaths {

    /**
     * Every distinct file a diff claims to touch.
     *
     * Both sides of the rename headers are collected, not just `+++`. A rename
     * or delete names its target on the `--- a/` line, and reading only the new
     * side would let a patch remove a file outside its territory while
     * appearing to touch nothing there.
     *
     * `/dev/null` is dropped: it is how a diff spells "this side does not
     * exist" for a create or delete, not a path anyone writes to.
     */
    fun extract(patchContent: String): List<String> =
        patchContent.lineSequence()
            .mapNotNull { line ->
                when {
                    line.startsWith(NEW_SIDE) -> line.removePrefix(NEW_SIDE)
                    line.startsWith(OLD_SIDE) -> line.removePrefix(OLD_SIDE)
                    line.startsWith(GIT_HEADER) -> line.substringAfter(" b/", missingDelimiterValue = "")
                    else -> ""
                }.takeIf { it.isNotBlank() && it != DEV_NULL }
            }
            .map { it.trim() }
            .distinct()
            .toList()

    /**
     * True when a path must not be written under any territory.
     *
     * Absolute paths, traversal segments, and anything the platform refuses to
     * parse are all refused. An unparseable path is treated as unsafe rather
     * than skipped — if it cannot be reasoned about, it cannot be cleared.
     */
    fun isUnsafeRelativePath(path: String): Boolean {
        val normalized = path.replace('\\', '/').trim()
        if (normalized.isBlank() || normalized == DEV_NULL || normalized.startsWith("/")) return true
        if (normalized.split("/").any { it == PARENT_SEGMENT }) return true

        val parsed = runCatching { Path.of(normalized) }.getOrNull() ?: return true
        val canonical = parsed.normalize().toString().replace('\\', '/')
        return parsed.isAbsolute || canonical == PARENT_SEGMENT || canonical.startsWith("$PARENT_SEGMENT/")
    }

    /** The first path in [paths] that is unsafe, or null when all are acceptable. */
    fun firstUnsafe(paths: List<String>): String? = paths.firstOrNull(::isUnsafeRelativePath)

    private const val NEW_SIDE = "+++ b/"
    private const val OLD_SIDE = "--- a/"
    private const val GIT_HEADER = "diff --git "
    private const val DEV_NULL = "/dev/null"
    private const val PARENT_SEGMENT = ".."
}
