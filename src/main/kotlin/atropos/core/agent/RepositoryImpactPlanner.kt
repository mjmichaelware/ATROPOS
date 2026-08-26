package atropos.core.agent

import atropos.ast.AstSymbolGraph
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/**
 * Produces a bounded affected-file slice for a mutation.
 *
 * The planner is intentionally a consumer of [AstSymbolGraph], not another
 * symbol graph. For very large or non-Kotlin repositories it supplements the
 * graph with bounded import/reference indexing. It never writes and it never
 * decides whether a mutation is authorized.
 */
class RepositoryImpactPlanner(
    private val repoRoot: Path,
    private val astGraph: AstSymbolGraph = AstSymbolGraph(repoRoot),
    private val maxFiles: Int = 20_000,
    private val maxBytes: Long = 64L * 1024 * 1024
) {
    fun plan(changedPaths: List<String>): RepositoryImpactPlan {
        val root = repoRoot.toAbsolutePath().normalize()
        val changed = changedPaths.map { safeRelative(it, root) }.distinct()
        val files = boundedFiles(root)
        val contentByFile = files.associateWith { path ->
            runCatching { Files.readString(path, StandardCharsets.UTF_8) }.getOrDefault("")
        }
        val impacted = LinkedHashSet<Path>()
        changed.forEach { relative ->
            val target = root.resolve(relative).normalize()
            if (Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) impacted.add(target)
        }

        // Existing Kotlin symbol/caller owner, used only when the bounded
        // source slice is small enough to parse without turning planning into
        // an unbounded memory operation.
        val kotlinChanges = changed.filter { it.endsWith(".kt") }
        if (kotlinChanges.isNotEmpty() && files.size <= KOTLIN_GRAPH_FILE_LIMIT) {
            runCatching {
                astGraph.impactOfPaths(kotlinChanges).mapTo(impacted) { it.file }
                astGraph.impactedByPaths(kotlinChanges)
                    .filter { it.kind != atropos.ast.AstSymbolKind.FILE }
                    .flatMap { astGraph.findCallers(it.qualifiedName) }
                    .mapTo(impacted) { it.file }
            }
        }

        val changedNames = changed.map { Path.of(it).fileName.toString().substringBeforeLast('.') }.toSet()
        contentByFile.forEach { (file, content) ->
            if (file in impacted) return@forEach
            val referencesChangedImport = changedNames.any { name ->
                IMPORT_REFERENCE.containsMatchIn(content.replace("-", "_")) &&
                    Regex("\\b${Regex.escape(name)}\\b").containsMatchIn(content)
            }
            if (referencesChangedImport) impacted.add(file)
        }
        return RepositoryImpactPlan(
            changedPaths = changed,
            impactedPaths = impacted.map { root.relativize(it).toString().replace('\\', '/') }.distinct().sorted(),
            scannedFiles = files.size,
            scannedBytes = contentByFile.values.sumOf { it.toByteArray(StandardCharsets.UTF_8).size.toLong() },
            truncated = files.size >= maxFiles || contentByFile.values.sumOf {
                it.toByteArray(StandardCharsets.UTF_8).size.toLong()
            } >= maxBytes
        )
    }

    private fun boundedFiles(root: Path): List<Path> {
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) return emptyList()
        val result = mutableListOf<Path>()
        var bytes = 0L
        Files.walk(root).use { stream ->
            stream.filter { path ->
                Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) &&
                    !path.startsWith(root.resolve(".git")) &&
                    SKIPPED_DIRS.none { path.startsWith(root.resolve(it)) }
            }.sorted().forEach { path ->
                if (result.size >= maxFiles || bytes >= maxBytes) return@forEach
                val size = runCatching { Files.size(path) }.getOrDefault(0L)
                if (bytes + size <= maxBytes) {
                    result.add(path)
                    bytes += size
                }
            }
        }
        return result
    }

    private fun safeRelative(raw: String, root: Path): String {
        val path = root.resolve(raw).normalize()
        require(path.startsWith(root) && !raw.startsWith("/")) { "impact path escapes repository root: $raw" }
        return root.relativize(path).toString().replace('\\', '/')
    }

    private companion object {
        const val KOTLIN_GRAPH_FILE_LIMIT = 5_000
        val SKIPPED_DIRS = setOf(".git", "build", ".gradle", "node_modules", "target", "dist", "out", ".atropos")
        val IMPORT_REFERENCE = Regex("(?m)^\\s*(?:import|use|require|include|open|from)\\b")
    }
}

data class RepositoryImpactPlan(
    val changedPaths: List<String>,
    val impactedPaths: List<String>,
    val scannedFiles: Int,
    val scannedBytes: Long,
    val truncated: Boolean
)
