package atropos.core.auditor

import atropos.core.AtroposRepoRootLocator
import atropos.core.security.RedactionFilter
import atropos.core.territory.TerritoryAssignment
import atropos.core.verification.DeterministicVerifier
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.security.MessageDigest
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

    /** Digest of the immutable finding set used by downstream promotion evidence. */
    val evidenceSha256: String
        get() = sha256(findings.joinToString("\n") { finding ->
            listOf(
                finding.check,
                finding.severity.name,
                finding.file.orEmpty(),
                finding.message,
                finding.evidence
            ).joinToString("\u001f")
        })

    private companion object {
        fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}

data class AuditorPromotionDecision(
    val allowed: Boolean,
    val blockingFindings: List<AuditFinding>,
    val message: String,
    val reportEvidenceSha256: String
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
    private val repoRoot: Path = AtroposRepoRootLocator.resolve()
) {
    private val findings = mutableListOf<AuditFinding>()
    private val normalizedRepoRoot = repoRoot.toAbsolutePath().normalize()

    fun auditTerritories(territories: List<TerritoryAssignment>): List<AuditFinding> {
        val results = mutableListOf<AuditFinding>()
        for (t in territories) {
            if (t.expiresAt != null && Instant.now().isAfter(t.expiresAt)) {
                results += AuditFinding(check = "territory-expiry", severity = AuditSeverity.FAILURE, file = t.allowedPrefix, message = "territory ${t.id} expired at ${t.expiresAt}")
            }
            val safePrefix = t.allowedPrefix.isNotBlank() && isSafeTerritoryPrefix(t.allowedPrefix)
            if (t.allowedPrefix.isBlank()) {
                results += AuditFinding(check = "territory-prefix", severity = AuditSeverity.FAILURE, message = "territory ${t.id} has blank allowed prefix")
            } else if (!safePrefix) {
                results += AuditFinding(
                    check = "territory-prefix-safety",
                    severity = AuditSeverity.FAILURE,
                    file = t.allowedPrefix,
                    message = "territory ${t.id} has an unsafe repository prefix"
                )
            }
            if (safePrefix) {
                results += AuditFinding(check = "territory-exists", severity = AuditSeverity.PASS, file = t.allowedPrefix, message = "territory ${t.id} for ${t.ownerId} valid")
            }
        }
        findings += results
        return results
    }

    fun auditSecrets(files: List<String>): List<AuditFinding> {
        val results = mutableListOf<AuditFinding>()
        val redactFilter = RedactionFilter()
        for (f in files) {
            val resolved = resolveAuditPath(f)
            if (resolved == null) {
                results += AuditFinding(
                    check = "secret-scan",
                    severity = AuditSeverity.FAILURE,
                    file = f,
                    message = "secret scan refused path outside repository root"
                )
                continue
            }
            val content: String
            try {
                content = resolved.toFile().readText()
            } catch (failure: Exception) {
                results += AuditFinding(
                    check = "secret-scan",
                    severity = AuditSeverity.FAILURE,
                    file = f,
                    message = "secret scan could not read file",
                    evidence = failure.javaClass.simpleName
                )
                continue
            }
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

    fun auditSecretText(label: String, content: String): List<AuditFinding> {
        val report = RedactionFilter().report(content)
        val result = if (report.changed) {
            AuditFinding(
                check = "secret-scan",
                severity = AuditSeverity.FAILURE,
                file = label,
                message = "secrets found: ${report.summary()}",
                evidence = report.summary()
            )
        } else {
            AuditFinding(
                check = "secret-scan",
                severity = AuditSeverity.PASS,
                file = label,
                message = "no secrets found in scanned text"
            )
        }
        findings += result
        return listOf(result)
    }

    fun auditDeterministic(files: List<String>): List<AuditFinding> {
        val verifier = DeterministicVerifier(repoRoot)
        val results = mutableListOf<AuditFinding>()
        val paths = files.mapNotNull { f ->
            resolveAuditPath(f)
        }
        if (paths.size != files.size) {
            results += AuditFinding(
                check = "deterministic-verify",
                severity = AuditSeverity.FAILURE,
                message = "deterministic audit refused one or more paths outside repository root"
            )
            findings += results
            return results
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
        } catch (failure: Exception) {
            results += AuditFinding(
                check = "deterministic-verify",
                severity = AuditSeverity.FAILURE,
                message = "unable to verify ${files.size} files",
                evidence = failure.javaClass.simpleName
            )
        }
        findings += results
        return results
    }

    private fun resolveAuditPath(raw: String): Path? {
        val requested = runCatching { Path.of(raw) }.getOrNull() ?: return null
        val resolved = (if (requested.isAbsolute) requested else normalizedRepoRoot.resolve(requested))
            .toAbsolutePath()
            .normalize()
        if (!resolved.startsWith(normalizedRepoRoot)) return null

        val relative = normalizedRepoRoot.relativize(resolved)
        var cursor = normalizedRepoRoot
        for (part in relative) {
            cursor = cursor.resolve(part)
            if (java.nio.file.Files.isSymbolicLink(cursor)) return null
        }

        val realRoot = runCatching { normalizedRepoRoot.toRealPath() }.getOrNull() ?: return null
        val realPath = runCatching { resolved.toRealPath() }.getOrNull()
        return when {
            realPath == null -> resolved
            realPath.startsWith(realRoot) -> resolved
            else -> null
        }
    }

    private fun isSafeTerritoryPrefix(raw: String): Boolean {
        val portable = raw.replace('\\', '/').trim().trim('/')
        if (portable.isBlank()) return false
        if (portable.split('/').any { it.isBlank() || it == "." || it == ".." }) return false
        val requested = runCatching { Path.of(portable) }.getOrNull() ?: return false
        if (requested.isAbsolute) return false
        return resolveAuditPath(portable) != null
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
        if (report.findings.isEmpty()) {
            blocking += AuditFinding(
                check = "audit-completeness",
                severity = AuditSeverity.CRITICAL,
                message = "promotion cannot proceed without recorded audit findings",
                evidence = "audit report was empty"
            )
        }
        if (auditedBy.isNullOrBlank()) {
            blocking += AuditFinding(
                check = "auditor-independence",
                severity = AuditSeverity.CRITICAL,
                message = "promotion cannot proceed without an auditor identity",
                evidence = "auditedBy was blank"
            )
        }
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
            message = if (blocking.isEmpty()) "auditor promotion gate passed" else "auditor promotion gate blocked: ${blocking.size} findings",
            reportEvidenceSha256 = report.evidenceSha256
        )
    }
}
