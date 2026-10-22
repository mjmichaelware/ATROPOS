/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.memory

import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import org.json.JSONArray
import org.json.JSONObject
import atropos.core.supremacy.OpenCoreAirGapController
import atropos.core.supremacy.SecretNonInterferenceGuard
import atropos.core.supremacy.SecurityLabel

data class TrajectoryStep(
    val stepIndex: Int,
    val promptTokens: Int,
    val actionSummary: String,
    val exitCode: Int,
    val reward: Double
)

data class EpisodicMemoryRecord(
    val issueId: String,
    val benchmarkName: String,
    val durationMs: Long,
    val patchSha256: String,
    val finalReward: Double,
    val steps: List<TrajectoryStep>
)

class NeuralMemoryDopamineSync(
    val repoRoot: Path,
    val airGapGuard: OpenCoreAirGapController = OpenCoreAirGapController,
    val sanitizer: SecretNonInterferenceGuard = SecretNonInterferenceGuard()
) {
    private val memoryDir = repoRoot.resolve(".atropos/memory/dopamine")
    private val ledgerFile = memoryDir.resolve("trajectories.jsonl")

    init {
        Files.createDirectories(memoryDir)
    }

    fun recordTrajectory(record: EpisodicMemoryRecord): Path {
        val sanitizedSteps = record.steps.map { step ->
            val cleanSummary = sanitizer.sanitizeToLabel(step.actionSummary, SecurityLabel.INTERNAL).payload
            step.copy(actionSummary = cleanSummary)
        }
        val cleanRecord = record.copy(steps = sanitizedSteps)

        val json = JSONObject().apply {
            put("issueId", cleanRecord.issueId)
            put("benchmarkName", cleanRecord.benchmarkName)
            put("durationMs", cleanRecord.durationMs)
            put("patchSha256", cleanRecord.patchSha256)
            put("finalReward", cleanRecord.finalReward)
            put("steps", JSONArray().apply {
                for (step in cleanRecord.steps) {
                    put(JSONObject().apply {
                        put("stepIndex", step.stepIndex)
                        put("promptTokens", step.promptTokens)
                        put("actionSummary", step.actionSummary)
                        put("exitCode", step.exitCode)
                        put("reward", step.reward)
                    })
                }
            })
        }

        Files.writeString(
            ledgerFile,
            json.toString() + "\n",
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        )
        return ledgerFile
    }

    fun pushDopamineSync(hfRepoId: String = "mjmichaelware/Atropos-Dopamine", hfToken: String): Boolean {
        val mode = airGapGuard.currentMode()
        if (mode.airGapped || mode.isLocalOnly) {
            return false
        }
        if (hfToken.isBlank()) {
            return false
        }
        return try {
            val hfUri = URI("https://huggingface.co/api/datasets/$hfRepoId")
            airGapGuard.assertNetworkCallPermitted(hfUri)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun querySimilarFailureTrajectories(errorDiagnostic: String): List<EpisodicMemoryRecord> {
        if (!Files.exists(ledgerFile)) return emptyList()
        val results = mutableListOf<EpisodicMemoryRecord>()
        try {
            val lines = Files.readAllLines(ledgerFile)
            for (line in lines) {
                if (line.isBlank()) continue
                val json = JSONObject(line)
                val reward = json.optDouble("finalReward", 0.0)
                if (reward < 0.5) {
                    val stepsArr = json.optJSONArray("steps") ?: JSONArray()
                    val steps = mutableListOf<TrajectoryStep>()
                    for (i in 0 until stepsArr.length()) {
                        val s = stepsArr.getJSONObject(i)
                        steps.add(
                            TrajectoryStep(
                                stepIndex = s.getInt("stepIndex"),
                                promptTokens = s.getInt("promptTokens"),
                                actionSummary = s.getString("actionSummary"),
                                exitCode = s.getInt("exitCode"),
                                reward = s.getDouble("reward")
                            )
                        )
                    }
                    results.add(
                        EpisodicMemoryRecord(
                            issueId = json.getString("issueId"),
                            benchmarkName = json.getString("benchmarkName"),
                            durationMs = json.getLong("durationMs"),
                            patchSha256 = json.getString("patchSha256"),
                            finalReward = reward,
                            steps = steps
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Memory read resilience
        }
        return results
    }
}
