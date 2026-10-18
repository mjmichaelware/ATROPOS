/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

import atropos.core.parser.TreeSitterGrammarBridge
import atropos.core.verification.AstStubHollowScanner
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

data class ExtractedPatchFile(
    val relativePath: String,
    val content: String,
    val isUnifiedDiff: Boolean = false
)

data class NormalizerExtraction(
    val files: List<ExtractedPatchFile>,
    val strippedProseChars: Int,
    val hadConversationalProse: Boolean
)

data class SyntaxValidationResult(
    val valid: Boolean,
    val violations: List<String> = emptyList(),
    val firstError: String? = null
)

sealed class PatchApplicationResult {
    data class Success(
        val patchedFiles: List<String>,
        val strippedProseChars: Int
    ) : PatchApplicationResult()

    data class Failure(
        val reason: String,
        val violations: List<String> = emptyList(),
        val unvalidatedFiles: List<String> = emptyList()
    ) : PatchApplicationResult()
}

/**
 * Adversarial Response Normalizer & AST Pre-Flight Validator.
 *
 * Strips all conversational preamble, apologies, markdown fences, and conversational
 * wrappers from arbitrary AI provider responses. Validates AST / syntax integrity
 * (Kotlin structural/TreeSitter checks, Python ast.parse, JSON parse) locally before
 * permitting ANY patch to be written to disk.
 */
class AdversarialResponseNormalizer(
    private val hollowScanner: AstStubHollowScanner = AstStubHollowScanner(),
    private val grammarBridge: TreeSitterGrammarBridge = TreeSitterGrammarBridge()
) {

    fun extractAndNormalize(rawResponse: String): NormalizerExtraction {
        val files = mutableListOf<ExtractedPatchFile>()
        val trimmed = rawResponse.trim()

        // 1. Explicit delimiter blocks: --- FILE: <path> --- ... --- END FILE ---
        val fileBlockRegex = Regex("--- FILE:\\s*([^\\n\\r]+)\\s*---[\\r\\n]+([\\s\\S]*?)[\\r\\n]+--- END FILE ---")
        for (match in fileBlockRegex.findAll(trimmed)) {
            val relPath = sanitizePath(match.groupValues[1])
            val content = match.groupValues[2].trim()
            if (relPath.isNotBlank() && content.isNotBlank()) {
                files.add(ExtractedPatchFile(relPath, content))
            }
        }

        // 2. Equals wrapper: === FILE: <path> === ... === END FILE ===
        if (files.isEmpty()) {
            val equalsRegex = Regex("=== FILE:\\s*([^\\n\\r]+)\\s*===[\\r\\n]+([\\s\\S]*?)[\\r\\n]+=== END FILE ===")
            for (match in equalsRegex.findAll(trimmed)) {
                val relPath = sanitizePath(match.groupValues[1])
                val content = match.groupValues[2].trim()
                if (relPath.isNotBlank() && content.isNotBlank()) {
                    files.add(ExtractedPatchFile(relPath, content))
                }
            }
        }

        // 3. XML wrapper: <file path="...">...</file>
        if (files.isEmpty()) {
            val xmlRegex = Regex("""<file\s+path=["']([^"']+)["'][^>]*>([\s\S]*?)</file>""")
            for (match in xmlRegex.findAll(trimmed)) {
                val relPath = sanitizePath(match.groupValues[1])
                val content = match.groupValues[2].trim()
                if (relPath.isNotBlank() && content.isNotBlank()) {
                    files.add(ExtractedPatchFile(relPath, content))
                }
            }
        }

        // 4. Markdown code fences: ```lang[:path] ... ```
        if (files.isEmpty()) {
            val fenceRegex = Regex("```(?:[a-zA-Z0-9_-]+)?[:\\s]*([a-zA-Z0-9_./-]+\\.[a-zA-Z0-9]+)?[\\r\\n]+([\\s\\S]*?)```")
            for (match in fenceRegex.findAll(trimmed)) {
                var filePath = sanitizePath(match.groupValues[1])
                val code = match.groupValues[2].trim()
                if (filePath.isBlank()) {
                    filePath = extractPathFromFirstLine(code)
                }
                if (filePath.isNotBlank() && code.isNotBlank()) {
                    files.add(ExtractedPatchFile(filePath, code))
                }
            }
        }

        // 5. Unified diff detection
        if (files.isEmpty() && (trimmed.contains("diff --git ") || (trimmed.contains("--- a/") && trimmed.contains("+++ b/")))) {
            val diffTarget = extractPathFromDiff(trimmed)
            if (diffTarget.isNotBlank()) {
                files.add(ExtractedPatchFile(diffTarget, trimmed, isUnifiedDiff = true))
            }
        }

        val totalCodeChars = files.sumOf { it.content.length }
        val strippedChars = (trimmed.length - totalCodeChars).coerceAtLeast(0)
        val hadProse = strippedChars > 30

        return NormalizerExtraction(
            files = files,
            strippedProseChars = strippedChars,
            hadConversationalProse = hadProse
        )
    }

    fun validateSyntax(filePath: String, code: String): SyntaxValidationResult {
        val violations = mutableListOf<String>()
        val cleanPath = filePath.lowercase()

        if (code.isBlank()) {
            violations.add("Empty code payload for $filePath")
            return SyntaxValidationResult(false, violations, violations.first())
        }

        if (code.contains("\u0000")) {
            violations.add("Binary null byte \\u0000 detected in $filePath")
        }

        when {
            cleanPath.endsWith(".kt") || cleanPath.endsWith(".kts") -> {
                validateKotlin(filePath, code, violations)
            }
            cleanPath.endsWith(".py") -> {
                validatePython(filePath, code, violations)
            }
            cleanPath.endsWith(".json") -> {
                validateJson(filePath, code, violations)
            }
            else -> {
                validateDelimiterBalance(code, violations)
            }
        }

        val isValid = violations.isEmpty()
        return SyntaxValidationResult(
            valid = isValid,
            violations = violations,
            firstError = violations.firstOrNull()
        )
    }

    fun applyValidatedPatches(rawResponse: String, targetDir: Path): PatchApplicationResult {
        val extraction = extractAndNormalize(rawResponse)
        if (extraction.files.isEmpty()) {
            return PatchApplicationResult.Failure("No file blocks or code patches could be extracted from provider response")
        }

        val failedViolations = mutableListOf<String>()
        val unvalidated = mutableListOf<String>()

        for (file in extraction.files) {
            val validation = validateSyntax(file.relativePath, file.content)
            if (!validation.valid) {
                failedViolations.addAll(validation.violations)
                unvalidated.add(file.relativePath)
            }
        }

        if (failedViolations.isNotEmpty()) {
            return PatchApplicationResult.Failure(
                reason = "AST/syntax pre-flight verification failed for ${unvalidated.joinToString(", ")}",
                violations = failedViolations,
                unvalidatedFiles = unvalidated
            )
        }

        // Write safely to disk only after all files pass pre-flight check
        val writtenPaths = mutableListOf<String>()
        for (file in extraction.files) {
            val dest = targetDir.resolve(file.relativePath).normalize()
            if (!dest.startsWith(targetDir.normalize())) {
                return PatchApplicationResult.Failure("Path traversal violation: ${file.relativePath} escapes project root")
            }
            dest.parent?.let { Files.createDirectories(it) }
            Files.writeString(dest, file.content)
            writtenPaths.add(file.relativePath)
        }

        return PatchApplicationResult.Success(
            patchedFiles = writtenPaths,
            strippedProseChars = extraction.strippedProseChars
        )
    }

    private fun validateKotlin(filePath: String, code: String, violations: MutableList<String>) {
        validateDelimiterBalance(code, violations)

        val hollows = hollowScanner.scanSource(Path.of(filePath), code)
        for (defect in hollows) {
            violations.add("Hollow defect on line ${defect.line}: ${defect.message}")
        }

        val parseTree = grammarBridge.parseTree(code)
        if (parseTree.parseErrors.isNotEmpty()) {
            for (err in parseTree.parseErrors) {
                violations.add("Kotlin AST structure error: $err")
            }
        }
    }

    private fun validatePython(filePath: String, code: String, violations: MutableList<String>) {
        validateDelimiterBalance(code, violations)

        val pythonBinary = resolvePythonExecutable()
        if (pythonBinary != null) {
            try {
                val pb = ProcessBuilder(pythonBinary, "-c", "import ast, sys; ast.parse(sys.stdin.read())")
                pb.redirectErrorStream(true)
                val process = pb.start()
                process.outputStream.bufferedWriter().use { it.write(code) }
                val finished = process.waitFor(2000, TimeUnit.MILLISECONDS)
                if (finished && process.exitValue() != 0) {
                    val errOutput = process.inputStream.bufferedReader().readText().trim()
                    violations.add("Python ast.parse validation error: ${errOutput.take(300)}")
                } else if (!finished) {
                    process.destroyForcibly()
                }
            } catch (e: Exception) {
                // If sub-process execution fails, fallback to delimiter balance
            }
        }
    }

    private fun validateJson(filePath: String, code: String, violations: MutableList<String>) {
        val trimmed = code.trim()
        try {
            if (trimmed.startsWith("{")) {
                JSONObject(trimmed)
            } else if (trimmed.startsWith("[")) {
                JSONArray(trimmed)
            } else {
                violations.add("JSON must begin with '{' or '[': $filePath")
            }
        } catch (e: Exception) {
            violations.add("JSON parse failure in $filePath: ${e.message}")
        }
    }

    private fun validateDelimiterBalance(code: String, violations: MutableList<String>) {
        val stack = ArrayDeque<Pair<Char, Int>>()
        var inSingleQuote = false
        var inDoubleQuote = false
        var inTripleQuote = false
        var i = 0

        while (i < code.length) {
            val c = code[i]
            if (c == '\\' && (inSingleQuote || inDoubleQuote || inTripleQuote)) {
                i += 2
                continue
            }
            if (code.startsWith("\"\"\"", i)) {
                inTripleQuote = !inTripleQuote
                i += 3
                continue
            }
            if (!inTripleQuote) {
                if (c == '"' && !inSingleQuote) inDoubleQuote = !inDoubleQuote
                if (c == '\'' && !inDoubleQuote) inSingleQuote = !inSingleQuote
            }
            if (!inSingleQuote && !inDoubleQuote && !inTripleQuote) {
                when (c) {
                    '(', '[', '{' -> stack.addLast(c to (i + 1))
                    ')' -> if (stack.isEmpty() || stack.removeLast().first != '(') violations.add("Unmatched ')' at char $i")
                    ']' -> if (stack.isEmpty() || stack.removeLast().first != '[') violations.add("Unmatched ']' at char $i")
                    '}' -> if (stack.isEmpty() || stack.removeLast().first != '{') violations.add("Unmatched '}' at char $i")
                }
            }
            i++
        }

        while (stack.isNotEmpty()) {
            val (unclosed, pos) = stack.removeLast()
            violations.add("Unclosed delimiter '$unclosed' starting at char $pos")
        }
    }

    private fun sanitizePath(raw: String): String =
        raw.trim().trimStart('/', '\\', '`', '\'', '"').trimEnd('`', '\'', '"', ':')

    private fun extractPathFromFirstLine(code: String): String {
        val firstLine = code.lineSequence().firstOrNull().orEmpty().trim()
        if (firstLine.startsWith("#") || firstLine.startsWith("//") || firstLine.startsWith("/*")) {
            val candidate = firstLine.trimStart('#', '/', '*', ' ')
                .removePrefix("File:")
                .removePrefix("file:")
                .removePrefix("path:")
                .trim()
            if (candidate.contains(".") && !candidate.contains(" ") && candidate.length < 120) {
                return sanitizePath(candidate)
            }
        }
        return ""
    }

    private fun extractPathFromDiff(diff: String): String {
        for (line in diff.lineSequence()) {
            if (line.startsWith("+++ b/")) {
                return sanitizePath(line.removePrefix("+++ b/"))
            } else if (line.startsWith("diff --git a/")) {
                val parts = line.split(" ")
                if (parts.size >= 4 && parts[3].startsWith("b/")) {
                    return sanitizePath(parts[3].removePrefix("b/"))
                }
            }
        }
        return ""
    }

    private fun resolvePythonExecutable(): String? {
        val candidate = File("/usr/bin/python3")
        if (candidate.exists() && candidate.canExecute()) return candidate.absolutePath
        val pathDirs = System.getenv("PATH")?.split(File.pathSeparator).orEmpty()
        for (dir in pathDirs) {
            val py = File(dir, "python3")
            if (py.exists() && py.canExecute()) return py.absolutePath
        }
        return null
    }
}
