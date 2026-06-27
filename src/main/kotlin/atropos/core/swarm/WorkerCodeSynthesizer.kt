package atropos.core.swarm

import atropos.core.AIProvider
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class WorkerCodeSynthesizer(private val workerNode: AIProvider) {

    private val executor = Executors.newFixedThreadPool(
        Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
    )

    fun synthesizeConcurrently(
        filePaths: List<String>,
        rootWorkspace: File,
        globalContext: String
    ): List<String> {
        require(rootWorkspace.exists() || rootWorkspace.mkdirs()) {
            "Workspace root is unavailable: ${rootWorkspace.absolutePath}"
        }

        val tasks = filePaths.distinct().map { path ->
            Callable<String> {
                val prompt = """
                    Write the complete Kotlin source file for: $path
                    Output ONLY code.
                    No markdown fences.
                    Include package declaration.
                    Keep it compatible with Kotlin/JVM on Termux.
                """.trimIndent()

                val raw = workerNode.complete(prompt, globalContext)
                val clean = sanitizeModelOutput(raw)

                val target = File(rootWorkspace, path)
                target.parentFile?.mkdirs()
                target.writeText(clean)

                "NODE_SUCCESS: $path"
            }
        }

        val futures = executor.invokeAll(tasks)
        return futures.map { it.get() }
    }

    fun shutdown(timeoutSeconds: Long = 5) {
        executor.shutdown()
        if (!executor.awaitTermination(timeoutSeconds, TimeUnit.SECONDS)) {
            executor.shutdownNow()
        }
    }

    internal fun sanitizeModelOutput(raw: String): String {
        var text = raw.trim()

        if (text.startsWith("```")) {
            text = text.removePrefix("```kotlin")
                .removePrefix("```kt")
                .removePrefix("```")
                .trim()
        }
        if (text.endsWith("```")) {
            text = text.removeSuffix("```").trim()
        }

        return text
    }
}
