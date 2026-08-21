package atropos.core.factory

class FactoryResultBuilder {
    fun buildResult(
        originalPlan: FactoryPlan,
        lineage: FactoryLineage,
        generatedProject: GeneratedAppProject,
        planningDagId: String,
        plannedAtomIds: List<String>,
        assetFiles: List<String>,
        softFailures: List<String>,
        memoryRecordId: String?,
        projectRecordId: String,
        contextHash: String,
        acceptanceFreezeSha256: String?,
        economics: FactoryRunEconomics,
        terminationReason: String
    ): FactoryPlan {
        return originalPlan.copy(
            queuedWork = emptyList(),
            assetFiles = assetFiles,
            memoryRecordId = memoryRecordId,
            projectRecordId = projectRecordId,
            generatedProject = generatedProject,
            planningDagId = planningDagId,
            plannedAtomIds = plannedAtomIds,
            softFailures = softFailures,
            promptFingerprint = lineage.promptFingerprint,
            promptSha256 = lineage.promptSha256,
            promptSpans = lineage.promptSpans,
            confidenceScore = lineage.confidence.score,
            confidenceBreakdown = lineage.confidence.breakdown,
            researchSha256 = lineage.researchSha256,
            researchChannels = lineage.researchChannels,
            researchState = lineage.researchState,
            proposalSha256 = generatedProject.proposalSha256,
            memoryPointers = lineage.memoryPointers,
            contextHash = contextHash,
            specGraphStatus = lineage.atomizerStatus,
            eventJournalPath = ".atropos/runs/${originalPlan.id}/events.journal",
            acceptanceFreezeSha256 = acceptanceFreezeSha256,
            economics = economics,
            terminationReason = terminationReason
        )
    }
}
