package atropos.core.agent

/**
 * The [AgentPatchRunResult] shapes for runs that produced no patch.
 *
 * Three distinct nothings, each of which an operator reads differently:
 *
 *  - [noRepairTarget] — there was nothing to repair. Not a failure.
 *  - [missingPatch] — the named patch does not exist. An operator mistake.
 *  - [localFailure] — a repair was attempted and no provider produced a diff.
 *
 * They were three near-identical constructor blocks whose only real difference
 * was the message. Collecting them here is what makes the differences legible;
 * inline, they read as one shape copied three times.
 *
 * Every result here carries `patchPath = null` and `checkResult = null`, which
 * is the load-bearing part: nothing downstream can mistake one of these for a
 * run that put something on disk.
 */
internal object AgentPatchRunResultFactory {

    fun noRepairTarget(patchId: String?): AgentPatchRunResult =
        AgentPatchRunResult(
            providerName = NO_PROVIDER,
            contextByteCount = 0,
            diffByteCount = 0,
            patchId = patchId,
            patchPath = null,
            checkResult = null,
            retryAttempted = false,
            failureSummary = NO_REPAIR_TARGET,
            rejectionReason = NO_REPAIR_TARGET,
            responsePreview = "",
            message = NO_REPAIR_TARGET
        )

    fun missingPatch(reference: String): AgentPatchRunResult {
        val reason = refusalForMissingPatch(reference)
        return AgentPatchRunResult(
            providerName = NO_PROVIDER,
            contextByteCount = 0,
            diffByteCount = 0,
            patchId = null,
            patchPath = null,
            checkResult = null,
            retryAttempted = false,
            failureSummary = reason,
            rejectionReason = reason,
            responsePreview = "",
            message = reason
        )
    }

    /**
     * A repair that ran and produced nothing applicable.
     *
     * The message says explicitly that nothing was applied. The local fallback
     * cannot synthesise a diff, and a result that merely reported a provider
     * name would read as though something had happened.
     */
    fun localFailure(
        providerName: String,
        contextByteCount: Int,
        retryAttempted: Boolean,
        failureSummary: String,
        rejectionReason: String? = null,
        responsePreview: String? = null
    ): AgentPatchRunResult =
        AgentPatchRunResult(
            providerName = providerName,
            contextByteCount = contextByteCount,
            diffByteCount = 0,
            patchId = null,
            patchPath = null,
            checkResult = null,
            retryAttempted = retryAttempted,
            rejectionReason = rejectionReason,
            responsePreview = responsePreview,
            failureSummary = failureSummary,
            message = NOTHING_APPLIED
        )

    fun refusalForMissingPatch(reference: String): String {
        val trimmed = reference.trim()
        return if (trimmed.isBlank()) "no patch id exists" else "patch not found: $trimmed"
    }

    private const val NO_PROVIDER = "none"
    private const val NO_REPAIR_TARGET = "no failed verification to repair."
    private const val NOTHING_APPLIED =
        "ATROPOS did not apply anything. Local fallback cannot generate a provider patch."
}
