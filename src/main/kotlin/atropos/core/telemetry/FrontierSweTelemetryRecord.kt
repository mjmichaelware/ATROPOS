/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.telemetry

import java.security.MessageDigest
import java.time.Instant

/**
 * SD7-T1-TEL01: Dynamic Telemetry Data Models for Frontier SWE Process Supervision.
 *
 * Encapsulates twelve discrete telemetry types across multi-file coding agent workflows:
 * 1. Human preference deltas
 * 2. Thought trace process graphs
 * 3. Agentic shell executions
 * 4. AST mutations
 * 5. Compiler diagnostics
 * 6. Static analysis defects
 * 7. Test assertion diffs
 * 8. Mutation kill ratios
 * 9. DOM accessibility trees
 * 10. Resource gas expenditures
 * 11. CDCL conflict clauses
 * 12. CAS Merkle receipts
 */
enum class TelemetryKind {
    HUMAN_PREFERENCE_DELTA,
    THOUGHT_TRACE_PROCESS_GRAPH,
    AGENTIC_SHELL_EXECUTION,
    AST_MUTATION,
    COMPILER_DIAGNOSTIC,
    STATIC_ANALYSIS_DEFECT,
    TEST_ASSERTION_DIFF,
    MUTATION_KILL_RATIO,
    DOM_ACCESSIBILITY_TREE,
    RESOURCE_GAS_EXPENDITURE,
    CDCL_CONFLICT_CLAUSE,
    CAS_MERKLE_RECEIPT
}

sealed interface TelemetryPayload {
    fun kind(): TelemetryKind
    fun toJson(): String
}

data class HumanPreferenceDelta(
    val dwellTimeMs: Long,
    val approved: Boolean,
    val diffHunk: String,
    val tokenDelta: Int
) : TelemetryPayload {
    override fun kind(): TelemetryKind = TelemetryKind.HUMAN_PREFERENCE_DELTA
    override fun toJson(): String = """{"dwellTimeMs":$dwellTimeMs,"approved":$approved,"diffHunk":"${diffHunk.replace("\"", "\\\"")}","tokenDelta":$tokenDelta}"""
}

data class ThoughtTraceProcessGraph(
    val traceId: String,
    val stepIndex: Int,
    val premiseTokens: Int,
    val deductionTokens: Int,
    val confidence: Double,
    val backtracks: Int,
    val rejectedHypotheses: List<String>,
    val stepHash: String
) : TelemetryPayload {
    override fun kind(): TelemetryKind = TelemetryKind.THOUGHT_TRACE_PROCESS_GRAPH
    override fun toJson(): String = """{"traceId":"$traceId","stepIndex":$stepIndex,"premiseTokens":$premiseTokens,"deductionTokens":$deductionTokens,"confidence":$confidence,"backtracks":$backtracks,"stepHash":"$stepHash"}"""
}

data class AgenticShellExecution(
    val argv: List<String>,
    val exitCode: Int,
    val stdoutTruncated: String,
    val stderrTruncated: String,
    val durationMs: Long
) : TelemetryPayload {
    override fun kind(): TelemetryKind = TelemetryKind.AGENTIC_SHELL_EXECUTION
    override fun toJson(): String = """{"argv":${argv.map { "\"$it\"" }},"exitCode":$exitCode,"durationMs":$durationMs}"""
}

data class AstMutationRecord(
    val filePath: String,
    val nodesAdded: Int,
    val nodesDeleted: Int,
    val nodesModified: Int,
    val structuralDistance: Double
) : TelemetryPayload {
    override fun kind(): TelemetryKind = TelemetryKind.AST_MUTATION
    override fun toJson(): String = """{"filePath":"$filePath","nodesAdded":$nodesAdded,"nodesDeleted":$nodesDeleted,"nodesModified":$nodesModified,"structuralDistance":$structuralDistance}"""
}

data class CompilerDiagnosticRecord(
    val errorCode: String,
    val filePath: String,
    val line: Int,
    val column: Int,
    val enclosingSymbol: String,
    val message: String,
    val fixSuggestions: List<String> = emptyList()
) : TelemetryPayload {
    override fun kind(): TelemetryKind = TelemetryKind.COMPILER_DIAGNOSTIC
    override fun toJson(): String = """{"errorCode":"$errorCode","filePath":"$filePath","line":$line,"column":$column,"symbol":"$enclosingSymbol","message":"${message.replace("\"", "\\\"")}"}"""
}

data class StaticAnalysisDefectRecord(
    val linter: String,
    val ruleId: String,
    val severity: String,
    val complexityMetrics: Int,
    val unsafeFlag: Boolean,
    val penalty: Double
) : TelemetryPayload {
    override fun kind(): TelemetryKind = TelemetryKind.STATIC_ANALYSIS_DEFECT
    override fun toJson(): String = """{"linter":"$linter","ruleId":"$ruleId","severity":"$severity","complexityMetrics":$complexityMetrics,"unsafe":$unsafeFlag,"penalty":$penalty}"""
}

data class TestAssertionDiffRecord(
    val testName: String,
    val assertionType: String,
    val expectedSnippet: String,
    val actualSnippet: String,
    val stackTraceSnippet: String
) : TelemetryPayload {
    override fun kind(): TelemetryKind = TelemetryKind.TEST_ASSERTION_DIFF
    override fun toJson(): String = """{"testName":"$testName","assertionType":"$assertionType","expected":"${expectedSnippet.take(100).replace("\"", "\\\"")}","actual":"${actualSnippet.take(100).replace("\"", "\\\"")}"}"""
}

data class MutationKillRatioRecord(
    val totalMutants: Int,
    val killedMutants: Int,
    val killRatio: Double,
    val vacuousAssertionDefect: Boolean,
    val survivingMutantIds: List<String> = emptyList()
) : TelemetryPayload {
    override fun kind(): TelemetryKind = TelemetryKind.MUTATION_KILL_RATIO
    override fun toJson(): String = """{"totalMutants":$totalMutants,"killedMutants":$killedMutants,"killRatio":$killRatio,"vacuous":$vacuousAssertionDefect}"""
}

data class DomAccessibilityTreeRecord(
    val domHierarchyHash: String,
    val ariaTreeHash: String,
    val boundingBoxCount: Int,
    val consoleErrorCount: Int,
    val failedNetworkRequests: Int
) : TelemetryPayload {
    override fun kind(): TelemetryKind = TelemetryKind.DOM_ACCESSIBILITY_TREE
    override fun toJson(): String = """{"domHash":"$domHashHierarchy","ariaHash":"$ariaTreeHash","bboxes":$boundingBoxCount,"consoleErrors":$consoleErrorCount}"""
    private val domHashHierarchy: String get() = domHierarchyHash
}

data class ResourceGasExpenditureRecord(
    val cpuMonotonicMs: Long,
    val peakRssBytes: Long,
    val estimatedJoules: Double,
    val thermalState: String,
    val hostArch: String
) : TelemetryPayload {
    override fun kind(): TelemetryKind = TelemetryKind.RESOURCE_GAS_EXPENDITURE
    override fun toJson(): String = """{"cpuMs":$cpuMonotonicMs,"peakRssBytes":$peakRssBytes,"joules":$estimatedJoules,"thermalState":"$thermalState","arch":"$hostArch"}"""
}

data class CdclConflictClauseRecord(
    val clauseDigest: String,
    val minimalUnsatCoreSize: Int,
    val antiUnificationRule: String,
    val solveTimeMs: Long,
    val memoryBytes: Long
) : TelemetryPayload {
    override fun kind(): TelemetryKind = TelemetryKind.CDCL_CONFLICT_CLAUSE
    override fun toJson(): String = """{"clauseDigest":"$clauseDigest","unsatCoreSize":$minimalUnsatCoreSize,"solveTimeMs":$solveTimeMs}"""
}

data class CasMerkleReceiptRecord(
    val h0PromptHash: String,
    val chunkMerkleRoot: String,
    val leafCount: Int,
    val casEvidencePointer: String,
    val timestampMs: Long = System.currentTimeMillis()
) : TelemetryPayload {
    override fun kind(): TelemetryKind = TelemetryKind.CAS_MERKLE_RECEIPT
    override fun toJson(): String = """{"h0":"$h0PromptHash","chunkRoot":"$chunkMerkleRoot","leafCount":$leafCount,"casPointer":"$casEvidencePointer"}"""
}

/**
 * Top-level atomic telemetry record containing header metadata, cryptographic proof,
 * and typed process-supervision payload.
 */
data class FrontierSweTelemetryRecord(
    val recordId: String,
    val timestampEpochMs: Long = System.currentTimeMillis(),
    val instanceId: String,
    val h0PromptLineage: String,
    val kind: TelemetryKind,
    val payloadJson: String,
    val rewardSignal: Double,
    val payloadHash: String = sha256Hex(payloadJson)
) {
    fun canonicalJson(): String = buildString {
        append("{")
        append("\"recordId\":\"$recordId\",")
        append("\"timestamp\":$timestampEpochMs,")
        append("\"instanceId\":\"$instanceId\",")
        append("\"h0Lineage\":\"$h0PromptLineage\",")
        append("\"kind\":\"${kind.name}\",")
        append("\"reward\":$rewardSignal,")
        append("\"payloadHash\":\"$payloadHash\",")
        append("\"payload\":$payloadJson")
        append("}")
    }

    companion object {
        fun create(
            instanceId: String,
            h0PromptLineage: String,
            payload: TelemetryPayload,
            rewardSignal: Double = 0.0
        ): FrontierSweTelemetryRecord {
            val json = payload.toJson()
            val id = "tel-${System.currentTimeMillis()}-${(1000..9999).random()}"
            return FrontierSweTelemetryRecord(
                recordId = id,
                instanceId = instanceId,
                h0PromptLineage = h0PromptLineage,
                kind = payload.kind(),
                payloadJson = json,
                rewardSignal = rewardSignal
            )
        }

        fun sha256Hex(str: String): String {
            val md = MessageDigest.getInstance("SHA-256")
            val bytes = md.digest(str.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
