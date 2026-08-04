package atropos.core.provider

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import atropos.core.security.ContextPathExclusions
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

class ContentAddressedTreeWriter(
    private val storeRoot: Path
) {
    fun materialize(sourceRoot: Path): FetchTree {
        val normalizedSource = sourceRoot.toAbsolutePath().normalize()
        val digest = MessageDigest.getInstance("SHA-256")
        val files = listFiles(normalizedSource)
        files.forEach { relative ->
            digest.update(relative.toByteArray(StandardCharsets.UTF_8))
            digest.update(0)
            digest.update(Files.readAllBytes(normalizedSource.resolve(relative)))
            digest.update(0)
        }
        val treeHash = digest.digest().joinToString("") { "%02x".format(it) }
        val target = storeRoot.resolve(treeHash).normalize()
        if (!Files.exists(target)) {
            Files.createDirectories(target)
            files.forEach { relative ->
                val from = normalizedSource.resolve(relative)
                val to = target.resolve(relative).normalize()
                if (!to.startsWith(target)) return@forEach
                if (Files.isSymbolicLink(from)) return@forEach
                Files.createDirectories(to.parent)
                Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING)
            }
        }
        return FetchTree(treeHash, target, files)
    }

    private fun listFiles(root: Path): List<String> {
        if (!Files.isDirectory(root)) return emptyList()
        return Files.walk(root).use { stream ->
            stream
                .filter { Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) }
                .map { root.relativize(it).toString().replace('\\', '/') }
                .filter { !excluded(it) }
                .sorted()
                .toList()
        }
    }

    /**
     * Delegated to the single owner shared with directly collected context.
     *
     * These rules used to live here in a second copy that had drifted: this
     * side did not exclude `.env`, the collector's side did not exclude
     * `.atropos/source-bindings`. Both sets now apply on both paths.
     */
    private fun excluded(relative: String): Boolean =
        ContextPathExclusions.isExcluded(relative)
}

data class FetchTree(
    val treeHash: String,
    val root: Path,
    val paths: List<String>
)
