/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.neurosymbolic

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

data class ResidualObligation(
    val obligationId: String,
    val ruleHash: String,
    val rationale: String
)

class ResidualObligationLedger(private val ledgerFile: Path) {

    fun recordVeto(rationale: String): ResidualObligation {
        val timestamp = System.currentTimeMillis()
        val raw = "$rationale:$timestamp"

        val digest = MessageDigest.getInstance("SHA-256")
        val obligationId = digest.digest(raw.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        val ruleHash = digest.digest(rationale.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

        val obligation = ResidualObligation(obligationId, ruleHash, rationale)

        val jsonLine = buildString {
            append("{\"obligationId\":\"").append(obligation.obligationId).append("\",")
            append("\"ruleHash\":\"").append(obligation.ruleHash).append("\",")
            append("\"rationale\":\"").append(obligation.rationale.replace("\"", "\\\"")).append("\"}\n")
        }

        if (ledgerFile.parent != null) {
            Files.createDirectories(ledgerFile.parent)
        }
        Files.writeString(
            ledgerFile,
            jsonLine,
            StandardOpenOption.CREATE,
            StandardOpenOption.APPEND
        )

        return obligation
    }

    fun activeObligations(): List<ResidualObligation> {
        if (!Files.exists(ledgerFile)) return emptyList()

        val list = mutableListOf<ResidualObligation>()
        val lines = Files.readAllLines(ledgerFile)
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                val obligationId = trimmed.substringAfter("\"obligationId\":\"").substringBefore("\"")
                val ruleHash = trimmed.substringAfter("\"ruleHash\":\"").substringBefore("\"")
                val rationale = trimmed.substringAfter("\"rationale\":\"").substringBefore("\"")
                list.add(ResidualObligation(obligationId, ruleHash, rationale))
            }
        }
        return list
    }

    /**
     * SD7-R-016: Ungrounded-requirement tracker blocking Phase-100 until all vetoes and obligations are resolved.
     */
    fun hasUngroundedRequirements(): Boolean = activeObligations().isNotEmpty()

    fun isPhase100Blocked(): Boolean = hasUngroundedRequirements()
}
