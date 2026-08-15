/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import atropos.core.evaluation.MetricId
import java.time.Instant

/**
 * P20-G01: Nonzero compile/test exit forbids VERIFIED.
 */
class CompileTestExitDetector : GovernanceDetector {
    override val id = "P20-G01"
    override val severity = ObservationSeverity.FAILURE

    override fun detect(context: GovernanceDetectorContext): RuntimeObservation? {
        if (context.exitCode != null && context.exitCode != 0) {
            return RuntimeObservation(
                id = "$id-obs-${System.currentTimeMillis()}",
                timestamp = Instant.now(),
                runtimeId = context.runtimeId,
                projectId = context.projectId,
                goalId = context.goalId,
                nodeId = context.nodeId,
                authorityFingerprint = context.authorityFingerprint,
                environmentFingerprint = context.environmentFingerprint,
                exitCode = context.exitCode,
                boundedOutput = "Nonzero compile/test exit: ${context.output.take(200)}",
                artifactHashes = context.artifactHashes,
                frequency = 1,
                severity = severity,
                requirementBlocked = "verified-completion"
            )
        }
        return null
    }
}

/**
 * P20-G02: Territory violations.
 */
class TerritoryViolationDetector : GovernanceDetector {
    override val id = "P20-G02"
    override val severity = ObservationSeverity.FAILURE

    override fun detect(context: GovernanceDetectorContext): RuntimeObservation? {
        val offending = context.changes.filter { change ->
            context.territory.none { allowed -> change.startsWith(allowed) }
        }
        if (offending.isNotEmpty()) {
            return RuntimeObservation(
                id = "$id-obs-${System.currentTimeMillis()}",
                timestamp = Instant.now(),
                runtimeId = context.runtimeId,
                projectId = context.projectId,
                goalId = context.goalId,
                nodeId = context.nodeId,
                authorityFingerprint = context.authorityFingerprint,
                environmentFingerprint = context.environmentFingerprint,
                exitCode = null,
                boundedOutput = "Territory violation: files modified outside assigned scope: ${offending.joinToString()}",
                artifactHashes = context.artifactHashes,
                frequency = 1,
                severity = severity,
                invariantBroken = "territory"
            )
        }
        return null
    }
}

/**
 * P20-G03: Recovery completeness.
 */
class RecoveryCompletenessDetector : GovernanceDetector {
    override val id = "P20-G03"
    override val severity = ObservationSeverity.FAILURE

    override fun detect(context: GovernanceDetectorContext): RuntimeObservation? {
        val metric = context.metrics.firstOrNull { it.id == MetricId.RESTART_RECOVERY_SUCCESS }
        if (metric != null && metric.value < MetricId.RESTART_RECOVERY_SUCCESS.target) {
            return RuntimeObservation(
                id = "$id-obs-${System.currentTimeMillis()}",
                timestamp = Instant.now(),
                runtimeId = context.runtimeId,
                projectId = context.projectId,
                goalId = context.goalId,
                nodeId = context.nodeId,
                authorityFingerprint = context.authorityFingerprint,
                environmentFingerprint = context.environmentFingerprint,
                exitCode = null,
                boundedOutput = "Recovery incompleteness: restart recovery success rate is ${metric.value}",
                artifactHashes = metric.evidenceHashes,
                frequency = 1,
                severity = severity,
                requirementBlocked = "restart-recovery"
            )
        }
        return null
    }
}

/**
 * P20-G04: Tokens-per-verified-change.
 */
class TokenInefficiencyDetector : GovernanceDetector {
    override val id = "P20-G04"
    override val severity = ObservationSeverity.DEGRADED

    override fun detect(context: GovernanceDetectorContext): RuntimeObservation? {
        val metric = context.metrics.firstOrNull { it.id == MetricId.COORDINATION_EFFICIENCY }
        if (metric != null && metric.value > MetricId.COORDINATION_EFFICIENCY.target) {
            return RuntimeObservation(
                id = "$id-obs-${System.currentTimeMillis()}",
                timestamp = Instant.now(),
                runtimeId = context.runtimeId,
                projectId = context.projectId,
                goalId = context.goalId,
                nodeId = context.nodeId,
                authorityFingerprint = context.authorityFingerprint,
                environmentFingerprint = context.environmentFingerprint,
                exitCode = null,
                boundedOutput = "Token inefficiency: coordination efficiency ${metric.value} exceeds target ${MetricId.COORDINATION_EFFICIENCY.target}",
                artifactHashes = metric.evidenceHashes,
                frequency = 1,
                severity = severity
            )
        }
        return null
    }
}
