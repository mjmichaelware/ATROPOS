/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import atropos.core.evaluation.MetricId
import java.time.Instant

/**
 * P20-G05: Observation success.
 */
class ObservationFailureDetector : GovernanceDetector {
    override val id = "P20-G05"
    override val severity = ObservationSeverity.DEGRADED

    override fun detect(context: GovernanceDetectorContext): RuntimeObservation? {
        val metric = context.metrics.firstOrNull { it.id == MetricId.REPAIR_QUALITY }
        if (metric != null && metric.value < MetricId.REPAIR_QUALITY.target) {
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
                boundedOutput = "Observation failure: repair quality is degraded at ${metric.value}",
                artifactHashes = metric.evidenceHashes,
                frequency = 1,
                severity = severity
            )
        }
        return null
    }
}

/**
 * P20-G06: Unredacted secrets.
 */
class UnredactedSecretDetector : GovernanceDetector {
    override val id = "P20-G06"
    override val severity = ObservationSeverity.SAFETY_CRITICAL

    override fun detect(context: GovernanceDetectorContext): RuntimeObservation? {
        val secretMetric = context.metrics.firstOrNull { it.id == MetricId.SECRET_SAFETY }
        if (secretMetric != null && secretMetric.value > MetricId.SECRET_SAFETY.target) {
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
                boundedOutput = "SAFETY CRITICAL: Unredacted secret or key leak detected",
                artifactHashes = secretMetric.evidenceHashes,
                frequency = 1,
                severity = severity,
                invariantBroken = "secret-safety"
            )
        }
        return null
    }
}

/**
 * P20-G07: Identity recognition.
 */
class IdentityMismatchesDetector : GovernanceDetector {
    override val id = "P20-G07"
    override val severity = ObservationSeverity.FAILURE

    override fun detect(context: GovernanceDetectorContext): RuntimeObservation? {
        val metric = context.metrics.firstOrNull { it.id == MetricId.IDENTITY_RECOGNITION }
        if (metric != null && metric.value < MetricId.IDENTITY_RECOGNITION.target) {
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
                boundedOutput = "Identity mismatches: recognition rate ${metric.value} is below target",
                artifactHashes = metric.evidenceHashes,
                frequency = 1,
                severity = severity
            )
        }
        return null
    }
}

/**
 * P20-G08: Oscillation and repeating deficiencies.
 */
class OscillationDetector : GovernanceDetector {
    override val id = "P20-G08"
    override val severity = ObservationSeverity.FAILURE

    override fun detect(context: GovernanceDetectorContext): RuntimeObservation? {
        if (context.failures >= 3) {
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
                boundedOutput = "Oscillation: Deficiency repeating with ${context.failures} consecutive failures",
                artifactHashes = context.artifactHashes,
                frequency = context.failures,
                severity = severity,
                requirementBlocked = "oscillation-cooldown"
            )
        }
        return null
    }
}

/**
 * P20-G09: Completion-state vocabulary collapse.
 */
class VocabularyCollapseDetector : GovernanceDetector {
    override val id = "P20-G09"
    override val severity = ObservationSeverity.SAFETY_CRITICAL

    override fun detect(context: GovernanceDetectorContext): RuntimeObservation? {
        if (context.stateVocabularyCollapsed) {
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
                boundedOutput = "Vocabulary Collapse: Completion-state distinction is collapsed",
                artifactHashes = context.artifactHashes,
                frequency = 1,
                severity = severity,
                invariantBroken = "vocabulary-collapse"
            )
        }
        return null
    }
}

/** Registry and orchestrator of all governance detectors. */
object GovernanceDetectorsRegistry {
    val detectors: List<GovernanceDetector> = listOf(
        CompileTestExitDetector(),
        TerritoryViolationDetector(),
        RecoveryCompletenessDetector(),
        TokenInefficiencyDetector(),
        ObservationFailureDetector(),
        UnredactedSecretDetector(),
        IdentityMismatchesDetector(),
        OscillationDetector(),
        VocabularyCollapseDetector()
    )

    fun runAll(context: GovernanceDetectorContext): List<RuntimeObservation> =
        detectors.mapNotNull { it.detect(context) }
}
