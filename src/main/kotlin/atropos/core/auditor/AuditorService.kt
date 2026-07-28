package atropos.core.auditor

import atropos.core.security.RedactionFilter
import atropos.core.territory.TerritoryAssignment
import atropos.core.verification.DeterministicVerifier
import java.nio.file.Path
import java.time.Instant
import java.util.UUID

enum class AuditSeverity { PASS, INFO, WARNING, FAILURE, CRITICAL }

data class AuditFinding(
    val id: String = "audit-${UUID.randomUUID().toString().take(12)}",
    val check: String,
    val severity: AuditSeverity,
    val file: String? = null,
    val message: String,
    val evidence: String = "",
    val timestamp: Instant = Instant.now()
)

data class AuditReport(
    val id: String = "report-${UUID.randomUUID().toString().take(12)}",
    val findings: List<AuditFinding>,
    val passed: Int,
    val warnings: Int,
    val failures: Int,
    val timestamp: Instant = Instant.now()
) {
    val summary: String get() = "$passed passed, $warnings warnings, $failures failures"
}

data class AuditorPromotionDecision(
    val allowed: Boolean,
    val blockingFindings: List<AuditFinding>,
    val message: String
)

class AuditorService(
    /**
     * The repository the audited files belong to.
     *
     * [DeterministicVerifier] treats paths outside its root as out-of-scope
     * findings, so an auditor rooted somewhere else reports every file it was
     * given as out of scope. Defaulting the verifier's root instead of passing
     * this one made that misconfiguration invisible while the verifier still
     * crashed on such paths and the failure was swallowed as a warning.
     */
    private val repoRoot: Path = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
) {
    private val findings = mutableListOf<AuditFinding>()

    fun auditTerritories(territories: List<TerritoryAssignment>): List<AuditFinding> {
        val results = mutableListOf<AuditFinding>()
        for (t in territories) {
            if (t.expiresAt != null && Instant.now().isAfter(t.expiresAt)) {
                results += AuditFinding(check = "territory-expiry", severity = AuditSeverity.WARNING, file = t.allowedPrefix, message = "territory ${t.id} expired at ${t.expiresAt}")
            }
            if (t.allowedPrefix.isBlank()) {
                results += AuditFinding(check = "territory-prefix", severity = AuditSeverity.FAILURE, message = "territory ${t.id} has blank allowed prefix")
            }
            results += AuditFinding(check = "territory-exists", severity = AuditSeverity.PASS, file = t.allowedPrefix, message = "territory ${t.id} for ${t.ownerId} valid")
        }
        findings += results
        return results
    }

    fun auditSecrets(files: List<String>): List<AuditFinding> {
        val results = mutableListOf<AuditFinding>()
        val redactFilter = RedactionFilter()
        for (f in files) {
            val content = try { java.io.File(f).readText() } catch (_: Exception) { continue }
            val report = redactFilter.report(content)
            if (report.changed) {
                results += AuditFinding(check = "secret-scan", severity = AuditSeverity.FAILURE, file = f, message = "secrets found: ${report.summary()}", evidence = report.summary())
            }
        }
        if (results.isEmpty()) {
            results += AuditFinding(check = "secret-scan", severity = AuditSeverity.PASS, message = "no secrets found in scanned files")
        }
        findings += results
        return results
    }

    fun auditDeterministic(files: List<String>): List<AuditFinding> {
        val verifier = DeterministicVerifier(repoRoot)
        val results = mutableListOf<AuditFinding>()
        val paths = files.mapNotNull { f ->
            try { Path.of(f) } catch (_: Exception) { null }
        }
        if (paths.isEmpty()) return results
        try {
            val vr = verifier.verify(paths)
            if (vr.findings.isNotEmpty()) {
                for (vf in vr.findings) {
                    results += AuditFinding(check = "deterministic-verify", severity = if (vf.severity.name == "ERROR") AuditSeverity.FAILURE else AuditSeverity.WARNING, file = vf.file, message = vf.evidence, evidence = vf.remediation)
                }
            } else {
                results += AuditFinding(check = "deterministic-verify", severity = AuditSeverity.PASS, message = "no issues across ${files.size} files")
            }
        } catch (_: Exception) {
            results += AuditFinding(check = "deterministic-verify", severity = AuditSeverity.WARNING, message = "unable to verify ${files.size} files")
        }
        findings += results
        return results
    }

    fun report(): AuditReport {
        val passed = findings.count { it.severity == AuditSeverity.PASS }
        val warnings = findings.count { it.severity == AuditSeverity.WARNING || it.severity == AuditSeverity.INFO }
        val failures = findings.count { it.severity == AuditSeverity.FAILURE || it.severity == AuditSeverity.CRITICAL }
        return AuditReport(findings = findings.toList(), passed = passed, warnings = warnings, failures = failures)
    }

    fun blockPromotion(report: AuditReport = report(), claimedBy: String? = null, auditedBy: String? = "auditor"): AuditorPromotionDecision {
        val blocking = report.findings.filter {
            it.severity == AuditSeverity.FAILURE || it.severity == AuditSeverity.CRITICAL
        }.toMutableList()
        if (claimedBy != null && auditedBy != null && claimedBy == auditedBy) {
            blocking += AuditFinding(
                check = "auditor-independence",
                severity = AuditSeverity.CRITICAL,
                message = "promotion cannot be audited by the same actor that claimed the work",
                evidence = "claimedBy=$claimedBy auditedBy=$auditedBy"
            )
        }
        return AuditorPromotionDecision(
            allowed = blocking.isEmpty(),
            blockingFindings = blocking,
            message = if (blocking.isEmpty()) "auditor promotion gate passed" else "auditor promotion gate blocked: ${blocking.size} findings"
        )
    }
}
