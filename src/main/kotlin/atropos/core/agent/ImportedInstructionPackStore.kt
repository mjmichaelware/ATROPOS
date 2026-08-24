/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.agent

import atropos.core.AtroposRepoRootLocator
import atropos.core.security.ContextPathExclusions
import atropos.core.security.RedactionFilter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.MessageDigest

data class ImportedInstructionPack(
    val id: String,
    val sourcePath: String,
    val contentHash: String,
    val text: String,
    val authority: String = "CONTEXT_ONLY"
) {
    fun hasValidContentHash(): Boolean = authority == "CONTEXT_ONLY" &&
        id == "instructions-${contentHash.take(16)}" && sha256(text) == contentHash

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}

/** Local, redacted instruction context; never Source Authority or amendment state. */
class ImportedInstructionPackStore(
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {
    private val root = repoRoot.resolve(".atropos/context-packs").normalize()
    private val index = root.resolve("instructions.tsv")

    fun import(pathValue: String): Result<ImportedInstructionPack> = runCatching {
        val source = resolveSource(pathValue)
        val text = redactionFilter.redact(Files.readString(source, StandardCharsets.UTF_8)).trimEnd() + "\n"
        require(text.isNotBlank()) { "instruction file is empty" }
        val hash = sha256(text)
        val pack = ImportedInstructionPack("instructions-${hash.take(16)}", repoRoot.relativize(source).toString().replace('\\', '/'), hash, text)
        require(pack.hasValidContentHash()) { "instruction hash self-check failed" }
        Files.createDirectories(root)
        Files.writeString(root.resolve("${pack.id}.md"), render(pack), StandardCharsets.UTF_8)
        val existing = if (Files.isRegularFile(index)) Files.readAllLines(index) else emptyList()
        val line = listOf(pack.id, pack.sourcePath, pack.contentHash).joinToString("\t")
        Files.writeString(index, (existing.filterNot { it.startsWith("${pack.id}\t") } + line).joinToString("\n") + "\n", StandardCharsets.UTF_8)
        pack
    }

    fun latest(maxBytes: Int = 24 * 1024): List<ImportedInstructionPack> {
        if (!Files.isRegularFile(index)) return emptyList()
        var remaining = maxBytes.coerceAtLeast(1)
        return Files.readAllLines(index).asReversed().mapNotNull { line ->
            val fields = line.split('\t')
            if (fields.size != 3) return@mapNotNull null
            val file = root.resolve("${fields[0]}.md").normalize()
            if (!file.startsWith(root) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return@mapNotNull null
            val stored = Files.readString(file, StandardCharsets.UTF_8)
            val body = stored.substringAfter("\n\n", missingDelimiterValue = "")
            val pack = ImportedInstructionPack(fields[0], fields[1], fields[2], body)
            if (!pack.hasValidContentHash()) return@mapNotNull null
            val size = body.toByteArray(StandardCharsets.UTF_8).size
            if (size > remaining) return@mapNotNull null
            remaining -= size
            pack
        }.distinctBy { it.id }
    }

    private fun resolveSource(value: String): Path {
        val path = Path.of(value.trim())
        val source = (if (path.isAbsolute) path else repoRoot.resolve(path)).normalize()
        require(source.startsWith(repoRoot)) { "instruction path must stay inside the repository" }
        val relative = repoRoot.relativize(source).toString().replace('\\', '/')
        require(!ContextPathExclusions.isExcluded(relative)) { "instruction path is excluded from context ingestion" }
        require(Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) { "instruction file not found: $relative" }
        return source
    }

    private fun render(pack: ImportedInstructionPack): String = buildString {
        appendLine("ID=${pack.id}")
        appendLine("SOURCE=${pack.sourcePath}")
        appendLine("CONTENT_HASH=${pack.contentHash}")
        appendLine("AUTHORITY=${pack.authority}")
        appendLine("OVERRIDE_POLICY=Source Docs and ATROPOS policy remain authoritative")
        appendLine()
        append(pack.text)
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8))
        .joinToString("") { "%02x".format(it) }
}
