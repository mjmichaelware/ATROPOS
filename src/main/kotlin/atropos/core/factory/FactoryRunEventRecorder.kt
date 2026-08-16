package atropos.core.factory

import atropos.core.journal.EventCategory
import atropos.core.journal.EventJournalService
import atropos.cli.ui.design.RunState
import atropos.core.observability.EventPublisher
import atropos.core.observability.ExecutionRole

class FactoryRunEventRecorder(
    private val journal: EventJournalService
) {
    private val publisher = EventPublisher(journal = journal)

    fun recordLifecycleStart(
        runId: String,
        intent: String,
        appName: String,
        promptFingerprint: String
    ) {
        record(
            runId = runId,
            category = EventCategory.LIFECYCLE,
            payload = "factory_started intent=$intent app=$appName",
            promptFingerprint = promptFingerprint
        )
    }

    fun recordResearchStatus(
        runId: String,
        state: String,
        researchSha256: String,
        channels: String,
        promptFingerprint: String
    ) {
        record(
            runId = runId,
            category = EventCategory.STATUS,
            payload = "factory_step kind=${FactoryStepKind.RESEARCH} state=$state research_sha256=$researchSha256 channels=$channels",
            promptFingerprint = promptFingerprint
        )
    }

    fun recordPlannedStep(
        runId: String,
        kind: FactoryStepKind,
        state: String,
        route: String,
        localFirst: Boolean,
        promptFingerprint: String
    ) {
        record(
            runId = runId,
            category = EventCategory.STATUS,
            payload = "factory_step kind=$kind state=$state route=$route local_first=$localFirst",
            promptFingerprint = promptFingerprint
        )
    }

    fun recordMemoryStep(
        runId: String,
        state: String,
        promptFingerprint: String
    ) {
        record(
            runId = runId,
            category = EventCategory.STATUS,
            payload = "factory_step kind=${FactoryStepKind.MEMORY} state=$state",
            promptFingerprint = promptFingerprint
        )
    }

    fun recordPlanDag(
        runId: String,
        dagId: String,
        atomIds: String,
        promptFingerprint: String
    ) {
        record(
            runId = runId,
            category = EventCategory.DAG,
            payload = "factory_plan dag=$dagId atoms=$atomIds",
            dagId = dagId,
            promptFingerprint = promptFingerprint
        )
    }

    fun recordPlanCompletion(
        runId: String,
        dagId: String,
        promptFingerprint: String
    ) {
        record(
            runId = runId,
            category = EventCategory.STATUS,
            payload = "factory_step kind=${FactoryStepKind.PLAN} state=COMPLETED dag=$dagId",
            dagId = dagId,
            promptFingerprint = promptFingerprint
        )
    }

    fun recordAtomizationStatus(
        runId: String,
        state: String,
        specgraph: String,
        atomCount: Int,
        dagId: String,
        promptFingerprint: String
    ) {
        record(
            runId = runId,
            category = EventCategory.STATUS,
            payload = "factory_step kind=${FactoryStepKind.ATOMIZE} state=$state specgraph=$specgraph atoms=$atomCount",
            dagId = dagId,
            promptFingerprint = promptFingerprint
        )
    }

    fun recordFileMutation(
        runId: String,
        territory: String,
        branch: String,
        dagId: String,
        promptFingerprint: String
    ) {
        record(
            runId = runId,
            category = EventCategory.FILE_MUTATION,
            payload = "factory_mutation target=$territory branch=$branch",
            dagId = dagId,
            promptFingerprint = promptFingerprint
        )
    }

    fun recordGenerationFailure(
        runId: String,
        failureType: String,
        dagId: String,
        promptFingerprint: String
    ) {
        record(
            runId = runId,
            category = EventCategory.FAILURE,
            payload = "factory_generation_failed type=$failureType",
            dagId = dagId,
            promptFingerprint = promptFingerprint
        )
    }

    fun recordVerification(
        runId: String,
        commitId: String,
        treeSha256: String,
        dagId: String,
        promptFingerprint: String
    ) {
        record(
            runId = runId,
            category = EventCategory.VERIFICATION,
            payload = "factory_verified commit=$commitId tree=$treeSha256",
            dagId = dagId,
            promptFingerprint = promptFingerprint
        )
    }

    fun recordTests(
        runId: String,
        state: String,
        evidencePath: String,
        dagId: String,
        promptFingerprint: String
    ) {
        record(
            runId = runId,
            category = EventCategory.TEST,
            payload = "factory_tests state=$state evidence=$evidencePath",
            dagId = dagId,
            promptFingerprint = promptFingerprint
        )
    }

    fun recordDiff(
        runId: String,
        state: String,
        fileCount: Int,
        treeSha256: String,
        dagId: String,
        promptFingerprint: String
    ) {
        record(
            runId = runId,
            category = EventCategory.DIFF,
            payload = "factory_diff state=$state files=$fileCount tree=$treeSha256",
            dagId = dagId,
            promptFingerprint = promptFingerprint
        )
    }

    fun recordCodeCompletion(
        runId: String,
        projectPath: String,
        dagId: String,
        promptFingerprint: String
    ) {
        record(
            runId = runId,
            category = EventCategory.STATUS,
            payload = "factory_step kind=${FactoryStepKind.CODE} state=COMPLETED project=$projectPath",
            dagId = dagId,
            promptFingerprint = promptFingerprint
        )
    }

    fun recordValidation(
        runId: String,
        evidencePath: String,
        dagId: String,
        promptFingerprint: String
    ) {
        record(
            runId = runId,
            category = EventCategory.STATUS,
            payload = "factory_step kind=${FactoryStepKind.VALIDATE} state=COMPLETED evidence=$evidencePath",
            dagId = dagId,
            promptFingerprint = promptFingerprint
        )
    }

    fun recordRepair(
        runId: String,
        state: String,
        verification: String,
        dagId: String,
        promptFingerprint: String
    ) {
        record(
            runId = runId,
            category = EventCategory.STATUS,
            payload = "factory_step kind=${FactoryStepKind.REPAIR} state=$state verification=$verification",
            dagId = dagId,
            promptFingerprint = promptFingerprint
        )
    }

    fun recordArtifactReady(
        runId: String,
        exportPath: String,
        dagId: String,
        promptFingerprint: String
    ) {
        record(
            runId = runId,
            category = EventCategory.TOOL_CALL,
            payload = "factory_artifact_ready export=$exportPath",
            dagId = dagId,
            promptFingerprint = promptFingerprint
        )
    }

    fun recordEvidenceCompletion(
        runId: String,
        evidencePath: String,
        dagId: String,
        promptFingerprint: String
    ) {
        record(
            runId = runId,
            category = EventCategory.STATUS,
            payload = "factory_step kind=${FactoryStepKind.EVIDENCE} state=COMPLETED path=$evidencePath",
            dagId = dagId,
            promptFingerprint = promptFingerprint
        )
    }

    fun recordCiStep(
        runId: String,
        state: String,
        dagId: String,
        promptFingerprint: String
    ) {
        record(
            runId = runId,
            category = EventCategory.STATUS,
            payload = "factory_step kind=${FactoryStepKind.CI} state=$state",
            dagId = dagId,
            promptFingerprint = promptFingerprint
        )
    }

    fun recordDeployment(
        runId: String,
        state: String,
        dagId: String,
        promptFingerprint: String
    ) {
        record(
            runId = runId,
            category = EventCategory.STATUS,
            payload = "factory_deployment state=$state",
            dagId = dagId,
            promptFingerprint = promptFingerprint
        )
    }

    fun recordPreview(
        runId: String,
        state: String,
        impactedSymbols: Int,
        browserStatus: String,
        dagId: String,
        promptFingerprint: String
    ) {
        record(
            runId = runId,
            category = EventCategory.VERIFICATION,
            payload = "factory_preview state=$state impacted_symbols=$impactedSymbols browser=$browserStatus",
            dagId = dagId,
            promptFingerprint = promptFingerprint
        )
    }

    fun recordAssetReady(
        runId: String,
        assetPath: String,
        dagId: String,
        promptFingerprint: String
    ) {
        record(
            runId = runId,
            category = EventCategory.TOOL_CALL,
            payload = "factory_asset_ready path=$assetPath",
            dagId = dagId,
            promptFingerprint = promptFingerprint
        )
    }

    fun recordAssetCompletion(
        runId: String,
        assetPath: String,
        dagId: String,
        promptFingerprint: String
    ) {
        record(
            runId = runId,
            category = EventCategory.STATUS,
            payload = "factory_step kind=${FactoryStepKind.ASSET} state=COMPLETED path=$assetPath",
            dagId = dagId,
            promptFingerprint = promptFingerprint
        )
    }

    fun recordAssetWarning(
        runId: String,
        failureType: String,
        dagId: String,
        promptFingerprint: String
    ) {
        record(
            runId = runId,
            category = EventCategory.WARNING,
            payload = "factory_asset_skipped type=$failureType",
            dagId = dagId,
            promptFingerprint = promptFingerprint
        )
    }

    fun recordAssetSkipped(
        runId: String,
        dagId: String,
        promptFingerprint: String
    ) {
        record(
            runId = runId,
            category = EventCategory.STATUS,
            payload = "factory_step kind=${FactoryStepKind.ASSET} state=SKIPPED_SOFT_FAIL",
            dagId = dagId,
            promptFingerprint = promptFingerprint
        )
    }

    fun recordCompletion(
        runId: String,
        projectId: String,
        evidencePath: String,
        dagId: String,
        promptFingerprint: String
    ) {
        record(
            runId = runId,
            category = EventCategory.COMPLETION,
            payload = "factory_completed project=$projectId evidence=$evidencePath",
            dagId = dagId,
            promptFingerprint = promptFingerprint
        )
    }

    fun recordCompletionFailure(
        runId: String,
        failureType: String,
        dagId: String,
        promptFingerprint: String
    ) {
        record(
            runId = runId,
            category = EventCategory.FAILURE,
            payload = "factory_completion_registration_failed type=$failureType",
            dagId = dagId,
            promptFingerprint = promptFingerprint
        )
    }

    private fun record(
        runId: String,
        category: EventCategory,
        payload: String,
        dagId: String? = null,
        promptFingerprint: String? = null
    ) {
        publisher.publish(
            runId = runId,
            role = ExecutionRole.SYSTEM,
            category = category,
            state = stateFor(category, payload),
            payload = buildString {
                promptFingerprint?.takeIf { it.isNotBlank() }?.let { append("prompt_fingerprint=$it ") }
                append(payload)
            },
            projectId = runId,
            dagId = dagId,
            requirement = promptFingerprint
        )
    }

    private fun stateFor(category: EventCategory, payload: String): RunState = when {
        category == EventCategory.FAILURE -> RunState.FAILED
        category == EventCategory.COMPLETION -> RunState.COMPLETE
        category == EventCategory.VERIFICATION || category == EventCategory.TEST -> RunState.REVIEW_REQUIRED
        payload.contains("state=PLANNED") || payload.contains("kind=PLAN") -> RunState.PLANNING
        payload.contains("state=SKIPPED") -> RunState.WAITING
        else -> RunState.RUNNING
    }
}
