package atropos.core.factory

import atropos.core.assets.AssetKind
import atropos.core.assets.AssetRequest
import atropos.core.assets.LocalAssetGenerator

class FactoryAssetHandler(
    private val assets: LocalAssetGenerator,
    private val recorder: FactoryRunEventRecorder
) {
    fun generateAssets(
        planSteps: List<FactoryStep>,
        projectName: String,
        redactedPrompt: String,
        projectTags: List<String>,
        runId: String,
        dagId: String,
        promptFingerprint: String
    ): Pair<List<String>, List<String>> {
        val assetFiles = mutableListOf<String>()
        val softFailures = mutableListOf<String>()

        if (planSteps.any { it.kind == FactoryStepKind.ASSET }) {
            runCatching {
                assets.generate(
                    AssetRequest(
                        kind = AssetKind.SVG,
                        name = projectName,
                        prompt = redactedPrompt,
                        tags = projectTags + listOf("factory", "local")
                    )
                )
            }.onSuccess { artifact ->
                assetFiles += artifact.file.path
                recorder.recordAssetReady(
                    runId = runId,
                    assetPath = artifact.file.path,
                    dagId = dagId,
                    promptFingerprint = promptFingerprint
                )
                recorder.recordAssetCompletion(
                    runId = runId,
                    assetPath = artifact.file.path,
                    dagId = dagId,
                    promptFingerprint = promptFingerprint
                )
            }.onFailure { failure ->
                softFailures += "asset=SKIPPED_SOFT_FAIL:${failure.javaClass.simpleName.lowercase().replace(Regex("[^a-z0-9]+"), "_").take(80)}"
                recorder.recordAssetWarning(
                    runId = runId,
                    failureType = failure.javaClass.simpleName,
                    dagId = dagId,
                    promptFingerprint = promptFingerprint
                )
                recorder.recordAssetSkipped(
                    runId = runId,
                    dagId = dagId,
                    promptFingerprint = promptFingerprint
                )
            }
        }

        return Pair(assetFiles, softFailures)
    }
}
