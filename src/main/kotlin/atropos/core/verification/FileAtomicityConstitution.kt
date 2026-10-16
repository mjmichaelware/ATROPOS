/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

import java.nio.file.Files
import java.nio.file.Path

data class FileAtomicityReport(
    val loc: Int,
    val valid: Boolean,
    val reason: String?
)

object FileAtomicityConstitution {
    const val SOFT_LIMIT_LOC: Int = 350
    const val HARD_LIMIT_LOC: Int = 500

    fun validate(file: Path): FileAtomicityReport {
        if (!Files.isRegularFile(file)) {
            return FileAtomicityReport(0, true, null)
        }

        val allLines = Files.readAllLines(file)
        val totalLines = allLines.size

        if (totalLines > HARD_LIMIT_LOC) {
            return FileAtomicityReport(
                loc = totalLines,
                valid = false,
                reason = "LOC_EXCEEDED: Hard line limit ($HARD_LIMIT_LOC) exceeded ($totalLines lines)"
            )
        }

        val meaningfulLines = allLines.count { line ->
            val trimmed = line.trim()
            trimmed.isNotBlank() && !trimmed.startsWith("//") && !trimmed.startsWith("/*") && !trimmed.startsWith("*")
        }

        val content = allLines.joinToString("\n")
        val hasUi = content.contains("androidx.compose") || content.contains("javafx.") || content.contains("<html>") || content.contains("div className")
        val hasDb = content.contains("SELECT ") || content.contains("INSERT INTO") || content.contains("java.sql.") || content.contains("sqlite3_")
        if (hasUi && hasDb) {
            return FileAtomicityReport(
                loc = totalLines,
                valid = false,
                reason = "CONCERN_POLLUTION: File combines UI rendering with direct database queries"
            )
        }

        return FileAtomicityReport(
            loc = meaningfulLines,
            valid = true,
            reason = if (meaningfulLines > SOFT_LIMIT_LOC) "ADVISORY: Soft limit ($SOFT_LIMIT_LOC) exceeded ($meaningfulLines LOC)" else null
        )
    }
}
