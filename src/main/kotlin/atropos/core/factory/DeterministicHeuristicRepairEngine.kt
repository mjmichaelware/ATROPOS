/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.factory

import atropos.core.dag.DagNode
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class HeuristicRepairOutcome(
    val repaired: Boolean,
    val ruleApplied: String?,
    val patchedFiles: List<String>
)

/**
 * Zero-LLM deterministic heuristic repair gate.
 * Intercepts common deterministic Python verification failures and repairs them locally
 * without dispatching calls to external AI providers.
 */
object DeterministicHeuristicRepairEngine {
    private val PYTHON_STDLIB_MODULES = setOf(
        "os", "sys", "json", "re", "math", "time", "datetime", "pathlib", "typing",
        "subprocess", "unittest", "collections", "itertools", "functools", "shutil",
        "tempfile", "copy", "random", "logging", "traceback", "io", "csv", "hashlib",
        "urllib", "socket", "threading", "multiprocessing", "uuid", "base64", "contextlib"
    )

    private val nameErrorPattern = Pattern.compile(
        """NameError:\s+name\s+['"]([A-Za-z0-9_]+)['"]\s+is\s+not\s+defined""",
        Pattern.CASE_INSENSITIVE
    )

    private val tracebackFilePattern = Pattern.compile(
        """File\s+["']([^"']+\.py)["'],\s+line\s+(\d+)""",
        Pattern.CASE_INSENSITIVE
    )

    fun attemptDeterministicRepair(
        targetDir: Path,
        failedNode: DagNode,
        errorLog: String
    ): HeuristicRepairOutcome {
        if (!Files.isDirectory(targetDir)) {
            return HeuristicRepairOutcome(false, null, emptyList())
        }

        // Rule 1: Missing __init__.py files in subdirectories
        val initOutcome = repairMissingInitPy(targetDir, failedNode)
        if (initOutcome.repaired) return initOutcome

        // Rule 2: Missing standard library imports flagged in tracebacks
        val importOutcome = repairMissingStdlibImports(targetDir, failedNode, errorLog)
        if (importOutcome.repaired) return importOutcome

        // Rule 3: Python syntax errors detected by py_compile
        val syntaxOutcome = repairSyntaxErrors(targetDir, failedNode)
        if (syntaxOutcome.repaired) return syntaxOutcome

        return HeuristicRepairOutcome(false, null, emptyList())
    }

    private fun repairMissingInitPy(targetDir: Path, failedNode: DagNode): HeuristicRepairOutcome {
        val created = mutableListOf<String>()
        val baseDir = targetDir.toFile()
        baseDir.walkTopDown().filter { it.isDirectory && it != baseDir }.forEach { dir ->
            val pathStr = dir.absolutePath
            if (!pathStr.contains(".git") && !pathStr.contains(".atropos") && !pathStr.contains("__pycache__")) {
                val hasPyFiles = dir.listFiles()?.any { it.isFile && it.name.endsWith(".py") } == true
                val initFile = File(dir, "__init__.py")
                if (hasPyFiles && !initFile.exists()) {
                    initFile.writeText("# package marker\n")
                    created.add(targetDir.relativize(initFile.toPath()).toString().replace('\\', '/'))
                }
            }
        }

        if (created.isNotEmpty()) {
            val verified = runVerificationCommand(targetDir, failedNode)
            if (verified) {
                return HeuristicRepairOutcome(true, "RULE_MISSING_INIT_PY", created)
            }
        }
        return HeuristicRepairOutcome(false, null, emptyList())
    }

    private fun repairMissingStdlibImports(
        targetDir: Path,
        failedNode: DagNode,
        errorLog: String
    ): HeuristicRepairOutcome {
        val nameMatcher = nameErrorPattern.matcher(errorLog)
        if (!nameMatcher.find()) {
            return HeuristicRepairOutcome(false, null, emptyList())
        }

        val missingName = nameMatcher.group(1)
        if (missingName !in PYTHON_STDLIB_MODULES) {
            return HeuristicRepairOutcome(false, null, emptyList())
        }

        val targetPath = extractTargetPythonFile(targetDir, errorLog)
            ?: return HeuristicRepairOutcome(false, null, emptyList())

        val content = runCatching { Files.readString(targetPath) }.getOrNull()
            ?: return HeuristicRepairOutcome(false, null, emptyList())

        if (content.contains("import $missingName")) {
            return HeuristicRepairOutcome(false, null, emptyList())
        }

        val patchedContent = "import $missingName\n$content"
        val originalContent = content
        val written = runCatching {
            Files.writeString(targetPath, patchedContent)
            true
        }.getOrDefault(false)

        if (written) {
            val verified = runVerificationCommand(targetDir, failedNode)
            val relPath = targetDir.relativize(targetPath).toString().replace('\\', '/')
            if (verified) {
                return HeuristicRepairOutcome(true, "RULE_MISSING_STDLIB_IMPORT:$missingName", listOf(relPath))
            } else {
                runCatching { Files.writeString(targetPath, originalContent) }
            }
        }

        return HeuristicRepairOutcome(false, null, emptyList())
    }

    private fun repairSyntaxErrors(targetDir: Path, failedNode: DagNode): HeuristicRepairOutcome {
        val pyFiles = targetDir.toFile().walkTopDown()
            .filter { it.isFile && it.name.endsWith(".py") && !it.absolutePath.contains(".git") && !it.absolutePath.contains(".atropos") }
            .map { it.toPath() }
            .toList()

        for (file in pyFiles) {
            val compileResult = checkPyCompile(file)
            if (compileResult != null && compileResult.contains("SyntaxError")) {
                val patched = attemptColonFix(file, compileResult)
                if (patched) {
                    val verified = runVerificationCommand(targetDir, failedNode)
                    val relPath = targetDir.relativize(file).toString().replace('\\', '/')
                    if (verified) {
                        return HeuristicRepairOutcome(true, "RULE_SYNTAX_ERROR_COLON", listOf(relPath))
                    }
                }
            }
        }

        return HeuristicRepairOutcome(false, null, emptyList())
    }

    private fun checkPyCompile(file: Path): String? {
        return try {
            val pb = ProcessBuilder("python3", "-m", "py_compile", file.toAbsolutePath().toString())
            pb.redirectErrorStream(true)
            val proc = pb.start()
            val output = proc.inputStream.bufferedReader().use { it.readText() }
            val exited = proc.waitFor(10, TimeUnit.SECONDS)
            if (exited && proc.exitValue() != 0) output else null
        } catch (_: Exception) {
            null
        }
    }

    private fun attemptColonFix(file: Path, compilerOutput: String): Boolean {
        val lineMatcher = Pattern.compile("""line\s+(\d+)""").matcher(compilerOutput)
        val lineNum = if (lineMatcher.find()) lineMatcher.group(1).toIntOrNull() else null
        val lines = runCatching { Files.readAllLines(file).toMutableList() }.getOrNull() ?: return false

        if (lineNum != null && lineNum in 1..lines.size) {
            val idx = lineNum - 1
            val targetLine = lines[idx].trimEnd()
            val needsColon = listOf("def ", "class ", "if ", "elif ", "else", "for ", "while ", "try", "except", "finally")
            val matchesKeyword = needsColon.any { targetLine.trimStart().startsWith(it) }
            if (matchesKeyword && !targetLine.endsWith(":")) {
                lines[idx] = "$targetLine:"
                return runCatching {
                    Files.write(file, lines)
                    true
                }.getOrDefault(false)
            }
        }
        return false
    }

    private fun extractTargetPythonFile(targetDir: Path, errorLog: String): Path? {
        val matcher = tracebackFilePattern.matcher(errorLog)
        var candidatePath: String? = null
        while (matcher.find()) {
            val fileStr = matcher.group(1)
            if (!fileStr.contains("/usr/") && !fileStr.contains("site-packages") && !fileStr.contains("unittest/")) {
                candidatePath = fileStr
            }
        }
        if (candidatePath == null) return null
        val direct = targetDir.resolve(candidatePath).normalize()
        if (Files.isRegularFile(direct)) return direct

        val fileName = Path.of(candidatePath).fileName.toString()
        return targetDir.toFile().walkTopDown().maxDepth(5).firstOrNull { it.isFile && it.name == fileName }?.toPath()
    }

    private fun runVerificationCommand(targetDir: Path, failedNode: DagNode): Boolean {
        val command = failedNode.actionPayload?.takeIf { it.isNotBlank() } ?: "python3 -m unittest"
        return try {
            val pb = ProcessBuilder("bash", "-c", command)
            pb.directory(targetDir.toFile())
            pb.redirectErrorStream(true)
            val proc = pb.start()
            val finished = proc.waitFor(30, TimeUnit.SECONDS)
            finished && proc.exitValue() == 0
        } catch (_: Exception) {
            false
        }
    }
}
