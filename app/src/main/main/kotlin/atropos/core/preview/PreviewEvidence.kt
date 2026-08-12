/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.preview

import java.time.Instant

/**
 * Evidence captured from an isolated preview.
 *
 * `C3-P17` requires "isolated preview, screenshots, visual comparison,
 * accessibility checks"; `C3-AF-02` requires the preview to be an acceptance
 * requirement with "blank/error states visible". The second half shapes this
 * type: a preview that rendered nothing must be distinguishable from one that
 * was never run, and both from one that rendered correctly.
 *
 * Hence [PreviewOutcome.BLANK]. A blank render reported as success is the exact
 * failure `C3-AF-02` names, and it is the easy one to ship because a screenshot
 * of an empty page is still a screenshot.
 */
data class PreviewEvidence(
    val id: String,
    /** The requirement this was captured for — evidence is never orphaned. */
    val requirementId: String,
    val outcome: PreviewOutcome,
    val capturedAt: Instant,
    /** SHA-256 of the captured image; absent when nothing was captured. */
    val screenshotSha256: String? = null,
    val accessibilityViolations: List<String> = emptyList(),
    val detail: String = ""
) {
    /**
     * True when this preview may support an acceptance claim.
     *
     * Accessibility violations block it because `HOE-F04` makes accessibility
     * release-blocking: a preview that rendered beautifully and failed a
     * contrast check has not passed.
     */
    fun supportsAcceptance(): Boolean =
        outcome == PreviewOutcome.RENDERED &&
            !screenshotSha256.isNullOrBlank() &&
            accessibilityViolations.isEmpty()

    fun refusalReason(): String? = when {
        outcome == PreviewOutcome.NOT_RUN -> "the preview was never run"
        outcome == PreviewOutcome.BLANK -> "the preview rendered nothing"
        outcome == PreviewOutcome.ERROR -> "the preview errored: $detail"
        screenshotSha256.isNullOrBlank() -> "no screenshot was captured"
        accessibilityViolations.isNotEmpty() ->
            "accessibility violations: ${accessibilityViolations.joinToString(", ")}"
        else -> null
    }
}

enum class PreviewOutcome {
    /** No attempt was made. Never conflated with a failed attempt. */
    NOT_RUN,
    RENDERED,
    /** Rendered, but empty — the failure mode C3-AF-02 calls out. */
    BLANK,
    ERROR
}

/**
 * Compares two captures.
 *
 * An absent baseline is refused rather than treated as a match: a first capture
 * has nothing to compare against, and reporting it as unchanged would let any
 * regression through on the run that introduced the baseline.
 */
object PreviewComparison {
    fun compare(baseline: PreviewEvidence?, current: PreviewEvidence): ComparisonResult = when {
        baseline == null -> ComparisonResult.NoBaseline
        baseline.screenshotSha256.isNullOrBlank() || current.screenshotSha256.isNullOrBlank() ->
            ComparisonResult.Incomparable("one side has no captured image")
        baseline.screenshotSha256 == current.screenshotSha256 -> ComparisonResult.Unchanged
        else -> ComparisonResult.Changed(baseline.screenshotSha256, current.screenshotSha256)
    }
}

sealed class ComparisonResult {
    object NoBaseline : ComparisonResult()
    object Unchanged : ComparisonResult()
    data class Changed(val from: String, val to: String) : ComparisonResult()
    data class Incomparable(val reason: String) : ComparisonResult()
}
