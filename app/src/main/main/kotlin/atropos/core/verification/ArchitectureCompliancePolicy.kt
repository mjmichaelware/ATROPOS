/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

import java.io.File

/** Configurable thresholds and concern-pair rules for atomicity scanning. */
data class ArchitectureCompliancePolicy(
    val defaultLineThreshold: Int = DEFAULT_LINE_THRESHOLD,
    val pathLineThresholds: Map<String, Int> = defaultPathLineThresholds,
    val forbiddenPairs: List<Pair<ArchitectureConcern, ArchitectureConcern>> = defaultForbiddenPairs
) {
    fun thresholdFor(file: File): Int {
        val path = file.invariantSeparatorsPath()
        return pathLineThresholds
            .filterKeys { suffix -> path.endsWith(suffix) || path == suffix }
            .values
            .minOrNull()
            ?: defaultLineThreshold
    }

    private fun File.invariantSeparatorsPath(): String = path.replace(File.separatorChar, '/')

    private companion object {
        const val DEFAULT_LINE_THRESHOLD = 400

        val defaultPathLineThresholds = mapOf(
            "src/main/kotlin/atropos/cli/commands/AgentCommand.kt" to 400,
            "src/main/kotlin/atropos/dloi/DloiService.kt" to 300
        )

        val defaultForbiddenPairs = listOf(
            ArchitectureConcern.ROUTING to ArchitectureConcern.RENDERING,
            ArchitectureConcern.ROUTING to ArchitectureConcern.SESSION_STATE,
            ArchitectureConcern.TRANSPORT to ArchitectureConcern.NORMALIZATION,
            ArchitectureConcern.VERIFICATION to ArchitectureConcern.EXECUTION,
            ArchitectureConcern.SOURCE_LOADING to ArchitectureConcern.ADDRESS_PARSING,
            ArchitectureConcern.SOURCE_LOADING to ArchitectureConcern.RENDERING
        )
    }
}
