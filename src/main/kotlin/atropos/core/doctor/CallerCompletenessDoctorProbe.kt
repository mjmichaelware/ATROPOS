/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.doctor

import java.nio.file.Path

class CallerCompletenessDoctorProbe(private val gate: CallerCompletenessGate) {

    fun check(): String {
        val report = gate.audit()
        if (report.passed) {
            return "PASS: All Tier-1 classes wired. Zero template references. Zero file atomicity defects."
        } else {
            val message = buildString {
                appendLine("FAIL: Completeness audit failed!")
                if (report.unwiredClasses.isNotEmpty()) {
                    appendLine("Unwired classes:")
                    report.unwiredClasses.forEach { appendLine("  - $it") }
                }
                if (report.violations.isNotEmpty()) {
                    appendLine("Violations:")
                    report.violations.forEach { appendLine("  - $it") }
                }
            }
            throw IllegalStateException(message)
        }
    }

    /**
     * SD7-GATE-T4: Doctor probe asserting Tier-4 completeness.
     */
    fun checkTier4(): String {
        val report = gate.auditTier4()
        if (report.passed) {
            return "PASS: All Tier-4 classes wired. Sem(M_local) <= Sem(EXT_live) pre-flight validated. Zero-entropy proof ready."
        } else {
            val message = buildString {
                appendLine("FAIL: Tier-4 completeness audit failed!")
                if (report.unwiredClasses.isNotEmpty()) {
                    appendLine("Unwired classes:")
                    report.unwiredClasses.forEach { appendLine("  - $it") }
                }
                if (report.violations.isNotEmpty()) {
                    appendLine("Violations:")
                    report.violations.forEach { appendLine("  - $it") }
                }
            }
            throw IllegalStateException(message)
        }
    }
}
