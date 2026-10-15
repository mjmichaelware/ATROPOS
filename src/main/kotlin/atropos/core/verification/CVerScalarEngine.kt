package atropos.core.verification

import atropos.core.toolchain.ToolchainResult
import java.nio.file.Path

class CVerScalarEngine {
    fun evaluate(compileResult: ToolchainResult, testResult: ToolchainResult, mutationKillRatio: Double): Int {
        if (compileResult.exitCode != 0) return 0
        if (testResult.exitCode != 0) return 0
        if (mutationKillRatio < 0.85) return 0
        return 1
    }
}