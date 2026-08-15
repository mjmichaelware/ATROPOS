// SPDX-License-Identifier: AGPL-3.0-only
package atropos.core.verification

data class BatchReport(
    val physicalLines: Int,
    val codeBearingLines: Int,
    val additions: Int,
    val deletions: Int
)

object BatchReporter {
    fun report(before: List<String>, after: List<String>): BatchReport {
        val physicalLines = after.size
        val codeBearingLines = after.count { it.isNotBlank() }
        
        val beforeFreq = before.groupingBy { it }.eachCount()
        val afterFreq = after.groupingBy { it }.eachCount()
        
        var additions = 0
        var deletions = 0
        
        val allLines = beforeFreq.keys + afterFreq.keys
        for (line in allLines) {
            val b = beforeFreq[line] ?: 0
            val a = afterFreq[line] ?: 0
            if (a > b) additions += (a - b)
            if (b > a) deletions += (b - a)
        }
        
        return BatchReport(physicalLines, codeBearingLines, additions, deletions)
    }
}
