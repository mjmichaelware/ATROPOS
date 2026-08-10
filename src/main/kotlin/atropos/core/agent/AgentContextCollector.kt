package atropos.core.agent

import atropos.core.AtroposRepoRootLocator
import atropos.core.provider.ActiveSourceBindingResolver
import atropos.core.provider.CodebaseContextPacker
import atropos.core.provider.SourcePackRequest
import atropos.core.provider.SourcePackResult
import atropos.core.provider.SourceBindingKind
import atropos.core.policy.BoundedProcessRunner
import atropos.core.security.RedactionFilter
import java.nio.file.Files
import atropos.core.security.ContextPathExclusions
import java.nio.file.Path

data class AgentContextSnapshot(
    val repoRoot: Path,
    val text: String,
    val byteCount: Int,
    val truncated: Boolean,
    val sourcePackId: String? = null,
    val fetchReceiptId: String? = null,
    val sourcePackContentHash: String? = null,
    val sourceTreeHash: String? = null,
    val sourceBindingKind: SourceBindingKind? = null,
    val sourcePackFailure: String? = null
)

private data class SourcePackSelection(
    val pack: atropos.core.provider.CodebaseContextPack?,
    val failure: String?
)

class AgentContextCollector(
    val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    val contextCapBytes: Int = 80 * 1024,
    private val contextPacker: CodebaseContextPacker = CodebaseContextPacker(repoRoot),
    private val sourceBindingResolver: ActiveSourceBindingResolver = ActiveSourceBindingResolver(repoRoot),
    private val redactionFilter: RedactionFilter = RedactionFilter(),
    private val processRunner: BoundedProcessRunner = BoundedProcessRunner(),
    private val commandTimeoutMillis: Long = 5_000L,
    private val commandOutputLines: Int = 256
) {
    private val boundedBuilder = Utf8BoundedBuilder(contextCapBytes)

    private val selectedSourceFiles = listOf(
        "src/main/kotlin/atropos/core/agent/AgentProviderSelector.kt",
        "src/main/kotlin/atropos/core/provider/ProviderDescriptor.kt",
        "src/main/kotlin/atropos/core/provider/ProviderTruthService.kt",
        "src/main/kotlin/atropos/core/provider/RoutePolicy.kt",
        "src/main/kotlin/atropos/core/provider/ProviderTask.kt",
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
        val sourcePack = sourcePack(defaultPackRoots(taskHint))
        truncated = appendSection(
            builder,
            "# Source Context Pack\n${sourcePack.pack?.text ?: "[source context pack refused: ${sourcePack.failure}]"}\n",
            truncated
        )
        if (sourcePack.failure != null) truncated = true
        val pack = sourcePack.pack

        val rendered = builder.toString()
        return AgentContextSnapshot(
            repoRoot = repoRoot,
            text = rendered,
            byteCount = rendered.toByteArray(Charsets.UTF_8).size,
            truncated = truncated,
            sourcePackId = pack?.id,
            fetchReceiptId = pack?.fetchReceipt?.id,
            sourcePackContentHash = pack?.contentHash,
            sourceTreeHash = pack?.fetchReceipt?.treeHash,
            sourceBindingKind = pack?.fetchReceipt?.bindingKind,
            sourcePackFailure = sourcePack.failure
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
        val sourcePack = sourcePack(roots)
        truncated = appendSection(
            builder,
            "# Source Context Pack\n${sourcePack.pack?.text ?: "[source context pack refused: ${sourcePack.failure}]"}\n",
            truncated
        )
        if (sourcePack.failure != null) truncated = true
        val pack = sourcePack.pack

        val rendered = builder.toString()
        return AgentContextSnapshot(
            repoRoot = repoRoot,
            text = rendered,
            byteCount = rendered.toByteArray(Charsets.UTF_8).size,
            truncated = truncated,
            sourcePackId = pack?.id,
            fetchReceiptId = pack?.fetchReceipt?.id,
            sourcePackContentHash = pack?.contentHash,
            sourceTreeHash = pack?.fetchReceipt?.treeHash,
            sourceBindingKind = pack?.fetchReceipt?.bindingKind,
            sourcePackFailure = sourcePack.failure
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
        val sourcePack = sourcePack(roots)
        truncated = appendSection(
            builder,
            "# Source Context Pack\n${sourcePack.pack?.text ?: "[source context pack refused: ${sourcePack.failure}]"}\n",
            truncated
        )
        if (sourcePack.failure != null) truncated = true
        val pack = sourcePack.pack

        val rendered = builder.toString()
        return AgentContextSnapshot(
            repoRoot = repoRoot,
            text = rendered,
            byteCount = rendered.toByteArray(Charsets.UTF_8).size,
            truncated = truncated,
            sourcePackId = pack?.id,
            fetchReceiptId = pack?.fetchReceipt?.id,
            sourcePackContentHash = pack?.contentHash,
            sourceTreeHash = pack?.fetchReceipt?.treeHash,
            sourceBindingKind = pack?.fetchReceipt?.bindingKind,
            sourcePackFailure = sourcePack.failure
        )
    }

    private fun selectedSources(files: List<Path>): String = buildString {
        for (file in files) {
            if (!Files.isRegularFile(file) || isExcluded(file)) continue
            appendLine("--- ${repoRoot.relativize(file)} ---")
            appendLine(redactionFilter.redact(Files.readString(file)))
        }
    }

    private fun patchSources(files: List<Path>): String = buildString {
        for (file in files) {
            if (!Files.isRegularFile(file) || isExcluded(file)) continue
            appendLine("FILE ${repoRoot.relativize(file)}")
            appendLine(redactionFilter.redact(Files.readString(file)))
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
                if (Files.isRegularFile(candidate) && !isExcluded(candidate)) candidate else null
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
        return if (Files.isRegularFile(candidate) && !isExcluded(candidate)) candidate else null
    }

    private fun sourcePack(allowedPaths: List<String>): SourcePackSelection {
        val binding = sourceBindingResolver.resolve().binding
            ?: return SourcePackSelection(null, "SOURCE_BINDING_UNAVAILABLE")
        val result = contextPacker.pack(
            SourcePackRequest(
                binding = binding,
                allowedPaths = allowedPaths.distinct(),
                maxBytes = (contextCapBytes / 2).coerceAtLeast(16 * 1024)
            )
        )
        val pack = (result as? SourcePackResult.Packed)?.pack
            ?: return SourcePackSelection(null, "SOURCE_PACK_REFUSED")
        if (pack.truncated) {
            return SourcePackSelection(null, "SOURCE_PACK_TRUNCATED")
        }
        if (pack.text.isBlank()) {
            return SourcePackSelection(null, "SOURCE_PACK_EMPTY")
        }
        return SourcePackSelection(pack, null)
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

    /**
     * Territory-independent exclusion, delegated to the single owner.
     *
     * A path that cannot be relativised falls back to its filename so an
     * unresolvable path is still judged rather than silently admitted.
     */
    private fun isExcluded(path: Path): Boolean {
        val relative = runCatching { repoRoot.relativize(path).toString() }
            .getOrDefault(path.fileName.toString())
        return ContextPathExclusions.isExcluded(relative)
    }

    private fun runCommand(vararg command: String): String {
        val result = try {
            processRunner.run(
                command = command.toList(),
                directory = repoRoot,
                timeoutMillis = commandTimeoutMillis,
                maxOutputBytes = contextCapBytes.coerceIn(1, 256 * 1024),
                maxOutputLines = commandOutputLines
            )
        } catch (failure: Exception) {
            return "${command.first()} unavailable: ${redactionFilter.redact(failure.message ?: failure.javaClass.simpleName)}"
        }

        if (result.timedOut) return "${command.first()} timed out"
        if (result.launchError != null) {
            return "${command.first()} unavailable: ${redactionFilter.redact(result.launchError)}"
        }

        val output = listOf(result.stdout, result.stderr)
            .filter { it.isNotBlank() }
            .joinToString("\n")
            .trim()
        if (result.exitCode != 0) {
            return "${command.first()} unavailable: ${redactionFilter.redact(output.ifBlank { "exit=${result.exitCode}" })}"
        }
        if (result.outputTruncated) {
            return redactionFilter.redact(output).ifBlank { "${command.first()} produced no output" } + "\n[command output truncated]"
        }
        return redactionFilter.redact(output).ifBlank {
            "${command.first()} produced no output"
        }
    }

    private fun appendSection(builder: StringBuilder, text: String, truncated: Boolean): Boolean =
        boundedBuilder.append(builder, text, truncated)
}
