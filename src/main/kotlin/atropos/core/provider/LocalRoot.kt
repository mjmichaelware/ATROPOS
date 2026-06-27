package atropos.core.provider

import java.io.File
import java.time.Instant

data class LocalProbeResult(val id: String, val available: Boolean, val summary: String, val details: String = "")

class LocalToolchainProbe(private val workspace: File = File(".")) {
    fun probeKotlinc(): LocalProbeResult = runCommand("kotlinc", "-version").toProbe("local.kotlinc", "Kotlin compiler")
    fun probeGit(): LocalProbeResult = runCommand("git", "status", "--short").toProbe("local.git", "Git status")
    fun probeWorkspace(): LocalProbeResult {
        val src = File(workspace, "src/main/kotlin")
        val count = if (src.exists()) src.walkTopDown().count { it.isFile && it.extension == "kt" } else 0
        return LocalProbeResult("local.workspace", src.exists(), if (src.exists()) "workspace source present" else "workspace source missing", "$count Kotlin files")
    }
    fun probeAll(): List<LocalProbeResult> = listOf(probeWorkspace(), probeKotlinc(), probeGit())
    private fun runCommand(vararg command: String): CommandProbe =
        try {
            val p = ProcessBuilder(*command).directory(workspace).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText().trim()
            CommandProbe(p.waitFor() == 0, out.take(240))
        } catch (failure: Exception) {
            CommandProbe(false, failure.message ?: failure.javaClass.simpleName)
        }
    private fun CommandProbe.toProbe(id: String, label: String) =
        LocalProbeResult(id, ok, if (ok) "$label available" else "$label unavailable", ProviderRedactor.redact(output))
    private data class CommandProbe(val ok: Boolean, val output: String)
}

class LocalStateStore(private val root: File = File(System.getProperty("user.home"), ".atropos/state")) {
    init { root.mkdirs() }
    fun appendEvent(stream: String, line: String) {
        val safe = stream.replace(Regex("""[^A-Za-z0-9_.-]"""), "_")
        File(root, "$safe.jsonl").appendText("""{"ts":"${Instant.now()}","event":"${ProviderRedactor.redact(line)}"}""" + "\n")
    }
    fun readTail(stream: String, maxLines: Int = 40): List<String> {
        val safe = stream.replace(Regex("""[^A-Za-z0-9_.-]"""), "_")
        val file = File(root, "$safe.jsonl")
        if (!file.exists()) return emptyList()
        return file.readLines().takeLast(maxLines)
    }
    fun health() = LocalProbeResult("local.state", root.exists() && root.isDirectory, "local state root", root.absolutePath)
}
