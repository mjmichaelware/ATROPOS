/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.telemetry

import org.json.JSONArray
import org.json.JSONObject

/**
 * ATOM EX4: ESLINT STATIC ANALYSIS AND ERROR LINTER
 * Path: src/main/kotlin/atropos/core/telemetry/EslintStaticAnalysisHarvester.kt
 *
 * Runs static code analysis over JavaScript and TypeScript source files,
 * harvesting linter defects, computing penalty scores, and suggesting quick-fixes.
 */
enum class LinterSeverity(val level: Int) {
    WARNING(1),
    ERROR(2)
}

data class LinterDefect(
    val filePath: String,
    val line: Int,
    val column: Int,
    val ruleId: String,
    val message: String,
    val severity: LinterSeverity,
    val fixable: Boolean = false
)

data class LinterHarvestReport(
    val totalFilesAudited: Int,
    val defectCount: Int,
    val errorCount: Int,
    val warningCount: Int,
    val defectPenaltyScore: Double,
    val defects: List<LinterDefect>
)

class EslintStaticAnalysisHarvester(
    private val warningPenaltyWeight: Double = 0.5,
    private val errorPenaltyWeight: Double = 2.0
) {
    fun parseEslintJsonReport(rawJson: String): LinterHarvestReport {
        val defects = mutableListOf<LinterDefect>()
        val array = try { JSONArray(rawJson) } catch (_: Exception) { JSONArray() }

        var errors = 0
        var warnings = 0

        for (i in 0 until array.length()) {
            val fileObj = array.optJSONObject(i) ?: continue
            val filePath = fileObj.optString("filePath")
            val messages = fileObj.optJSONArray("messages") ?: continue

            for (j in 0 until messages.length()) {
                val msgObj = messages.optJSONObject(j) ?: continue
                val ruleId = msgObj.optString("ruleId", "unknown-rule")
                val line = msgObj.optInt("line", 1)
                val col = msgObj.optInt("column", 1)
                val msg = msgObj.optString("message")
                val sev = if (msgObj.optInt("severity") == 2) LinterSeverity.ERROR else LinterSeverity.WARNING
                val fix = msgObj.has("fix")

                if (sev == LinterSeverity.ERROR) errors++ else warnings++

                defects.add(
                    LinterDefect(
                        filePath = filePath,
                        line = line,
                        column = col,
                        ruleId = ruleId,
                        message = msg,
                        severity = sev,
                        fixable = fix
                    )
                )
            }
        }

        val penalty = (errors * errorPenaltyWeight) + (warnings * warningPenaltyWeight)

        return LinterHarvestReport(
            totalFilesAudited = array.length(),
            defectCount = defects.size,
            errorCount = errors,
            warningCount = warnings,
            defectPenaltyScore = penalty,
            defects = defects
        )
    }

    companion object {
        val defaultHarvester = EslintStaticAnalysisHarvester()
    }
}
