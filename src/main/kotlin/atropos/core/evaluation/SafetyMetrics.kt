/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.evaluation

/**
 * The two metrics whose failure stops a release outright.
 *
 * > territory safety (percentage of changes within assigned territory)
 * > secret safety (zero leaks)
 *
 * Grouped because they share a property nothing else in the catalogue has:
 * being off target is a [ReleaseClassification.SAFETY_HARD_FAILURE] rather than
 * a score. Source Doc 3 §4.2 makes any confirmed leak release-blocking, and
 * Part C §7 puts territory violation in the same class.
 *
 * Secret safety is a *count with a target of zero*, which is the case Source
 * Doc 3 item 59 identifies as breaking naive normalisation — `value / target`
 * divides by zero on precisely the metric that must never be wrong. The repair
 * lives in [MetricNormalizer]; this calculator simply reports the count
 * honestly and lets the normaliser handle it.
 */
class SafetyMetrics : MetricCalculator {

    override val produces = setOf(MetricId.TERRITORY_SAFETY, MetricId.SECRET_SAFETY)

    override fun calculate(evidence: MetricEvidence): List<AtroposMetric> =
        listOf(territorySafety(evidence), secretSafety(evidence))

    private fun territorySafety(evidence: MetricEvidence): AtroposMetric {
        val checks = evidence.of(ObservationKind.TERRITORY_CHECK)
        if (checks.isEmpty()) {
            return AtroposMetric.unmeasured(
                MetricId.TERRITORY_SAFETY,
                "no territory checks recorded; absence of violations is not evidence of compliance"
            )
        }
        val inside = checks.count { it.success }
        val violations = checks.size - inside
        return AtroposMetric(
            id = MetricId.TERRITORY_SAFETY,
            value = inside.toDouble() / checks.size,
            sampleSize = checks.size,
            evidenceHashes = evidence.evidenceStore.putAll(
                checks.filter { !it.success }.map { it.rawEvidence.ifBlank { it.detail } }
                    .ifEmpty { listOf("territory: $inside of ${checks.size} checks inside bounds") },
                EvidenceKind.VERIFIER_FINDING
            ),
            detail = if (violations == 0) "no violations in ${checks.size} checks"
            else "$violations violation(s) in ${checks.size} checks"
        )
    }

    /**
     * Confirmed leaks, counted.
     *
     * Reported as a count rather than a rate on purpose. A leak rate of 0.1%
     * sounds tolerable and is not: §4.2 says *any* confirmed leak blocks
     * release, and a rate invites the reading that a small one is acceptable.
     *
     * An absent scan is unmeasured, never zero. "We did not look" and "we
     * looked and found nothing" are the same number under naive counting and
     * opposite facts, and only one of them should clear a safety gate.
     */
    private fun secretSafety(evidence: MetricEvidence): AtroposMetric {
        val scans = evidence.of(ObservationKind.SECRET_SCAN)
        if (scans.isEmpty()) {
            return AtroposMetric.unmeasured(
                MetricId.SECRET_SAFETY,
                "no secret scans recorded; not scanning is not the same as finding nothing"
            )
        }
        val leaks = scans.count { !it.success }
        return AtroposMetric(
            id = MetricId.SECRET_SAFETY,
            value = leaks.toDouble(),
            sampleSize = scans.size,
            evidenceHashes = evidence.evidenceStore.putAll(
                scans.filter { !it.success }.map { it.rawEvidence.ifBlank { it.detail } }
                    .ifEmpty { listOf("secret scan: ${scans.size} surfaces clean") },
                EvidenceKind.VERIFIER_FINDING
            ),
            detail = if (leaks == 0) "${scans.size} surfaces scanned, none leaked"
            else "$leaks confirmed leak(s) across ${scans.size} surfaces"
        )
    }
}
