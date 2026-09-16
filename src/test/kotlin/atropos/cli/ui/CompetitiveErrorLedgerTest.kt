/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class CompetitiveErrorLedgerTest {

    @Test
    fun `record and summary tracks errors by severity and category`() {
        val ledger = CompetitiveErrorLedger()

        ledger.recordParseError("missing bracket", "add closing bracket")
        ledger.recordExecutionError("command not found", "check PATH")
        ledger.recordVerificationError("gate failed", "review evidence")
        ledger.recordProviderError("API timeout", "retry with backoff")
        ledger.recordTerritoryError("access denied", "request permission")
        ledger.recordConfigError("invalid value", "use valid range")
        ledger.recordNetworkError("connection refused", "check host")
        ledger.recordInternalError("null pointer", "report bug")

        val summary = ledger.summary()

        assertEquals(8, summary.totalErrors)
        assertEquals(1, summary.bySeverity[CompetitiveErrorLedger.Severity.CRITICAL] ?: 0)
        assertEquals(6, summary.bySeverity[CompetitiveErrorLedger.Severity.HIGH] ?: 0)
        assertEquals(1, summary.bySeverity[CompetitiveErrorLedger.Severity.MEDIUM] ?: 0)
        assertEquals(0, summary.bySeverity[CompetitiveErrorLedger.Severity.LOW] ?: 0)

        assertEquals(1, summary.byCategory[CompetitiveErrorLedger.Category.PARSE] ?: 0)
        assertEquals(1, summary.byCategory[CompetitiveErrorLedger.Category.EXECUTION] ?: 0)
        assertEquals(1, summary.byCategory[CompetitiveErrorLedger.Category.VERIFICATION] ?: 0)
        assertEquals(1, summary.byCategory[CompetitiveErrorLedger.Category.PROVIDER] ?: 0)
        assertEquals(1, summary.byCategory[CompetitiveErrorLedger.Category.TERRITORY] ?: 0)
        assertEquals(1, summary.byCategory[CompetitiveErrorLedger.Category.CONFIG] ?: 0)
        assertEquals(1, summary.byCategory[CompetitiveErrorLedger.Category.NETWORK] ?: 0)
        assertEquals(1, summary.byCategory[CompetitiveErrorLedger.Category.INTERNAL] ?: 0)
    }

    @Test
    fun `every recorded error has a remediation`() {
        val ledger = CompetitiveErrorLedger()

        ledger.recordParseError("missing bracket", "add closing bracket")
        ledger.recordExecutionError("command not found", "check PATH")

        CompetitiveErrorLedgerTestUtils.assertRemediationCompleteness(ledger)
    }

    @Test
    fun `severity consistency within category`() {
        val ledger = CompetitiveErrorLedger()

        ledger.recordParseError("error 1", "fix 1")
        ledger.recordParseError("error 2", "fix 2")

        CompetitiveErrorLedgerTestUtils.assertSeverityConsistency(ledger)
    }

    @Test
    fun `secret redaction prevents leaks`() {
        val redactionFilter = RedactionFilter()
        val ledger = CompetitiveErrorLedger(redactionFilter)

        ledger.recordExecutionError("api key sk-1234567890abcdef", "rotate key")
        ledger.recordParseError("password secret123", "use vault")

        CompetitiveErrorLedgerTestUtils.assertSecretRedaction(ledger, redactionFilter)
    }

    @Test
    fun `competitive benchmark compares against baseline`() {
        val ledger = CompetitiveErrorLedger()

        ledger.recordParseError("parse error", "fix")
        ledger.recordExecutionError("exec error", "fix")
        ledger.recordExecutionError("exec error 2", "fix")

        val baseline = mapOf(
            CompetitiveErrorLedger.Category.PARSE to 1,
            CompetitiveErrorLedger.Category.EXECUTION to 2,
        )

        val benchmark = CompetitiveErrorLedgerTestUtils.competitiveBenchmark(ledger, baseline)

        assertEquals(3, benchmark["total_errors"])
        assertEquals(mapOf("CRITICAL" to 0, "HIGH" to 2, "MEDIUM" to 1, "LOW" to 0), benchmark["severity_distribution"])
        assertEquals(0, benchmark["vs_baseline"]?.get(CompetitiveErrorLedger.Category.PARSE))
        assertEquals(0, benchmark["vs_baseline"]?.get(CompetitiveErrorLedger.Category.EXECUTION))
    }
}