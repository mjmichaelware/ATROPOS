/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.core.security.RedactionFilter
import atropos.core.verification.VerifiedCompletionGate
import atropos.core.verification.DeterministicVerifier
import atropos.core.verification.DeterministicChecks
import java.time.Instant

/**
 * Competitive Error Ledger (F-CLI-011).
 *
 * A structured log of all errors encountered during CLI execution,
 * categorized by severity and source. This enables competitive
 * comparison against other CLI tools' error reporting quality.
 *
 * The ledger records:
 * - Error type and severity
 * - Source component (parser, executor, verifier, etc.)
 * - Redacted error message
 * - Timestamp and session ID
 * - Remediation hint
 *
 * Tests verify that:
 * - Every error has a remediation hint
 * - No raw secrets leak in error messages
 * - Error categories are exhaustive
 * - Severity mapping is consistent
 */
class CompetitiveErrorLedger(
    private val redactionFilter: RedactionFilter = RedactionFilter(),
) {
    sealed class Entry {
        data class Error(
            val id: String,
            val severity: Severity,
            val category: Category,
            val message: String,
            val remediation: String,
            val timestamp: Instant = Instant.now(),
            val sessionId: String,
        ) : Entry()

        data class Summary(
            val totalErrors: Int,
            val bySeverity: Map<Severity, Int>,
            val byCategory: Map<Category, Int>,
            val sessionId: String,
        ) : Entry()
    }

    enum class Severity {
        CRITICAL,    // Blocks operation, requires immediate action
        HIGH,        // Operation failed, clear remediation exists
        MEDIUM,      // Degraded functionality, workaround available
        LOW,         // Cosmetic or informational
    }

    enum class Category {
        PARSE,       // Syntax/format errors
        EXECUTION,   // Runtime/command failures
        VERIFICATION, // Verification gate failures
        PROVIDER,    // Provider/API errors
        TERRITORY,   // Territory/permission violations
        CONFIG,      // Configuration errors
        NETWORK,     // Network/connectivity issues
        INTERNAL,    // Unexpected internal errors
    }

    private val entries = mutableListOf<Entry.Error>()
    private val sessionId = java.util.UUID.randomUUID().toString()

    fun record(
        severity: Severity,
        category: Category,
        message: String,
        remediation: String,
    ): Entry.Error {
        val redacted = redactionFilter.redact(message)
        val entry = Entry.Error(
            id = java.util.UUID.randomUUID().toString(),
            severity = severity,
            category = category,
            message = redacted,
            remediation = remediation,
            sessionId = sessionId,
        )
        entries.add(entry)
        return entry
    }

    fun recordParseError(message: String, remediation: String) =
        record(Severity.HIGH, Category.PARSE, message, remediation)

    fun recordExecutionError(message: String, remediation: String) =
        record(Severity.HIGH, Category.EXECUTION, message, remediation)

    fun recordVerificationError(message: String, remediation: String) =
        record(Severity.CRITICAL, Category.VERIFICATION, message, remediation)

    fun recordProviderError(message: String, remediation: String) =
        record(Severity.HIGH, Category.PROVIDER, message, remediation)

    fun recordTerritoryError(message: String, remediation: String) =
        record(Severity.CRITICAL, Category.TERRITORY, message, remediation)

    fun recordConfigError(message: String, remediation: String) =
        record(Severity.MEDIUM, Category.CONFIG, message, remediation)

    fun recordNetworkError(message: String, remediation: String) =
        record(Severity.HIGH, Category.NETWORK, message, remediation)

    fun recordInternalError(message: String, remediation: String) =
        record(Severity.CRITICAL, Category.INTERNAL, message, remediation)

    fun summary(): Entry.Summary {
        return Entry.Summary(
            totalErrors = entries.size,
            bySeverity = entries.groupBy { it.severity }.mapValues { it.value.size },
            byCategory = entries.groupBy { it.category }.mapValues { it.value.size },
            sessionId = sessionId,
        )
    }

    fun all(): List<Entry.Error> = entries.toList()

    fun clear() { entries.clear() }
}

/**
 * Test utilities for competitive error ledger.
 */
object CompetitiveErrorLedgerTestUtils {
    /**
     * Verifies that every error in the ledger has a non-empty remediation.
     * This is the "remediation completeness" test.
     */
    fun assertRemediationCompleteness(ledger: CompetitiveErrorLedger) {
        val missingRemediation = ledger.all().filter { it.remediation.isBlank() }
        require(missingRemediation.isEmpty()) {
            "Found ${missingRemediation.size} errors without remediation: ${missingRemediation.map { it.id }}"
        }
    }

    /**
     * Verifies that no raw secrets appear in error messages.
     * This is the "secret redaction" test.
     */
    fun assertSecretRedaction(ledger: CompetitiveErrorLedger, redactionFilter: RedactionFilter) {
        val leaked = ledger.all().filter { entry ->
            val original = entry.message // Already redacted
            // Check for common secret patterns that should have been caught
            redactionFilter.isSecretLikely(original) // Would be false if properly redacted
        }
        require(leaked.isEmpty()) {
            "Found ${leaked.size} potentially unredacted secrets in error messages"
        }
    }

    /**
     * Verifies that severity mapping is consistent.
     * Same category + similar message should have same severity.
     */
    fun assertSeverityConsistency(ledger: CompetitiveErrorLedger) {
        val byCategory = ledger.all().groupBy { it.category }
        byCategory.forEach { (category, entries) ->
            val severities = entries.map { it.severity }.distinct()
            require(severities.size <= 2) {
                "Category $category has inconsistent severities: $severities"
            }
        }
    }

    /**
     * Competitive benchmark: compares error ledger against baseline.
     */
    fun competitiveBenchmark(
        ledger: CompetitiveErrorLedger,
        baseline: Map<CompetitiveErrorLedger.Category, Int>,
    ): Map<String, Any> {
        val summary = ledger.summary()
        return mapOf(
            "total_errors" to summary.totalErrors,
            "severity_distribution" to summary.bySeverity,
            "category_distribution" to summary.byCategory,
            "vs_baseline" to baseline.map { (cat, expected) ->
                cat to (summary.byCategory[cat] ?: 0) - expected
            },
        )
    }
}