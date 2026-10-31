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
import java.nio.file.LinkOption

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
    val sourcePackFailure: String? = null,
    /**
     * Redacted, bounded account of how the source pack resolved: effective
     * target root, binding kind, allowed paths and pack outcome. It exists so a
     * `MISSING_SOURCE_PACK` refusal can be diagnosed from the run record alone
     * without re-running the benchmark and without dumping source bytes.
     */
    val sourcePackDiagnostics: String? = null
)

private data class SourcePackSelection(
    val pack: atropos.core.provider.CodebaseContextPack?,
    val failure: String?,
    val diagnostics: String? = null
)

class AgentContextCollector(
    val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    val contextCapBytes: Int = resolveProfileContextCapBytes(),
    private val contextPacker: CodebaseContextPacker = CodebaseContextPacker(repoRoot),
    private val sourceBindingResolver: ActiveSourceBindingResolver = ActiveSourceBindingResolver(repoRoot),
    private val redactionFilter: RedactionFilter = RedactionFilter(),
    private val processRunner: BoundedProcessRunner = BoundedProcessRunner(),
    private val importedInstructionPacks: ImportedInstructionPackStore = ImportedInstructionPackStore(repoRoot),
    private val commandTimeoutMillis: Long = 5_000L,
    private val commandOutputLines: Int = 256,
    val searchDirectoryExclusionGate: GlobalSearchDirectoryExclusionGate = GlobalSearchDirectoryExclusionGate.defaultGate()
) {
    companion object {
        fun resolveProfileContextCapBytes(): Int {
            val envCap = System.getenv("ATROPOS_CONTEXT_CAP_BYTES")?.toIntOrNull()
            if (envCap != null && envCap > 0) return envCap
            val maxMem = Runtime.getRuntime().maxMemory()
            return (maxMem / 32).coerceIn(80L * 1024L, 2L * 1024L * 1024L).toInt()
        }
    }
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
        val targetWorkspace = resolveTargetWorkspace(taskHint)
        val files = (selectedSourceFiles + taskHintFiles(taskHint)).distinct()
        val builder = StringBuilder(contextCapBytes)
        var truncated = false

        truncated = appendSection(builder, "# Repo Root\n${repoRoot}\n", truncated)
        if (targetWorkspace != repoRoot) {
            truncated = appendSection(builder, "# Target Workspace\n$targetWorkspace\n", truncated)
        }
        truncated = appendSection(builder, "# Git Status\n${gitStatus()}\n", truncated)
        truncated = appendSection(builder, "# Shallow Tree\n${shallowTree()}\n", truncated)
        truncated = appendSection(builder, "# Selected Sources\n${selectedSources(files)}\n", truncated)
        val sourcePack = sourcePack(taskPackPaths(taskHint, files, targetWorkspace), targetWorkspace)
        truncated = appendSection(
            builder,
            "# Source Context Pack\n${sourcePack.pack?.text ?: "[source context pack refused: ${sourcePack.failure}]"}\n",
            truncated
        )
        truncated = appendImportedInstructions(builder, truncated)
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
            sourcePackFailure = sourcePack.failure,
            sourcePackDiagnostics = sourcePack.diagnostics
        )
    }

    fun collectPatch(taskHint: String? = null): AgentContextSnapshot {
        val targetWorkspace = resolveTargetWorkspace(taskHint)
        val files = taskHintFiles(taskHint).ifEmpty {
            listOf(repoRoot.resolve("README.md").normalize())
        }.distinct()
        val builder = StringBuilder(contextCapBytes)
        var truncated = false

        truncated = appendSection(builder, "# Repo Root\n${repoRoot}\n", truncated)
        if (targetWorkspace != repoRoot) {
            truncated = appendSection(builder, "# Target Workspace\n$targetWorkspace\n", truncated)
        }
        truncated = appendSection(builder, "# Git Status\n${gitStatus()}\n", truncated)
        truncated = appendSection(builder, "# File Snapshots\n${patchSources(files)}\n", truncated)
        truncated = appendSection(builder, "# Shallow Tree\n${shallowTree()}\n", truncated)
        val roots = taskPackPaths(taskHint, files, targetWorkspace)
        val sourcePack = sourcePack(roots, targetWorkspace)
        truncated = appendSection(
            builder,
            "# Source Context Pack\n${sourcePack.pack?.text ?: "[source context pack refused: ${sourcePack.failure}]"}\n",
            truncated
        )
        truncated = appendImportedInstructions(builder, truncated)
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
            sourcePackFailure = sourcePack.failure,
            sourcePackDiagnostics = sourcePack.diagnostics
        )
    }

    fun collectRepair(taskHint: String? = null, fileHints: List<String> = emptyList()): AgentContextSnapshot {
        val targetWorkspace = resolveTargetWorkspace(taskHint)
        val hintedFiles = (taskHintFiles(taskHint) + fileHints.mapNotNull { resolveHint(it) }).distinct()
        val files = hintedFiles.ifEmpty {
            listOf(repoRoot.resolve("README.md").normalize())
        }
        val builder = StringBuilder(contextCapBytes)
        var truncated = false

        truncated = appendSection(builder, "# Repo Root\n${repoRoot}\n", truncated)
        if (targetWorkspace != repoRoot) {
            truncated = appendSection(builder, "# Target Workspace\n$targetWorkspace\n", truncated)
        }
        truncated = appendSection(builder, "# Git Status\n${gitStatus()}\n", truncated)
        truncated = appendSection(builder, "# File Snapshots\n${patchSources(files)}\n", truncated)
        truncated = appendSection(builder, "# Shallow Tree\n${shallowTree()}\n", truncated)
        val roots = taskPackPaths(taskHint, files, targetWorkspace)
        val sourcePack = sourcePack(roots, targetWorkspace)
        truncated = appendSection(
            builder,
            "# Source Context Pack\n${sourcePack.pack?.text ?: "[source context pack refused: ${sourcePack.failure}]"}\n",
            truncated
        )
        truncated = appendImportedInstructions(builder, truncated)
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
            sourcePackFailure = sourcePack.failure,
            sourcePackDiagnostics = sourcePack.diagnostics
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

    /**
     * Resolves the workspace this task actually operates on.
     *
     * A target workspace is only ever a directory that already exists. When the
     * hint is a free-form issue, a prompt file, or anything that is not an
     * existing directory, the task belongs to the current repository root — the
     * collector must not fabricate an empty temporary workspace and then pack
     * nothing (or an escaping path) out of it, which surfaces as a spurious
     * `MISSING_SOURCE_PACK` refusal.
     */
    private fun resolveTargetWorkspace(taskHint: String?): Path {
        val hintPath = taskHint
            ?.let { runCatching { Path.of(it.trim()) }.getOrNull() }
            ?.takeIf { Files.isDirectory(it) }
            ?: return repoRoot
        return runCatching {
            atropos.core.TargetWorkspaceLocator.resolveTargetWorkspace(hintPath)
        }.getOrDefault(repoRoot)
    }

    private fun taskHintFiles(taskHint: String?, root: Path = repoRoot): List<Path> {
        val task = taskHint?.trim().orEmpty()
        if (task.isBlank()) return emptyList()

        val candidates = mutableSetOf<Path>()
        val lower = task.lowercase()

        // A hint that names an existing file (for example the evaluator's
        // `astropy__astropy-11693_prompt.txt`) is itself a candidate, so its
        // contents can be scanned for referenced source paths below.
        runCatching { Path.of(task) }.getOrNull()
            ?.let { if (it.isAbsolute) it.normalize() else root.resolve(it).normalize() }
            ?.takeIf { Files.isRegularFile(it) && !isExcluded(it) }
            ?.let { candidates.add(it) }

        if ("readme" in lower) {
            listOf("README.md", "README").forEach { candidates.add(root.resolve(it).normalize()) }
        }

        Regex("""(?:^|[\s"'`(])([A-Za-z0-9_./-]+\.[A-Za-z0-9_]+)""")
            .findAll(buildString {
                append(task)
                candidates.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".txt") }
                    .forEach { prompt ->
                        runCatching { Files.readString(prompt) }.getOrNull()?.let {
                            append('\n').append(it)
                        }
                    }
            })
            .mapNotNull { match ->
                val relative = match.groupValues[1].trim().trim('"').trim('\'')
                val candidate = root.resolve(relative).normalize()
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

    private fun sourcePack(allowedPaths: List<String>, targetWorkspace: Path? = null): SourcePackSelection {
        val effectiveRepoRoot = targetWorkspace?.takeIf { it != repoRoot } ?: repoRoot
        val bindingResolver = if (effectiveRepoRoot != repoRoot) {
            ActiveSourceBindingResolver(effectiveRepoRoot)
        } else {
            sourceBindingResolver
        }
        val packer = if (effectiveRepoRoot != repoRoot) {
            CodebaseContextPacker(effectiveRepoRoot)
        } else {
            contextPacker
        }
        val binding = bindingResolver.resolve().binding
            ?: return SourcePackSelection(
                null,
                "SOURCE_BINDING_UNAVAILABLE",
                packDiagnostics(effectiveRepoRoot, "unavailable", allowedPaths, "binding_unavailable")
            )
        val effectiveAllowedPaths = if (targetWorkspace != null && targetWorkspace != repoRoot) {
            // The target packer resolves paths relative to the target root.
            // Callers that discovered a target through the task hint cannot
            // safely reuse paths relative to the engine root.
            allowedPaths.ifEmpty { listOf(".") }
        } else {
            allowedPaths
        }
        val maxPackBytes = (contextCapBytes / 2).coerceAtLeast(16 * 1024)
        val result = packer.pack(
            SourcePackRequest(
                binding = binding,
                allowedPaths = effectiveAllowedPaths.distinct(),
                maxBytes = maxPackBytes,
                // The pack itself remains bounded by maxPackBytes. A smaller
                // per-file default would mark a specifically referenced file
                // as truncated even when the complete file fits in that
                // bounded pack, causing a false fail-closed refusal.
                maxFileBytes = maxPackBytes
            )
        )
        val pack = (result as? SourcePackResult.Packed)?.pack
            ?: return SourcePackSelection(
                null,
                "SOURCE_PACK_REFUSED: $result",
                packDiagnostics(effectiveRepoRoot, binding.kind.name, effectiveAllowedPaths, "refused: $result")
            )
        if (pack.truncated) {
            return SourcePackSelection(
                null,
                "SOURCE_PACK_TRUNCATED",
                packDiagnostics(
                    effectiveRepoRoot,
                    binding.kind.name,
                    effectiveAllowedPaths,
                    "truncated bytes=${pack.byteCount} cap=$maxPackBytes"
                )
            )
        }
        if (pack.text.isBlank()) {
            return SourcePackSelection(
                null,
                "SOURCE_PACK_EMPTY",
                packDiagnostics(effectiveRepoRoot, binding.kind.name, effectiveAllowedPaths, "empty")
            )
        }
        return SourcePackSelection(
            pack,
            null,
            packDiagnostics(
                effectiveRepoRoot,
                binding.kind.name,
                effectiveAllowedPaths,
                "packed id=${pack.id} files=${pack.includedPaths.size} bytes=${pack.byteCount}"
            )
        )
    }

    /**
     * A bounded, redacted one-line account of a pack attempt. Paths are
     * truncated and passed through the redaction filter; no source bytes are
     * ever included.
     */
    private fun packDiagnostics(
        effectiveRoot: Path,
        bindingKind: String,
        allowed: List<String>,
        outcome: String
    ): String {
        val root = redactionFilter.redact(effectiveRoot.toString()).take(200)
        val paths = allowed
            .map { redactionFilter.redact(it.replace('\\', '/')).take(120) }
            .joinToString("|")
        return "pack target=$root binding=$bindingKind allowed=[$paths] outcome=$outcome"
    }

    /**
     * Keep the provider pack bounded to files named by the task. Passing a
     * directory here makes a large external repository consume the whole
     * budget in sorted-file order, which correctly produces a truncated pack
     * but leaves the agent with no attested context. Exact file paths preserve
     * the pack's fail-closed semantics while retaining the issue's useful
     * source references.
     */
    private fun taskPackPaths(taskHint: String?, files: List<Path>, targetWorkspace: Path? = null): List<String> {
        val effectiveRoot = targetWorkspace?.takeIf { it != repoRoot } ?: repoRoot
        val taskFiles = taskHintFiles(taskHint, effectiveRoot)
        val candidates = taskFiles.ifEmpty { files }
        val hinted = candidates.mapNotNull { file ->
            val relative = effectiveRoot.relativizeSafely(file) ?: return@mapNotNull null
            // A candidate that does not live under the effective root — an
            // engine-root file when the target is external, or a prompt file
            // outside it — would relativise to an escaping `..` path. The packer
            // refuses such territory, so drop it here rather than let it poison
            // an otherwise valid pack.
            if (relative.isBlank() || relative.startsWith("/") || relative.split('/').contains("..")) {
                return@mapNotNull null
            }
            relative.takeIf { Files.isRegularFile(file) && !isExcluded(file) }
        }.distinct()
        if (hinted.isNotEmpty()) return hinted

        val defaults = defaultPackRoots(taskHint)
        return defaults.ifEmpty { listOf("README.md", "README.rst", "README") }
    }

    private fun defaultPackRoots(taskHint: String?): List<String> {
        if (!isAtroposEngineRoot(repoRoot)) return listOf("README.md", "README.rst", "README")
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

    /**
     * Checks if the given path is the ATROPOS engine root by looking for
     * ATROPOS-specific marker files/directories.
     */
    private fun isAtroposEngineRoot(path: Path): Boolean {
        return Files.isRegularFile(path.resolve("settings.gradle.kts"), LinkOption.NOFOLLOW_LINKS) &&
            Files.isRegularFile(path.resolve("build.gradle.kts"), LinkOption.NOFOLLOW_LINKS) &&
            Files.isDirectory(path.resolve("src/main/kotlin/atropos"), LinkOption.NOFOLLOW_LINKS)
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

    private fun appendImportedInstructions(builder: StringBuilder, truncated: Boolean): Boolean {
        val packs = importedInstructionPacks.latest()
        if (packs.isEmpty()) return truncated
        val content = buildString {
            appendLine("# Imported Instructions (context-only; Source Authority remains authoritative)")
            packs.forEach { pack ->
                appendLine("--- ${pack.id} source=${pack.sourcePath} sha256=${pack.contentHash} ---")
                appendLine(pack.text)
                appendLine("--- END ${pack.id} ---")
            }
        }
        return appendSection(builder, content, truncated)
    }
}
