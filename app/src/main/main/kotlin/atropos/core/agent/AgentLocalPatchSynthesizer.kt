package atropos.core.agent

import java.nio.charset.StandardCharsets

class AgentLocalPatchSynthesizer(
    private val patchStore: AgentPatchStore
) {
    fun synthesize(task: String): AgentPatchRunResult? {
        val request = parseLocalCreateTask(task) ?: return null
        val diff = buildCreateFileDiff(request.path, request.content)
        val normalizedDiff = patchStore.normalizeProviderDiff(diff)
        val record = patchStore.createRecord(
            provider = "local_fallback",
            task = task,
            contextBytes = 0,
            diff = normalizedDiff
        )
        val check = patchStore.runGitApplyCheck(record.diffFile)
        patchStore.writeMeta(record, check)

        return AgentPatchRunResult(
            providerName = "local_fallback",
            contextByteCount = 0,
            diffByteCount = record.diffBytes,
            patchId = record.id,
            patchPath = record.diffFile,
            checkResult = check,
            failureSummary = if (check.passed) null else "local fallback patch did not pass git apply --check",
            message = if (check.passed) null else "local fallback patch failed validation"
        )
    }

    private data class LocalPatchRequest(
        val path: String,
        val content: String
    )

    private fun parseLocalCreateTask(task: String): LocalPatchRequest? {
        val normalizedTask = task.replace("\r\n", "\n").replace('\r', '\n')
        val lines = normalizedTask.lineSequence().toList()
        val line = lines.firstOrNull()?.trim().orEmpty()
        if (line.isBlank()) return null

        val oneLineMatch = Regex(
            """(?i)^create\s+(.+?)\s+containing\s+exactly\s+one\s+line:\s*(.+)$"""
        ).find(line)
        val path: String
        val content: String
        if (oneLineMatch != null) {
            path = oneLineMatch.groupValues.getOrNull(1)?.trim().orEmpty()
            content = oneLineMatch.groupValues.getOrNull(2)?.trim().orEmpty()
        } else {
            val multiLineMatch = Regex(
                """(?i)^create\s+(.+?)\s+containing\s*:\s*$"""
            ).find(line) ?: return null
            path = multiLineMatch.groupValues.getOrNull(1)?.trim().orEmpty()
            content = lines.drop(1).joinToString("\n").trimEnd()
        }

        if (!safeRequest(path, content)) return null
        return LocalPatchRequest(path = path, content = content)
    }

    private fun safeRequest(path: String, content: String): Boolean {
        if (path.isBlank() || content.isBlank()) return false
        if (path.contains("..") || path.contains('\u0000') || path.startsWith("/") || path.startsWith("\\")) {
            return false
        }
        if (content.contains('\u0000')) return false
        if (content.lineSequence().count() > MAX_LOCAL_LINES) return false
        return content.toByteArray(StandardCharsets.UTF_8).size <= MAX_LOCAL_CONTENT_BYTES
    }

    private fun buildCreateFileDiff(path: String, content: String): String {
        val lines = content.replace("\r\n", "\n").replace('\r', '\n').lineSequence().toList()
        val safeLines = if (lines.isEmpty()) listOf("") else lines
        return buildString {
            appendLine("--- /dev/null")
            appendLine("+++ b/$path")
            appendLine("@@ -0,0 +1,${safeLines.size} @@")
            safeLines.forEach { line -> appendLine("+$line") }
        }.trimEnd() + "\n"
    }

    private companion object {
        const val MAX_LOCAL_LINES = 2_048
        const val MAX_LOCAL_CONTENT_BYTES = 64 * 1024
    }
}
