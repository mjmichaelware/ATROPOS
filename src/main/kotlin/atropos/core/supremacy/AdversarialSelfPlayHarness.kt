package atropos.core.supremacy

data class BoundaryInputCase(val name: String, val inputString: String)

data class AdversarialReport(
    val testedCasesCount: Int,
    val survivals: Int,
    val failures: List<String>,
    val passed: Boolean
)

class AdversarialSelfPlayHarness {
    fun generateBoundaryCases(): List<BoundaryInputCase> = listOf(
        BoundaryInputCase("EMPTY_STRING", ""),
        BoundaryInputCase("WHITESPACE_SURGE", " \t \r\n  \u00A0 "),
        BoundaryInputCase("NULL_BYTE_INJECTION", "input\u0000injected"),
        BoundaryInputCase("PATH_TRAVERSAL_ESCAPE", "../../.atropos/secrets"),
        BoundaryInputCase("INTEGER_EXTREMES_MIN", Long.MIN_VALUE.toString()),
        BoundaryInputCase("INTEGER_EXTREMES_MAX", Long.MAX_VALUE.toString()),
        BoundaryInputCase("MASSIVE_STRING_64K", "A".repeat(65536)),
        BoundaryInputCase("SPECIAL_CHARS_UNICODE", "\uD83D\uDCA9\uFE0F\u200D\uD83C\uDF84\u202Ereversed"),
        BoundaryInputCase("JSON_DELIMITER_COLLAPSE", "{\"key\": \"value\"}]]]]}}}}"),
        BoundaryInputCase("COMMAND_SUBSTITUTION", "\$(rm -rf / || echo hacked)")
    )

    fun executeAdversarialTest(
        candidateRunner: (String) -> Pair<Int, String>
    ): AdversarialReport {
        val cases = generateBoundaryCases()
        val failures = mutableListOf<String>()
        var survivals = 0

        for (case in cases) {
            try {
                val (exitCode, output) = candidateRunner(case.inputString)
                if (exitCode == 0 || exitCode == 400 || exitCode == 403) {
                    survivals++
                } else if (exitCode == 139 || exitCode == 134) {
                    failures.add("${case.name}: Fatal signal crash ($exitCode)")
                } else {
                    failures.add("${case.name}: Unexpected exit code ($exitCode) Output: ${output.take(80)}")
                }
            } catch (t: Throwable) {
                failures.add("${case.name}: Exception thrown (${t.javaClass.simpleName}: ${t.message})")
            }
        }

        return AdversarialReport(
            testedCasesCount = cases.size,
            survivals = survivals,
            failures = failures,
            passed = failures.isEmpty()
        )
    }
}
