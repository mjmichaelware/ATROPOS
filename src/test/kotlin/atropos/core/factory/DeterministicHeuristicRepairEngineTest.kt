/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.factory

import atropos.core.dag.DagNode
import atropos.core.dag.DagNodeAction
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeterministicHeuristicRepairEngineTest {

    @Test
    fun repairs_missing_init_py_in_subdirectories() {
        val tempDir = createTempDirectory("heuristic-init-test")
        try {
            val subpkg = tempDir.resolve("pkg")
            Files.createDirectories(subpkg)
            val modulePy = subpkg.resolve("module.py")
            Files.writeString(modulePy, "def hello(): return 'world'\n")

            val failedNode = DagNode(
                id = "node-verify-suite",
                dagId = "dag-test",
                label = "Verify test suite",
                action = DagNodeAction.RUN_COMMAND,
                actionPayload = "python3 -c 'import pkg.module; assert pkg.module.hello() == \"world\"'"
            )

            val outcome = DeterministicHeuristicRepairEngine.attemptDeterministicRepair(
                targetDir = tempDir,
                failedNode = failedNode,
                errorLog = "ModuleNotFoundError: No module named 'pkg'"
            )

            assertTrue(outcome.repaired)
            assertEquals("RULE_MISSING_INIT_PY", outcome.ruleApplied)
            assertTrue(Files.exists(subpkg.resolve("__init__.py")))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun repairs_missing_standard_library_import_on_name_error() {
        val tempDir = createTempDirectory("heuristic-import-test")
        try {
            val scriptPy = tempDir.resolve("script.py")
            val code = """
                def get_cwd():
                    return os.getcwd()
            """.trimIndent()
            Files.writeString(scriptPy, code)

            val failedNode = DagNode(
                id = "node-verify-suite",
                dagId = "dag-test",
                label = "Verify test suite",
                action = DagNodeAction.RUN_COMMAND,
                actionPayload = "python3 -c 'import script; script.get_cwd()'"
            )

            val trace = """
                Traceback (most recent call last):
                  File "script.py", line 2, in get_cwd
                    return os.getcwd()
                NameError: name 'os' is not defined
            """.trimIndent()

            val outcome = DeterministicHeuristicRepairEngine.attemptDeterministicRepair(
                targetDir = tempDir,
                failedNode = failedNode,
                errorLog = trace
            )

            assertTrue(outcome.repaired)
            assertTrue(outcome.ruleApplied!!.contains("RULE_MISSING_STDLIB_IMPORT"))
            val content = Files.readString(scriptPy)
            assertTrue(content.contains("import os"))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun repairs_line_continuation_syntax_error() {
        val tempDir = createTempDirectory("heuristic-linecont-test")
        try {
            val scriptPy = tempDir.resolve("calc.py")
            // Code with escaped quotes that cause Python SyntaxError
            val code = "def greet():\n    return \\\"hello world\\\"\n"
            Files.writeString(scriptPy, code)

            val failedNode = DagNode(
                id = "node-verify-suite",
                dagId = "dag-test",
                label = "Verify test suite",
                action = DagNodeAction.RUN_COMMAND,
                actionPayload = "python3 -c 'import calc; assert calc.greet() == \"hello world\"'"
            )

            val trace = "SyntaxError: unexpected character after line continuation character"

            val outcome = DeterministicHeuristicRepairEngine.attemptDeterministicRepair(
                targetDir = tempDir,
                failedNode = failedNode,
                errorLog = trace
            )

            assertTrue(outcome.repaired)
            assertEquals("RULE_SYNTAX_ERROR_LINE_CONTINUATION", outcome.ruleApplied)
            val content = Files.readString(scriptPy)
            assertEquals("def greet():\n    return \"hello world\"\n", content)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    @Test
    fun repairs_syntax_error_inside_isolated_staging_worktree() {
        val tempDir = createTempDirectory("heuristic-staging-worktree-test")
        try {
            val stagingDir = tempDir.resolve(".atropos/staging/node-verify-1").createDirectories()
            val scriptPy = stagingDir.resolve("math_util.py")
            val code = "def add(a, b):\n    \\\"\\\"\\\"Add two numbers.\\\"\\\"\\\"\n    return a + b\n"
            Files.writeString(scriptPy, code)

            val failedNode = DagNode(
                id = "node-verify-1",
                dagId = "dag-test",
                label = "Verify test suite",
                action = DagNodeAction.RUN_COMMAND,
                actionPayload = "python3 -c 'import math_util; assert math_util.add(2, 3) == 5'"
            )

            // Test with brief errorLog (does not even contain the full traceback)
            val outcome = DeterministicHeuristicRepairEngine.attemptDeterministicRepair(
                targetDir = tempDir,
                failedNode = failedNode,
                errorLog = "verification failed"
            )

            assertTrue(outcome.repaired)
            assertEquals("RULE_SYNTAX_ERROR_LINE_CONTINUATION", outcome.ruleApplied)
            val content = Files.readString(scriptPy)
            assertFalse(content.contains("\\\""))
            assertTrue(content.contains("\"\"\"Add two numbers.\"\"\""))
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }
}
