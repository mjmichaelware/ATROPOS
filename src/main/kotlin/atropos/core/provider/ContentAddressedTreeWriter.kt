package atropos.core.provider

import java.nio.charset.StandardCharsets
import java.nio.file.Files
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
                .filter { Files.isRegularFile(it) }
                .filter { !Files.isSymbolicLink(it) }
                .map { root.relativize(it).toString().replace('\\', '/') }
                .filter { !excluded(it) }
                .sorted()
                .toList()
        }
    }

    private fun excluded(relative: String): Boolean {
        val name = relative.substringAfterLast('/')
        return relative == ".git" ||
            relative.startsWith(".git/") ||
            relative == ".gradle" ||
            relative.startsWith(".gradle/") ||
            relative == "build" ||
            relative.startsWith("build/") ||
            relative == ".atropos/secrets" ||
            relative.startsWith(".atropos/secrets/") ||
            relative == ".atropos/source-bindings" ||
            relative.startsWith(".atropos/source-bindings/") ||
            relative == ".atropos/agent/patches" ||
            relative.startsWith(".atropos/agent/patches/") ||
            name.endsWith(".jar") ||
            name.endsWith(".class") ||
            name.endsWith(".zip") ||
            name.endsWith(".tar") ||
            name.endsWith(".gz") ||
            name.endsWith(".png") ||
            name.endsWith(".jpg") ||
            name.endsWith(".jpeg") ||
            name.endsWith(".gif") ||
            name.endsWith(".key") ||
            name.endsWith(".pem") ||
            name.endsWith(".p12") ||
            name.contains("token", ignoreCase = true) ||
            name.contains("secret", ignoreCase = true) ||
            name.contains("credential", ignoreCase = true)
    }
}

data class FetchTree(
    val treeHash: String,
    val root: Path,
    val paths: List<String>
)
