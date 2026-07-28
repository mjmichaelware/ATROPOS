package atropos.core.agent

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
        val line = task.lineSequence().firstOrNull()?.trim().orEmpty()
        if (line.isBlank()) return null

        val match = Regex(
            """(?i)^create\s+(.+?)\s+containing\s+exactly\s+one\s+line:\s*(.+)$"""
        ).find(line) ?: return null
        val path = match.groupValues.getOrNull(1)?.trim().orEmpty()
        val content = match.groupValues.getOrNull(2)?.trim().orEmpty()
        if (path.isBlank() || content.isBlank()) return null
        if (path.contains("..") || path.startsWith("/") || path.startsWith("\\")) return null
        return LocalPatchRequest(path = path, content = content)
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
}
