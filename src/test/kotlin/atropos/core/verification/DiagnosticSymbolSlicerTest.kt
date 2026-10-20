/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DiagnosticSymbolSlicerTest {

    @Test
    fun parses_python_unittest_traceback_and_extracts_target() {
        val tempDir = createTempDirectory("slicer-test")
        try {
            val pyFile = tempDir.resolve("math_util.py")
            val code = """
                def calculate_sum(a, b):
                    result = a + b
                    assert result > 0, "must be positive"
                    return result
            """.trimIndent()
            Files.writeString(pyFile, code)

            val trace = """
                Traceback (most recent call last):
                  File "math_util.py", line 3, in calculate_sum
                    assert result > 0, "must be positive"
                AssertionError: must be positive
                FAIL: test_calculate_sum (test_math.TestMath)
            """.trimIndent()

            val sliced = DiagnosticSymbolSlicer.slice(tempDir, trace, "node-verify-suite")

            assertEquals("math_util.py", sliced.targetFile)
            assertEquals(3, sliced.failingLine)
            assertTrue(sliced.assertionError.contains("AssertionError") || sliced.assertionError.contains("must be positive"))
            assertTrue(sliced.codeSlice.contains("assert result > 0"))
            assertTrue(sliced.compressedPrompt.length < 1000)
            assertTrue(sliced.compressedPrompt.contains("[TARGETED ATOM REPAIR]"))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun compresses_large_stack_trace_to_under_one_thousand_characters() {
        val tempDir = createTempDirectory("slicer-large-test")
        try {
            val pyFile = tempDir.resolve("large_service.py")
            val lines = (1..200).map { "def method_$it(): return $it" }
            Files.write(pyFile, lines)

            val hugeStackTrace = buildString {
                appendLine("Traceback (most recent call last):")
                for (i in 1..80) {
                    appendLine("""  File "large_service.py", line $i, in method_$i""")
                    appendLine("    return $i")
                }
                appendLine("AssertionError: synthetic failure at line 42")
            }

            assertTrue(hugeStackTrace.length > 2000)

            val sliced = DiagnosticSymbolSlicer.slice(tempDir, hugeStackTrace, "node-verify-suite")
            assertTrue(sliced.compressedPrompt.length < 1000)
            assertNotNull(sliced.targetFile)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun handles_tracebacks_with_escaped_quotes_and_module_scope() {
        val tempDir = createTempDirectory("slicer-escaped-test")
        try {
            val pyFile = tempDir.resolve("parser.py")
            Files.writeString(pyFile, "print('valid code')\nraise ValueError('invalid syntax')\n")

            val trace = """
                Traceback (most recent call last):
                  File \"parser.py\", line 2, in <module>
                    raise ValueError('invalid syntax')
                ValueError: invalid syntax
            """.trimIndent()

            val sliced = DiagnosticSymbolSlicer.slice(tempDir, trace, "node-verify-suite")
            assertEquals("parser.py", sliced.targetFile)
            assertEquals(2, sliced.failingLine)
            assertTrue(sliced.codeSlice.isNotBlank())
            assertTrue(sliced.compressedPrompt.contains("--- FILE: parser.py ---"))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun resolves_failing_file_from_staging_directory() {
        val repoDir = createTempDirectory("slicer-staging-test")
        try {
            val stagingDir = repoDir.resolve(".atropos/staging/worktree-node-1").createDirectories()
            val pyFile = stagingDir.resolve("worker.py")
            Files.writeString(pyFile, "def run():\n    return 42 / 0\n")

            val trace = """
                Traceback (most recent call last):
                  File "/usr/lib/python3.11/unittest/case.py", line 545, in run
                    testMethod()
                  File "worker.py", line 2, in run
                    return 42 / 0
                ZeroDivisionError: division by zero
            """.trimIndent()

            val sliced = DiagnosticSymbolSlicer.slice(repoDir, trace, "verify-worker")
            assertTrue(sliced.targetFile?.endsWith("worker.py") == true)
            assertEquals(2, sliced.failingLine)
            assertTrue(sliced.codeSlice.contains("42 / 0"))
        } finally {
            repoDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun guarantees_file_contents_never_dispatched_empty_even_without_file_match() {
        val emptyDir = createTempDirectory("slicer-empty-test")
        try {
            val trace = """
                FAIL: test_something (__main__.TestSomething)
                AssertionError: unhandled case without file trace
            """.trimIndent()

            val sliced = DiagnosticSymbolSlicer.slice(emptyDir, trace, "empty-suite")
            assertTrue(sliced.codeSlice.isNotBlank())
            assertTrue(sliced.compressedPrompt.contains("[TARGETED ATOM REPAIR]"))
            assertTrue(sliced.compressedPrompt.contains("[INSTRUCTION]"))
        } finally {
            emptyDir.toFile().deleteRecursively()
        }
    }
}
