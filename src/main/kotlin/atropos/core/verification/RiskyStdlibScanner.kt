// SPDX-License-Identifier: AGPL-3.0-only
package atropos.core.verification

import java.io.File

data class RiskyUsage(val file: File, val line: Int, val pattern: String)

object RiskyStdlibScanner {
    fun scan(files: List<File>): List<RiskyUsage> {
        val usages = mutableListOf<RiskyUsage>()
        val patterns = listOf(
            ".takeLast(" to "Sequence.takeLast",
            "kotlin.io.path" to "kotlin.io.path",
            "import kotlinx" to "kotlinx imports above target",
            "import java." to "Java APIs above target"
        )
        
        for (file in files) {
            if (!file.isFile) continue
            file.useLines { lines ->
                lines.forEachIndexed { index, text ->
                    for ((strPattern, name) in patterns) {
                        if (text.contains(strPattern)) {
                            usages.add(RiskyUsage(file, index + 1, name))
                        }
                    }
                }
            }
        }
        
        return usages
    }
}
