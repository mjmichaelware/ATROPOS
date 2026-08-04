package atropos.core.agent

/**
 * Decides whether a provider response is usable as a patch, and if not, why.
 *
 * The single owner of that judgement. [AgentPatchCascadeRunner] and
 * [AgentRepairService] each carried a private copy of these three rules, so the
 * two paths could — and did — drift into disagreeing about what counts as a
 * diff. A patch that one path would apply and the other would refuse is the
 * kind of difference nobody notices until it matters.
 *
 * ## The three rules, in order
 *
 * 1. Something diff-shaped must be extractable at all.
 * 2. It must have a hunk body. A response can carry `diff --git` headers and no
 *    `@@` hunk — a model describing a change rather than making one. Applying
 *    that is a no-op that would be recorded as a successful mutation.
 * 3. It must survive [AgentPatchExtractor.validate].
 *
 * The order matters for the reason text: "no unified diff found" and "diff body
 * missing" are different operator-facing problems, and collapsing them loses the
 * distinction between a model that ignored the format and one that half-followed it.
 */
internal class AgentPatchResponseValidator(
    private val patchExtractor: AgentPatchExtractor
) {

    /** The extraction when the response is usable, null when it is not. */
    fun usableDiff(response: String): AgentPatchExtraction? {
        val extraction = patchExtractor.extract(response) ?: return null
        if (!extraction.hasHunkBody) return null
        if (patchExtractor.validate(extraction.diff) != null) return null
        return extraction
    }

    /**
     * Why [response] was not usable.
     *
     * Only meaningful after [usableDiff] returned null; on a usable response the
     * validator has no complaint to report and this falls through to the
     * catch-all.
     */
    fun rejectionReason(response: String): String {
        val extraction = patchExtractor.extract(response)
        return when {
            extraction == null ->
                if (containsDiffHeader(response)) DIFF_BODY_MISSING else NO_DIFF_FOUND

            !extraction.hasHunkBody -> DIFF_BODY_MISSING

            else -> patchExtractor.validate(extraction.diff) ?: UNKNOWN_REJECTION
        }
    }

    /**
     * True when the text carries a diff header even though extraction failed.
     *
     * Distinguishes "the model tried and produced something malformed" from "the
     * model answered in prose", which is the difference between a retry that is
     * worth attempting and one that is not.
     */
    fun containsDiffHeader(text: String): Boolean =
        text.contains("diff --git ") ||
            text.contains("\n--- ") ||
            text.trimStart().startsWith("--- ")

    internal companion object {
        const val NO_DIFF_FOUND = "no unified diff found"
        const val DIFF_BODY_MISSING = "diff body missing"
        const val UNKNOWN_REJECTION = "unknown patch rejection"

        /** The empty extraction used to fill a failure attempt that has no diff. */
        fun emptyExtraction(): AgentPatchExtraction = AgentPatchExtraction("", emptyList(), false)
    }
}
