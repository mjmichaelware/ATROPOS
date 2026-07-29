package atropos.core.agent

import atropos.core.AtroposRepoRootLocator
import atropos.core.provider.CodebaseContextPacker
import atropos.core.provider.SourceBinding
import atropos.core.provider.SourcePackRequest
import atropos.core.provider.SourcePackResult
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

data class AgentContextSnapshot(
    val repoRoot: Path,
    val text: String,
    val byteCount: Int,
    val truncated: Boolean,
    val sourcePackId: String? = null,
    val fetchReceiptId: String? = null
)

class AgentContextCollector(
    val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    val contextCapBytes: Int = 80 * 1024,
    private val contextPacker: CodebaseContextPacker = CodebaseContextPacker(repoRoot)
) {
    private val selectedSourceFiles = listOf(
        "src/main/kotlin/atropos/core/Provider.kt",
        "src/main/kotlin/atropos/core/ProviderState.kt",
        "src/main/kotlin/atropos/core/Routing.kt",
        "src/main/kotlin/atropos/core/agent/AgentProviderSelector.kt",
        "src/main/kotlin/atropos/cli/CommandRouter.kt",
        "src/main/kotlin/atropos/cli/input/CommandRegistry.kt",
        "src/main/kotlin/atropos/core/agent/AgentContextCollector.kt",
        "src/main/kotlin/atropos/core/agent/AgentPatchExtractor.kt",
        "src/main/kotlin/atropos/core/agent/AgentPatchStore.kt",
        "src/main/kotlin/atropos/core/agent/AgentPromptContract.kt",
        "src/main/kotlin/atropos/core/agent/AgentService.kt",
        "src/main/kotlin/atropos/cli/commands/AgentCommand.kt"
    ).map(repoRoot::resolve)

    fun collect(taskHint: String? = null): AgentContextSnapshot {
        val files = (selectedSourceFiles + taskHintFiles(taskHint)).distinct()
        val builder = StringBuilder(contextCapBytes)
        var truncated = false

        truncated = appendSection(builder, "# Repo Root\n${repoRoot}\n", truncated)
        truncated = appendSection(builder, "# Git Status\n${gitStatus()}\n", truncated)
        truncated = appendSection(builder, "# Shallow Tree\n${shallowTree()}\n", truncated)
        truncated = appendSection(builder, "# Selected Sources\n${selectedSources(files)}\n", truncated)
        val pack = sourcePack(defaultPackRoots(taskHint))
        truncated = appendSection(builder, "# Source Context Pack\n${pack?.text ?: "source context pack unavailable"}\n", truncated)

        val rendered = builder.toString()
        return AgentContextSnapshot(
            repoRoot = repoRoot,
            text = rendered,
            byteCount = rendered.toByteArray(Charsets.UTF_8).size,
            truncated = truncated,
            sourcePackId = pack?.id,
            fetchReceiptId = pack?.fetchReceipt?.id
        )
    }

    fun collectPatch(taskHint: String? = null): AgentContextSnapshot {
        val files = taskHintFiles(taskHint).ifEmpty {
            listOf(repoRoot.resolve("README.md").normalize())
        }.distinct()
        val builder = StringBuilder(contextCapBytes)
        var truncated = false

        truncated = appendSection(builder, "# Repo Root\n${repoRoot}\n", truncated)
        truncated = appendSection(builder, "# Git Status\n${gitStatus()}\n", truncated)
        truncated = appendSection(builder, "# File Snapshots\n${patchSources(files)}\n", truncated)
        truncated = appendSection(builder, "# Shallow Tree\n${shallowTree()}\n", truncated)
        val roots = files.mapNotNull { file -> repoRoot.relativizeSafely(file)?.substringBeforeLast('/', missingDelimiterValue = "") }
            .filter { it.isNotBlank() }
            .ifEmpty { listOf("README.md") }
        val pack = sourcePack(roots)
        truncated = appendSection(builder, "# Source Context Pack\n${pack?.text ?: "source context pack unavailable"}\n", truncated)

        val rendered = builder.toString()
        return AgentContextSnapshot(
            repoRoot = repoRoot,
            text = rendered,
            byteCount = rendered.toByteArray(Charsets.UTF_8).size,
            truncated = truncated,
            sourcePackId = pack?.id,
            fetchReceiptId = pack?.fetchReceipt?.id
        )
    }

    fun collectRepair(taskHint: String? = null, fileHints: List<String> = emptyList()): AgentContextSnapshot {
        val hintedFiles = (taskHintFiles(taskHint) + fileHints.mapNotNull { resolveHint(it) }).distinct()
        val files = hintedFiles.ifEmpty {
            listOf(repoRoot.resolve("README.md").normalize())
        }
        val builder = StringBuilder(contextCapBytes)
        var truncated = false

        truncated = appendSection(builder, "# Repo Root\n${repoRoot}\n", truncated)
        truncated = appendSection(builder, "# Git Status\n${gitStatus()}\n", truncated)
        truncated = appendSection(builder, "# File Snapshots\n${patchSources(files)}\n", truncated)
        truncated = appendSection(builder, "# Shallow Tree\n${shallowTree()}\n", truncated)
        val roots = files.mapNotNull { file -> repoRoot.relativizeSafely(file)?.substringBeforeLast('/', missingDelimiterValue = "") }
            .filter { it.isNotBlank() }
            .ifEmpty { listOf("README.md") }
        val pack = sourcePack(roots)
        truncated = appendSection(builder, "# Source Context Pack\n${pack?.text ?: "source context pack unavailable"}\n", truncated)

        val rendered = builder.toString()
        return AgentContextSnapshot(
            repoRoot = repoRoot,
            text = rendered,
            byteCount = rendered.toByteArray(Charsets.UTF_8).size,
            truncated = truncated,
            sourcePackId = pack?.id,
            fetchReceiptId = pack?.fetchReceipt?.id
        )
    }

    private fun selectedSources(files: List<Path>): String = buildString {
        for (file in files) {
            if (!Files.isRegularFile(file)) continue
            appendLine("--- ${repoRoot.relativize(file)} ---")
            appendLine(Files.readString(file))
        }
    }

    private fun patchSources(files: List<Path>): String = buildString {
        for (file in files) {
            if (!Files.isRegularFile(file)) continue
            appendLine("FILE ${repoRoot.relativize(file)}")
            appendLine(Files.readString(file))
            appendLine("END FILE")
        }
    }

    private fun taskHintFiles(taskHint: String?): List<Path> {
        val task = taskHint?.trim().orEmpty()
        if (task.isBlank()) return emptyList()

        val candidates = mutableSetOf<Path>()
        val lower = task.lowercase()

        if ("readme" in lower) {
            listOf("README.md", "README").forEach { candidates.add(repoRoot.resolve(it).normalize()) }
        }

        Regex("""(?:^|[\s"'`(])([A-Za-z0-9_./-]+\.[A-Za-z0-9_]+)""")
            .findAll(task)
            .mapNotNull { match ->
                val relative = match.groupValues[1].trim().trim('"').trim('\'')
                val candidate = repoRoot.resolve(relative).normalize()
                if (Files.isRegularFile(candidate)) candidate else null
            }
            .forEach { candidates.add(it) }

        return candidates.toList()
    }

    private fun resolveHint(value: String): Path? {
        val candidate = runCatching {
            val path = Path.of(value.trim())
            if (path.isAbsolute) path.normalize() else repoRoot.resolve(path).normalize()
        }.getOrNull() ?: return null
        if (!candidate.startsWith(repoRoot)) return null
        return if (Files.isRegularFile(candidate)) candidate else null
    }

    private fun sourcePack(allowedPaths: List<String>): atropos.core.provider.CodebaseContextPack? {
        val result = contextPacker.pack(
            SourcePackRequest(
                binding = SourceBinding.localPath(repoRoot),
                allowedPaths = allowedPaths.distinct(),
                maxBytes = (contextCapBytes / 2).coerceAtLeast(16 * 1024)
            )
        )
        return (result as? SourcePackResult.Packed)?.pack
    }

    private fun defaultPackRoots(taskHint: String?): List<String> {
        val lower = taskHint.orEmpty().lowercase()
        if ("self-host" in lower || "self host" in lower || "atropos" in lower) {
            return listOf(
                "src/main/kotlin/atropos/core/agent",
                "src/main/kotlin/atropos/core/provider",
                "src/main/kotlin/atropos/core/dag",
                "src/main/kotlin/atropos/core/worktree",
                "src/main/kotlin/atropos/core/verification"
            )
        }
        return listOf(
            "src/main/kotlin/atropos/core/agent",
            "src/main/kotlin/atropos/core/provider"
        )
    }

    private fun Path.relativizeSafely(file: Path): String? =
        runCatching { relativize(file).toString().replace('\\', '/') }.getOrNull()

    private fun gitStatus(): String =
        runCommand("git", "status", "--short", "--branch")

    private fun shallowTree(): String = buildString {
        appendLine(".")
        walk(repoRoot, 0, this)
    }

    private fun walk(dir: Path, depth: Int, out: StringBuilder) {
        if (depth >= 3 || !Files.isDirectory(dir)) return

        val children = try {
            Files.list(dir)
        } catch (_: Exception) {
            return
        }

        try {
            val entries = mutableListOf<Path>()
            children.forEach { candidate ->
                if (!isExcluded(candidate)) {
                    entries.add(candidate)
                }
            }
            entries.sortBy { it.fileName.toString() }

            for (child in entries) {
                val relative = repoRoot.relativize(child).toString()
                out.append("  ".repeat(depth + 1))
                out.appendLine(relative)
                if (Files.isDirectory(child)) {
                    walk(child, depth + 1, out)
                }
            }
        } finally {
            children.close()
        }
    }

    private fun isExcluded(path: Path): Boolean {
        val relative = runCatching { repoRoot.relativize(path).toString() }.getOrDefault(path.fileName.toString())
        val normalized = relative.replace('\\', '/')
        val name = path.fileName.toString()

        if (normalized.startsWith(".git/") || normalized == ".git") return true
        if (normalized.startsWith(".gradle/") || normalized == ".gradle") return true
        if (normalized.startsWith("build/") || normalized == "build") return true
        if (normalized.startsWith(".atropos/secrets/") || normalized == ".atropos/secrets") return true
        if (normalized.startsWith(".atropos/agent/patches/") || normalized == ".atropos/agent/patches") return true
        if (normalized == ".env" || normalized.startsWith(".env.")) return true
        if (name.endsWith(".jar") || name.endsWith(".class")) return true
        if (name.endsWith(".zip") || name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".gif")) return true
        if (name.endsWith(".key") || name.endsWith(".pem") || name.endsWith(".crt") || name.endsWith(".p12")) return true
        if (name.endsWith(".token") || name.endsWith(".secret") || name.endsWith(".credentials")) return true
        if (name.contains("keys", ignoreCase = true) && !name.endsWith(".kt") && !name.endsWith(".kts")) return true
        if (name.contains("token", ignoreCase = true)) return true
        if (name.contains("credential", ignoreCase = true)) return true
        if (name.contains("secret", ignoreCase = true)) return true
        return false
    }

    private fun runCommand(vararg command: String): String {
        return try {
            val process = ProcessBuilder(*command)
                .directory(repoRoot.toFile())
                .redirectErrorStream(true)
                .start()

            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                return "${command.first()} timed out"
            }

            process.inputStream.bufferedReader().readText().trim().ifBlank {
                "${command.first()} produced no output"
            }
        } catch (failure: Exception) {
            "${command.first()} unavailable: ${failure.message ?: failure.javaClass.simpleName}"
        }
    }

    private fun appendSection(builder: StringBuilder, text: String, truncated: Boolean): Boolean {
        if (truncated) return true

        val currentBytes = builder.toString().toByteArray(Charsets.UTF_8).size
        val remaining = contextCapBytes - currentBytes
        if (remaining <= 0) return true

        val sectionBytes = text.toByteArray(Charsets.UTF_8)
        if (sectionBytes.size <= remaining) {
            builder.append(text)
            return false
        }

        builder.append(String(sectionBytes, 0, remaining, Charsets.UTF_8))
        builder.appendLine()
        builder.appendLine("[context truncated]")
        return true
    }
}
