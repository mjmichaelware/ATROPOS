package atropos.core.agent

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Provider edit operations. These are proposals only; [AgentPatchStore] still
 * owns persistence, territory, policy and application.
 *
 * The envelope is deliberately small and strict. It avoids asking a provider
 * to calculate fragile unified-diff offsets while retaining one canonical
 * downstream patch/gate path.
 */
sealed interface AgentEditOperation {
    val path: String

    data class Create(override val path: String, val content: String) : AgentEditOperation
    data class Rewrite(
        override val path: String,
        val content: String,
        val expectedSha256: String? = null
    ) : AgentEditOperation
    data class Replace(
        override val path: String,
        val search: String,
        val replacement: String,
        val expectedSha256: String? = null
    ) : AgentEditOperation
}

data class AgentEditMaterialization(
    val diff: String,
    val touchedPaths: List<String>
)

/** Parses the provider-facing, non-diff edit envelope. */
class AgentEditDecoder {
    fun decode(raw: String): List<AgentEditOperation>? {
        val normalized = raw.replace("\r\n", "\n").replace('\r', '\n')
        val operations = mutableListOf<AgentEditOperation>()
        val consumed = BooleanArray(normalized.length)
        parseBlocks(normalized, CREATE_OR_REWRITE, operations, consumed)
        parseReplaceBlocks(normalized, operations, consumed)
        if (operations.isEmpty()) return null
        if (normalized.indices.any { normalized[it].isWhitespace().not() && !consumed[it] }) return null
        return operations
    }

    private fun parseBlocks(
        text: String,
        pattern: Regex,
        output: MutableList<AgentEditOperation>,
        consumed: BooleanArray
    ) {
        pattern.findAll(text).forEach { match ->
            val kind = match.groupValues[1]
            val path = safePath(match.groupValues[2]) ?: return@forEach
            val content = match.groupValues[3].removePrefix("\n")
            if (content.isEmpty()) return@forEach
            output += if (kind == "create") AgentEditOperation.Create(path, content)
            else AgentEditOperation.Rewrite(path, content)
            mark(consumed, match.range)
        }
    }

    private fun parseReplaceBlocks(text: String, output: MutableList<AgentEditOperation>, consumed: BooleanArray) {
        REPLACE.findAll(text).forEach { match ->
            val path = safePath(match.groupValues[1]) ?: return@forEach
            val search = match.groupValues[2]
            val replacement = match.groupValues[3]
            if (search.isEmpty()) return@forEach
            output += AgentEditOperation.Replace(path, search, replacement)
            mark(consumed, match.range)
        }
    }

    private fun safePath(raw: String): String? {
        val path = raw.trim().replace('\\', '/')
        if (path.isBlank() || path.startsWith('/') || path.contains('\u0000')) return null
        val normalized = Path.of(path).normalize().toString().replace('\\', '/')
        return normalized.takeIf { it.isNotBlank() && it != "." && !it.startsWith("../") && it != ".." }
    }

    private fun mark(target: BooleanArray, range: IntRange) {
        range.forEach { index -> if (index in target.indices) target[index] = true }
    }

    private companion object {
        val CREATE_OR_REWRITE = Regex(
            "(?s)<atropos-(create|rewrite)\\s+path=\"([^\"]+)\">(.*?)</atropos-\\1>"
        )
        val REPLACE = Regex(
            "(?s)<atropos-replace\\s+path=\"([^\"]+)\">\\s*<<<<<<< SEARCH\\n(.*?)\\n=======\\n(.*?)\\n>>>>>>> REPLACE\\s*</atropos-replace>"
        )
    }
}

/**
 * Converts strict operations into the existing patch record format. The
 * resulting patch is checked and applied by AgentPatchStore, so this class has
 * no independent write, policy, territory or verification authority.
 */
class AgentEditMaterializer(private val repoRoot: Path) {
    fun materialize(operations: List<AgentEditOperation>): AgentEditMaterialization {
        require(operations.isNotEmpty()) { "edit envelope contains no operations" }
        val finalContents = linkedMapOf<String, String?>()
        operations.forEach { operation ->
            val path = safePath(operation.path)
            val target = repoRoot.resolve(path).normalize()
            require(target.startsWith(repoRoot.toAbsolutePath().normalize())) { "edit escapes repository root" }
            val existing = if (finalContents.containsKey(path)) {
                finalContents[path]
            } else {
                val loaded = if (Files.exists(target)) Files.readString(target, StandardCharsets.UTF_8) else null
                finalContents[path] = loaded
                loaded
            }
            when (operation) {
                is AgentEditOperation.Create -> {
                    require(existing == null && !Files.exists(target)) { "create target already exists: $path" }
                    finalContents[path] = normalize(operation.content)
                }
                is AgentEditOperation.Rewrite -> {
                    require(existing != null) { "rewrite target does not exist: $path" }
                    requireHash(operation.expectedSha256, existing, path)
                    finalContents[path] = normalize(operation.content)
                }
                is AgentEditOperation.Replace -> {
                    require(existing != null) { "replace target does not exist: $path" }
                    requireHash(operation.expectedSha256, existing, path)
                    require(existing.indexOf(operation.search) == existing.lastIndexOf(operation.search)) {
                        "replace search must match exactly once: $path"
                    }
                    require(existing.contains(operation.search)) { "replace search not found: $path" }
                    finalContents[path] = existing.replace(operation.search, operation.replacement)
                }
            }
        }
        val diff = finalContents.entries.joinToString("\n") { (path, content) ->
            renderDiff(path, content)
        }.trimEnd() + "\n"
        return AgentEditMaterialization(diff, finalContents.keys.toList())
    }

    private fun renderDiff(path: String, replacement: String?): String {
        val target = repoRoot.resolve(path).normalize()
        val before = if (Files.isRegularFile(target)) Files.readString(target, StandardCharsets.UTF_8) else null
        val oldLines = before?.let(::lines) ?: emptyList()
        val newLines = lines(replacement ?: "")
        val prefix = oldLines.zip(newLines).takeWhile { it.first == it.second }.count()
        val suffix = oldLines.drop(prefix).asReversed().zip(newLines.drop(prefix).asReversed())
            .takeWhile { it.first == it.second }.count()
        val oldEnd = oldLines.size - suffix
        val newEnd = newLines.size - suffix
        val contextStart = maxOf(0, prefix - 3)
        val contextEndOld = minOf(oldLines.size, oldEnd + 3)
        val contextEndNew = minOf(newLines.size, newEnd + 3)
        val hunkStart = contextStart + 1
        val oldCount = contextEndOld - contextStart
        val newCount = contextEndNew - contextStart
        return buildString {
            appendLine("--- ${if (before == null) "/dev/null" else "a/$path"}")
            appendLine("+++ b/$path")
            appendLine("@@ -${if (before == null) "0,0" else "$hunkStart,$oldCount"} +$hunkStart,$newCount @@")
            oldLines.subList(contextStart, prefix).forEach { appendLine(" $it") }
            oldLines.subList(prefix, oldEnd).forEach { appendLine("-$it") }
            newLines.subList(prefix, newEnd).forEach { appendLine("+$it") }
            newLines.subList(newEnd, contextEndNew).forEach { appendLine(" $it") }
        }.trimEnd()
    }

    private fun lines(content: String): List<String> = content.replace("\r\n", "\n").replace('\r', '\n').split('\n')

    private fun normalize(content: String): String = content.replace("\r\n", "\n").replace('\r', '\n')

    private fun requireHash(expected: String?, content: String, path: String) {
        expected ?: return
        require(expected.equals(sha256(content), ignoreCase = true)) { "edit source hash mismatch: $path" }
    }

    private fun safePath(raw: String): String {
        val normalized = Path.of(raw).normalize().toString().replace('\\', '/')
        require(normalized.isNotBlank() && normalized != "." && !normalized.startsWith("../") && !normalized.startsWith('/')) {
            "invalid edit path: $raw"
        }
        return normalized
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }
}
