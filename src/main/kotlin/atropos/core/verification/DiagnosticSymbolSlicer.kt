/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

import java.nio.file.Files
import java.nio.file.Path
import java.util.regex.Pattern

data class SlicedDiagnostic(
    val targetFile: String?,
    val failingLine: Int?,
    val assertionError: String,
    val codeSlice: String,
    val compressedPrompt: String
)

/**
 * Distills raw test suite failures down to isolated symbol slices and high-density prompts.
 * Prevents token overflow by pruning stack traces and slicing the target source code
 * down to the enclosing function or class scope (+/- 25 lines).
 */
object DiagnosticSymbolSlicer {
    private const val SLICE_WINDOW = 25
    private const val MAX_PROMPT_CHARS = 980

    private val tracebackPattern = Pattern.compile(
        """File\s+["'\\]*([^"'\\\r\n]+?)["'\\]*,\s*line\s*(\d+)(?:,\s*in\s*([^\r\n]+))?""",
        Pattern.CASE_INSENSITIVE
    )

    private val fallbackPyPattern = Pattern.compile(
        """(?:[Ff]ile\s+)?["'\\]*([A-Za-z0-9_\-./\\]+\.py)["'\\]*(?:,\s*line\s*(\d+))?""",
        Pattern.CASE_INSENSITIVE
    )

    private val assertionPattern = Pattern.compile(
        """(?:AssertionError|Exception|Error|FAIL):\s*(.+)""",
        Pattern.CASE_INSENSITIVE
    )

    fun slice(
        targetDir: Path,
        errorLog: String,
        failedNodeLabel: String? = null
    ): SlicedDiagnostic {
        val parsed = parseDiagnosticTrace(errorLog)
        val targetPath = resolveTargetFilePath(targetDir, parsed.filePath)
        val failingLine = parsed.lineNumber

        val (relativeFile, codeSlice) = if (targetPath != null && Files.isRegularFile(targetPath)) {
            val rel = try {
                targetDir.relativize(targetPath).toString().replace('\\', '/')
            } catch (_: Exception) {
                targetPath.fileName.toString()
            }
            val slice = extractCodeSlice(targetPath, failingLine)
            val finalSlice = if (slice.isNotBlank()) {
                slice
            } else {
                runCatching { Files.readString(targetPath) }.getOrDefault("")
            }
            rel to finalSlice
        } else {
            val file = parsed.filePath?.let { Path.of(it).fileName.toString() } ?: "unknown_file.py"
            val fallbackSource = buildString {
                appendLine("# Target source file: $file")
                appendLine("# Diagnostic failure: ${parsed.assertionError}")
                if (failingLine != null) {
                    appendLine("# Failing line: $failingLine")
                }
            }
            file to fallbackSource
        }

        val compressedPrompt = buildCompressedPrompt(
            failedLabel = failedNodeLabel ?: "test-suite",
            relativeFile = relativeFile,
            failingLine = failingLine,
            assertionError = parsed.assertionError,
            codeSlice = codeSlice
        )

        return SlicedDiagnostic(
            targetFile = relativeFile,
            failingLine = failingLine,
            assertionError = parsed.assertionError,
            codeSlice = codeSlice,
            compressedPrompt = compressedPrompt
        )
    }

    private data class ParsedTrace(
        val filePath: String?,
        val lineNumber: Int?,
        val functionName: String?,
        val assertionError: String
    )

    private fun isSystemLibrary(path: String): Boolean {
        val normalized = path.replace('\\', '/').lowercase()
        return normalized.contains("/usr/") ||
            normalized.contains("/lib/python") ||
            normalized.contains("/lib64/python") ||
            normalized.contains("site-packages") ||
            normalized.contains("/dist-packages") ||
            normalized.contains("<frozen") ||
            normalized.contains("<string>") ||
            normalized.endsWith("/unittest/case.py") ||
            normalized.endsWith("/unittest/runner.py") ||
            normalized.endsWith("/unittest/loader.py") ||
            normalized.endsWith("/unittest/suite.py")
    }

    private fun parseDiagnosticTrace(errorLog: String): ParsedTrace {
        var lastFile: String? = null
        var lastLine: Int? = null
        var lastFunc: String? = null
        var assertionMsg = "Assertion or test execution failure"

        var fallbackFile: String? = null
        var fallbackLine: Int? = null

        val matcher = tracebackPattern.matcher(errorLog)
        while (matcher.find()) {
            val rawCandidate = matcher.group(1) ?: continue
            val candidateFile = rawCandidate.trim().trim('\"', '\'', '\\')
            val candidateLine = matcher.group(2)?.toIntOrNull()
            val candidateFunc = matcher.group(3)?.trim()

            fallbackFile = candidateFile
            fallbackLine = candidateLine

            if (!isSystemLibrary(candidateFile)) {
                lastFile = candidateFile
                lastLine = candidateLine
                lastFunc = candidateFunc
            }
        }

        if (lastFile == null && fallbackFile != null) {
            lastFile = fallbackFile
            lastLine = fallbackLine
        }

        if (lastFile == null) {
            val fbMatcher = fallbackPyPattern.matcher(errorLog)
            while (fbMatcher.find()) {
                val candidate = fbMatcher.group(1)?.trim()?.trim('\"', '\'', '\\')
                if (!candidate.isNullOrBlank() && !isSystemLibrary(candidate)) {
                    lastFile = candidate
                    lastLine = fbMatcher.group(2)?.toIntOrNull()
                }
            }
        }

        val assertMatcher = assertionPattern.matcher(errorLog)
        if (assertMatcher.find()) {
            assertionMsg = assertMatcher.group(0).lines().first().trim()
        } else {
            val failLine = errorLog.lines().firstOrNull {
                it.contains("FAIL:") || it.contains("ERROR:") || it.contains("AssertionError")
            }
            if (failLine != null) {
                assertionMsg = failLine.trim()
            }
        }

        return ParsedTrace(lastFile, lastLine, lastFunc, assertionMsg)
    }

    private fun resolveTargetFilePath(targetDir: Path, candidate: String?): Path? {
        val searchDirs = LinkedHashSet<Path>()
        searchDirs.add(targetDir)

        val localStaging = targetDir.resolve(".atropos/staging")
        if (Files.isDirectory(localStaging)) {
            searchDirs.add(localStaging)
        }

        var curr: Path? = targetDir.toAbsolutePath().normalize()
        for (i in 0..4) {
            if (curr == null) break
            val staging = curr.resolve(".atropos/staging")
            if (Files.isDirectory(staging)) {
                searchDirs.add(staging)
            }
            curr = curr.parent
        }

        if (!candidate.isNullOrBlank()) {
            val cleanCandidate = candidate.trim().trim('\"', '\'', '\\').replace('\\', '/')
            val directPath = Path.of(cleanCandidate)
            if (directPath.isAbsolute && Files.isRegularFile(directPath)) {
                return directPath
            }

            for (dir in searchDirs) {
                val direct = dir.resolve(cleanCandidate).normalize()
                if (Files.isRegularFile(direct)) return direct
            }

            val simpleName = Path.of(cleanCandidate).fileName.toString()
            for (dir in searchDirs) {
                val found = dir.toFile().walkTopDown().maxDepth(6)
                    .firstOrNull { it.isFile && it.name == simpleName }
                    ?.toPath()
                if (found != null) return found
            }
        }

        // Under NO circumstances should unknown_file.py be dispatched without file contents.
        for (dir in searchDirs) {
            val anyPy = dir.toFile().walkTopDown().maxDepth(6)
                .filter { it.isFile && it.extension == "py" && !it.name.startsWith("test_") }
                .firstOrNull()?.toPath()
                ?: dir.toFile().walkTopDown().maxDepth(6)
                    .filter { it.isFile && it.extension == "py" }
                    .firstOrNull()?.toPath()
            if (anyPy != null) return anyPy
        }

        return null
    }

    private fun extractCodeSlice(path: Path, failingLine: Int?): String {
        val lines = runCatching { Files.readAllLines(path) }.getOrNull() ?: return ""
        if (lines.isEmpty()) return ""

        val (boundStart, boundEnd) = AutonomousDiagnosticSlicingEngine.calculateWindowBounds(failingLine, lines.size)
        val start = (boundStart - 1).coerceAtLeast(0)
        val end = (boundEnd - 1).coerceAtMost(lines.size - 1)

        val sb = StringBuilder()
        for (i in start..end) {
            val lineNum = i + 1
            val prefix = if (lineNum == failingLine) ">" else " "
            sb.append(String.format("%s%4d | %s\n", prefix, lineNum, lines[i]))
        }
        return sb.toString()
    }

    private fun buildCompressedPrompt(
        failedLabel: String,
        relativeFile: String,
        failingLine: Int?,
        assertionError: String,
        codeSlice: String
    ): String {
        val header = "[TARGETED ATOM REPAIR]\n" +
            "Failure: $failedLabel\n" +
            "File: $relativeFile${failingLine?.let { ":$it" } ?: ""}\n" +
            "Diagnostic: ${assertionError.take(180)}\n\n"

        val instruction = "\n[INSTRUCTION]\n" +
            "Output the complete fixed file with delimiter format:\n" +
            "--- FILE: $relativeFile ---\n" +
            "<corrected_code>\n" +
            "--- END FILE ---\n"

        val remainingBudget = (MAX_PROMPT_CHARS - header.length - instruction.length).coerceAtLeast(100)
        val formattedSlice = if (codeSlice.isNotBlank()) {
            "[FOCUSED SCOPE SLICE (+/- 25 lines)]\n" + codeSlice.take(remainingBudget)
        } else {
            ""
        }

        val full = header + formattedSlice + instruction
        return if (full.length > MAX_PROMPT_CHARS) full.take(MAX_PROMPT_CHARS) else full
    }
}
