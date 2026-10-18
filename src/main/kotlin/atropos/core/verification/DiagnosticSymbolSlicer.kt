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
        """File\s+["']([^"']+)["'],\s+line\s+(\d+)(?:,\s+in\s+([A-Za-z0-9_]+))?""",
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

        val codeSlice = if (targetPath != null && Files.isRegularFile(targetPath)) {
            extractCodeSlice(targetPath, failingLine)
        } else {
            ""
        }

        val relativeFile = targetPath?.let {
            targetDir.relativize(it).toString().replace('\\', '/')
        } ?: parsed.filePath ?: "unknown_file.py"

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

    private fun parseDiagnosticTrace(errorLog: String): ParsedTrace {
        var lastFile: String? = null
        var lastLine: Int? = null
        var lastFunc: String? = null
        var assertionMsg = "Assertion or test execution failure"

        val matcher = tracebackPattern.matcher(errorLog)
        while (matcher.find()) {
            val candidateFile = matcher.group(1)
            val candidateLine = matcher.group(2)?.toIntOrNull()
            val candidateFunc = matcher.group(3)

            if (!candidateFile.contains("/usr/") && !candidateFile.contains("site-packages") &&
                !candidateFile.contains("unittest/") && !candidateFile.contains("pytest")
            ) {
                lastFile = candidateFile
                lastLine = candidateLine
                lastFunc = candidateFunc
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
        if (candidate.isNullOrBlank()) return null
        val direct = targetDir.resolve(candidate).normalize()
        if (Files.isRegularFile(direct)) return direct

        val simpleName = Path.of(candidate).fileName.toString()
        return runCatching {
            Files.walk(targetDir, 5).use { stream ->
                stream.filter { Files.isRegularFile(it) && it.fileName.toString() == simpleName }
                    .findFirst()
                    .orElse(null)
            }
        }.getOrNull()
    }

    private fun extractCodeSlice(path: Path, failingLine: Int?): String {
        val lines = runCatching { Files.readAllLines(path) }.getOrNull() ?: return ""
        if (lines.isEmpty()) return ""

        val center = if (failingLine != null && failingLine in 1..lines.size) {
            failingLine - 1
        } else {
            0
        }

        val start = (center - SLICE_WINDOW).coerceAtLeast(0)
        val end = (center + SLICE_WINDOW).coerceAtMost(lines.size - 1)

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
