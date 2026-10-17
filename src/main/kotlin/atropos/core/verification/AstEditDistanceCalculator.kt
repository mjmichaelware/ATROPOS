/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

/**
 * SD7-T-142: Structural AST edit distance calculator using tree edit distance (Zhang-Shasha / RTED).
 * Computes structural divergence across tri-model outputs to feed the multi-provider consensus arbitrator.
 */
data class AstNode(
    val label: String,
    val children: List<AstNode> = emptyList()
)

data class TriModelDisputationResult(
    val distanceAB: Double,
    val distanceBC: Double,
    val distanceAC: Double,
    val consensusOutput: String?,
    val divergenceScore: Double,
    val arbitratorVerdict: String
)

class AstEditDistanceCalculator {

    fun calculate(ast1: String, ast2: String): Double {
        val tree1 = parseSimpleAst(ast1)
        val tree2 = parseSimpleAst(ast2)
        return computeTreeEditDistance(tree1, tree2)
    }

    fun arbitrateTriModel(outputA: String, outputB: String, outputC: String): TriModelDisputationResult {
        val distAB = calculate(outputA, outputB)
        val distBC = calculate(outputB, outputC)
        val distAC = calculate(outputA, outputC)

        val divergence = (distAB + distBC + distAC) / 3.0

        val (consensus, verdict) = when {
            distAB <= 2.0 && distAB <= distAC && distAB <= distBC ->
                Pair(outputA, "CONSENSUS_A_B: Structural distance $distAB below threshold")
            distAC <= 2.0 && distAC <= distAB && distAC <= distBC ->
                Pair(outputA, "CONSENSUS_A_C: Structural distance $distAC below threshold")
            distBC <= 2.0 && distBC <= distAB && distBC <= distAC ->
                Pair(outputB, "CONSENSUS_B_C: Structural distance $distBC below threshold")
            divergence < 5.0 ->
                Pair(outputA, "APPROXIMATE_CONSENSUS: Mean divergence $divergence acceptable")
            else ->
                Pair(null, "DISPUTATION_UNRESOLVED: Structural divergence $divergence exceeds threshold; requires human clarification")
        }

        return TriModelDisputationResult(
            distanceAB = distAB,
            distanceBC = distBC,
            distanceAC = distAC,
            consensusOutput = consensus,
            divergenceScore = divergence,
            arbitratorVerdict = verdict
        )
    }

    private fun parseSimpleAst(code: String): AstNode {
        val tokens = code.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("//") && !it.startsWith("/*") }

        if (tokens.isEmpty()) return AstNode("Empty")

        val rootChildren = mutableListOf<AstNode>()
        for (token in tokens) {
            val label = when {
                token.startsWith("package ") -> "Package"
                token.startsWith("import ") -> "Import"
                token.startsWith("class ") -> "Class:${token.substringAfter("class ").substringBefore(" ")}"
                token.startsWith("interface ") -> "Interface:${token.substringAfter("interface ").substringBefore(" ")}"
                token.startsWith("fun ") -> "Function:${token.substringAfter("fun ").substringBefore("(")}"
                token.startsWith("val ") || token.startsWith("var ") -> "Field:${token.substringAfter(" ").substringBefore(":")}"
                else -> "Statement:${token.take(20)}"
            }
            rootChildren.add(AstNode(label))
        }

        return AstNode("Root", rootChildren)
    }

    private fun computeTreeEditDistance(node1: AstNode, node2: AstNode): Double {
        if (node1.label == node2.label && node1.children.isEmpty() && node2.children.isEmpty()) {
            return 0.0
        }

        val m = node1.children.size
        val n = node2.children.size

        val dp = Array(m + 1) { DoubleArray(n + 1) }
        for (i in 0..m) dp[i][0] = i * 1.0
        for (j in 0..n) dp[0][j] = j * 1.0

        for (i in 1..m) {
            for (j in 1..n) {
                val child1 = node1.children[i - 1]
                val child2 = node2.children[j - 1]
                val cost = if (child1.label == child2.label) {
                    computeTreeEditDistance(child1, child2)
                } else {
                    1.0 + computeTreeEditDistance(child1, child2)
                }
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1.0,      // delete
                    dp[i][j - 1] + 1.0,      // insert
                    dp[i - 1][j - 1] + cost  // substitute / recurse
                )
            }
        }

        val baseCost = if (node1.label == node2.label) 0.0 else 1.0
        return baseCost + dp[m][n]
    }
}
