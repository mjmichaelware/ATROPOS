/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.factory

import atropos.core.AtroposConfig
import atropos.core.ProviderFactory
import atropos.core.dag.DagNode
import atropos.core.dag.DagNodeAction
import atropos.core.dag.DagNodeState
import atropos.core.dag.DagStore
import atropos.core.memory.LocalMemoryStore
import atropos.core.provider.ModelDiscoveryEngine
import atropos.core.provider.ProviderCascadeRouter
import atropos.core.thinking.Thinking
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * An explicit, atomic repair node spawned in the DAG when verification fails.
 */
data class RepairAtom(
    val id: String,
    val targetNodeId: String,
    val failureReason: String,
    val errorLog: String,
    val attempt: Int,
    val createdAt: Instant = Instant.now()
) {
    fun toDagNode(dagId: String, metaDir: Path): DagNode = DagNode(
        id = id,
        dagId = dagId,
        label = "Repair verification failure for $targetNodeId (attempt $attempt)",
        action = DagNodeAction.PROVIDER_CALL,
        actionPayload = errorLog.take(2000),
        dependencies = listOf(targetNodeId),
        territory = emptyList(),
        expectedOutputs = emptyList(),
        state = DagNodeState.RUNNING,
        createdAt = createdAt,
        updatedAt = createdAt,
        metaFile = metaDir.resolve("$id.meta")
    )

    companion object {
        fun spawn(failedNode: DagNode, errorLog: String, attempt: Int): RepairAtom {
            val safeTargetId = failedNode.id.removePrefix("node-")
            val id = "repair-$safeTargetId-$attempt"
            return RepairAtom(
                id = id,
                targetNodeId = failedNode.id,
                failureReason = failedNode.failureReason ?: "verification failed",
                errorLog = errorLog.ifBlank { failedNode.result ?: "verification failed" },
                attempt = attempt
            )
        }
    }
}

data class RepairAtomResult(
    val success: Boolean,
    val repairAtom: RepairAtom,
    val message: String,
    val patchedFiles: List<String> = emptyList()
)

/**
 * Routes verification errors and stack traces through the active LLM provider cascade
 * to spawn RepairAtoms and patch the project filesystem.
 */
class FactoryRepairRouter(
    private val repoRoot: Path,
    private val dagStore: DagStore,
    private val memoryStore: LocalMemoryStore? = null,
    private val cascadeRouter: ProviderCascadeRouter = ProviderCascadeRouter(
        factory = ProviderFactory()
    )
) {
    fun repair(
        dagId: String,
        failedNode: DagNode,
        errorLog: String,
        attempt: Int,
        targetDir: Path = repoRoot
    ): RepairAtomResult {
        val repairAtom = RepairAtom.spawn(failedNode, errorLog, attempt)
        val dagDir = repoRoot.resolve(".atropos/dag/execution/definitions").resolve(dagId)
        val repairNode = repairAtom.toDagNode(dagId, dagDir)

        Thinking.outline("repair", "Spawning RepairAtom [${repairAtom.id}] for failed node ${failedNode.id}")
        Thinking.step("repair", "Writing repair atom to DAG obligation store (attempt $attempt)")
        runCatching { dagStore.writeNode(repairNode) }

        ModelDiscoveryEngine.ensureDiscovered()
        val activeProvider = ModelDiscoveryEngine.resolveActiveProvider("default")
        Thinking.detail("repair", "Active verified provider resolved for repair: $activeProvider")

        val sourceFiles = collectSourceFiles(targetDir)
        val prompt = buildRepairPrompt(failedNode, errorLog, attempt, sourceFiles)

        val cascadeResult = try {
            Thinking.step("repair", "Querying provider cascade ($activeProvider) with failure stack trace")
            cascadeRouter.completeWithCascade(
                requestedProvider = activeProvider,
                prompt = prompt,
                context = errorLog.take(2000)
            )
        } catch (e: Exception) {
            Thinking.detail("repair", "Provider repair call failed: ${e.message}")
            dagStore.writeNode(repairNode.copy(
                state = DagNodeState.FAILED,
                result = "Provider repair call crashed: ${e.message}",
                failureReason = e.message ?: "call failed",
                finishedAt = Instant.now()
            ))
            return RepairAtomResult(
                success = false,
                repairAtom = repairAtom,
                message = "Provider call failed: ${e.message}"
            )
        }

        val patched = applyPatch(cascadeResult.response, targetDir)
        val success = patched.isNotEmpty()

        val finalNode = repairNode.copy(
            state = if (success) DagNodeState.COMPLETE else DagNodeState.FAILED,
            result = if (success) {
                "Successfully patched ${patched.size} file(s): ${patched.joinToString(", ")}"
            } else {
                "Provider answered but no file patches could be extracted: ${cascadeResult.response.take(200)}"
            },
            failureReason = if (success) null else "No patch extracted from provider response",
            finishedAt = Instant.now()
        )
        runCatching { dagStore.writeNode(finalNode) }

        if (success) {
            Thinking.step("repair", "RepairAtom [${repairAtom.id}] successfully applied ${patched.size} patch(es)")
            memoryStore?.rememberDetailed(
                kind = atropos.core.memory.MemoryKind.NOTE,
                title = "repair applied for ${failedNode.id}",
                body = "files=${patched.joinToString(", ")} provider=${cascadeResult.providerName}",
                tags = listOf("repair", "success", failedNode.id),
                subjectType = "repair_atom",
                subjectId = repairAtom.id
            )
        } else {
            Thinking.step("repair", "RepairAtom [${repairAtom.id}] could not produce valid patch")
        }

        return RepairAtomResult(
            success = success,
            repairAtom = repairAtom,
            message = finalNode.result ?: "repair completed",
            patchedFiles = patched
        )
    }

    private fun collectSourceFiles(targetDir: Path): Map<String, String> {
        val files = mutableMapOf<String, String>()
        if (!Files.exists(targetDir)) return files
        Files.walk(targetDir).use { paths ->
            paths.filter { Files.isRegularFile(it) && !targetDir.relativize(it).startsWith(".atropos") && !targetDir.relativize(it).startsWith(".git") }
                .forEach { file ->
                    val rel = targetDir.relativize(file).toString().replace('\\', '/')
                    runCatching {
                        files[rel] = Files.readString(file)
                    }
                }
        }
        return files
    }

    private fun buildRepairPrompt(
        failedNode: DagNode,
        errorLog: String,
        attempt: Int,
        sourceFiles: Map<String, String>
    ): String = buildString {
        appendLine("[FACTORY DAG REPAIR GATE]")
        appendLine("Failed Atom: ${failedNode.label} (${failedNode.id})")
        appendLine("Verification Command: ${failedNode.actionPayload ?: "test suite"}")
        appendLine("Attempt: $attempt / 3")
        appendLine()
        appendLine("[FAILURE DIAGNOSTIC / STACK TRACE / ERROR LOGS]")
        appendLine(errorLog.take(4000))
        appendLine()
        appendLine("[CURRENT PROJECT SOURCE FILES]")
        sourceFiles.entries.take(12).forEach { (path, code) ->
            appendLine("--- FILE: $path ---")
            appendLine(code.take(3000))
            appendLine("--- END FILE ---")
            appendLine()
        }
        appendLine("[INSTRUCTION]")
        appendLine("Analyze the failure diagnostic and stack trace. Provide the complete corrected file content for the file(s) that need fixing.")
        appendLine("Format every modified or created file strictly as:")
        appendLine("--- FILE: <relative_path> ---")
        appendLine("<complete code>")
        appendLine("--- END FILE ---")
        appendLine("Do not include markdown chat explanations. Output only the file blocks.")
    }

    private fun applyPatch(response: String, targetDir: Path): List<String> {
        val patched = mutableListOf<String>()
        val fileBlockRegex = Regex("--- FILE:\\s*([^\\n\\r]+)\\s*---[\\r\\n]+([\\s\\S]*?)[\\r\\n]+--- END FILE ---")
        val matches = fileBlockRegex.findAll(response).toList()

        for (match in matches) {
            val relPath = match.groupValues[1].trim().trimStart('/')
            val content = match.groupValues[2].trim()
            if (relPath.isNotBlank() && content.isNotBlank()) {
                val dest = targetDir.resolve(relPath)
                dest.parent?.let { Files.createDirectories(it) }
                Files.writeString(dest, content)
                patched.add(relPath)
            }
        }

        if (patched.isEmpty()) {
            val codeBlockRegex = Regex("```(?:[a-zA-Z0-9_-]+)?[:\\s]*([a-zA-Z0-9_./-]+\\.[a-zA-Z0-9]+)?[\\r\\n]+([\\s\\S]*?)```")
            for (match in codeBlockRegex.findAll(response)) {
                var filePath = match.groupValues[1].trim()
                val code = match.groupValues[2].trim()
                if (filePath.isBlank()) {
                    val firstLine = code.lineSequence().firstOrNull().orEmpty().trim()
                    if (firstLine.startsWith("#") || firstLine.startsWith("//")) {
                        val candidate = firstLine.trimStart('#', '/', ' ').trim()
                        if (candidate.contains(".") && !candidate.contains(" ")) {
                            filePath = candidate
                        }
                    }
                }
                if (filePath.isNotBlank() && code.isNotBlank()) {
                    val dest = targetDir.resolve(filePath)
                    dest.parent?.let { Files.createDirectories(it) }
                    Files.writeString(dest, code)
                    patched.add(filePath)
                }
            }
        }

        return patched
    }
}
