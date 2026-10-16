/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

import java.nio.file.Files
import java.nio.file.Path

data class HollowDefect(
    val line: Int,
    val pattern: String,
    val message: String
)

class AstStubHollowScanner {
    fun scanSource(filePath: Path, content: String): List<HollowDefect> {
        val defects = mutableListOf<HollowDefect>()
        val lines = content.lines()

        for (i in lines.indices) {
            val lineNum = i + 1
            val lineText = lines[i]
            val trimmed = lineText.trim()

            if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                continue
            }

            if (trimmed.contains("TODO()")) {
                defects.add(HollowDefect(lineNum, "TODO()", "Forbidden hollow placeholder TODO() detected"))
            }

            if (trimmed.contains("NotImplementedError")) {
                defects.add(HollowDefect(lineNum, "NotImplementedError", "Forbidden hollow placeholder NotImplementedError detected"))
            }

            if (trimmed.contains("throw RuntimeException(\"not implemented\")", ignoreCase = true) ||
                trimmed.contains("throw Exception(\"not implemented\")", ignoreCase = true)) {
                defects.add(HollowDefect(lineNum, "not implemented exception", "Forbidden stub exception throw detected"))
            }

            if (trimmed.matches(Regex(""".*catch\s*\([^)]*\)\s*\{\s*\}.*"""))) {
                defects.add(HollowDefect(lineNum, "empty catch block", "Forbidden empty catch block detected"))
            }
        }

        return defects
    }
}
