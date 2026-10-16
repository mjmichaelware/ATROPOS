/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.neurosymbolic

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension

data class ContractCoverageReport(
    val verifiedFunctions: Int,
    val missingContracts: Int,
    val passed: Boolean
)

class HoareLogicAnnotationScanner {

    fun checkCoverage(worktree: Path): ContractCoverageReport {
        if (!Files.exists(worktree)) {
            return ContractCoverageReport(0, 0, true)
        }

        val ktFiles = Files.walk(worktree)
            .filter { Files.isRegularFile(it) && it.extension == "kt" }
            .toList()

        var verified = 0
        var missing = 0

        for (file in ktFiles) {
            val content = Files.readString(file)
            val lines = content.lines()

            for (i in lines.indices) {
                val line = lines[i].trim()
                if (line.startsWith("fun ") && !line.startsWith("private fun ")) {
                    // Check next 20 lines for contract clauses
                    val slice = lines.drop(i).take(20).joinToString("\n")
                    val hasContract = slice.contains("require(") ||
                            slice.contains("check(") ||
                            slice.contains("assert(") ||
                            slice.contains("invariant(")

                    if (hasContract) {
                        verified++
                    } else {
                        missing++
                    }
                }
            }
        }

        val passed = missing == 0
        return ContractCoverageReport(
            verifiedFunctions = verified,
            missingContracts = missing,
            passed = passed
        )
    }
}
