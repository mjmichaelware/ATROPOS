package atropos.cli.ui

import atropos.core.testing.AtroposTestMatrix

class TestMatrixRenderer(private val matrix: AtroposTestMatrix = AtroposTestMatrix()) {
    fun render(): String {
        val result = matrix.run()
        return buildString {
            appendLine("test matrix:")
            appendLine("  passed: ${result.passed}")
            appendLine("  passed_count: ${result.passedCount}")
            appendLine("  failed: ${result.failed}")
            result.rows.forEach { row ->
                appendLine("  ${if (row.passed) "ok" else "fail"} ${row.id} - ${row.detail}")
            }
        }
    }
}
