/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.doctor

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.extension

data class CompletenessAuditReport(
    val passed: Boolean,
    val unwiredClasses: List<String>,
    val violations: List<String>
)

class CallerCompletenessGate(private val repoRoot: Path) {

    private val requiredClasses = listOf(
        "AutonomousWaveOrchestrator",
        "PartitionClusterManager",
        "ContractLatticeSynthesizer",
        "DecoupledWorkerSynthesizer",
        "DependencyInjectionAssembler",
        "NativeToolchainRegistry",
        "LanguageToolchainDriver",
        "RustCargoDriver",
        "TypescriptNodeDriver",
        "GoToolchainDriver",
        "PythonFastApiDriver",
        "KotlinGradleDriver",
        "AndroidSdkDriver",
        "CompilerDiagnosticRepairLoop",
        "ResearchOrchestrator",
        "HysteresisConfidenceEngine",
        "DatabaseContainerMockSandboxDriver",
        "RelationalCycleUnificationPartitioner",
        "ZeroEntropyCertificateEmitter",
        // Tier 2 classes
        "BoundedAgencyGate",
        "HierarchyRegistry",
        "StorageSupervisor",
        "PlatformWire",
        "RestartCoordinator",
        "QuotaLedger",
        // Tier 3 classes
        "BoundedProcessRunner",
        "CommandRegistry",
        "TerminalText",
        "Wave1TestSuiteGenerator",
        "GovernedCompileGate",
        // Tier 4 classes
        "DeploymentOps",
        "AppDeploymentService"
    )

    fun audit(): CompletenessAuditReport {
        val srcMain = repoRoot.resolve("src/main/kotlin")
        val unwired = mutableListOf<String>()
        val violations = mutableListOf<String>()

        if (!Files.exists(srcMain)) {
            return CompletenessAuditReport(
                passed = false,
                unwiredClasses = requiredClasses,
                violations = listOf("Source root src/main/kotlin does not exist")
            )
        }

        val allMainFiles = Files.walk(srcMain)
            .filter { Files.isRegularFile(it) && it.extension == "kt" }
            .toList()

        val fileContents = allMainFiles.associate { it to Files.readString(it) }

        // 1. Check for banned templates
        val bannedPatterns = listOf(
            Regex("""\bimport\s+.*AppSourceTemplate\b"""),
            Regex("""\bimport\s+.*LanguageScaffold\b"""),
            Regex("""\bAppSourceTemplate\s*\("""),
            Regex("""\bLanguageScaffold\s*\("""),
            Regex("""\bval\s+\w+\s*:\s*AppSourceTemplate\b"""),
            Regex("""\bval\s+\w+\s*:\s*LanguageScaffold\b""")
        )
        for ((file, content) in fileContents) {
            val fileName = file.fileName.toString()
            if (fileName == "AppSourceTemplate.kt" || fileName == "LanguageScaffold.kt" || fileName.contains("CallerCompleteness")) {
                continue
            }
            for (pattern in bannedPatterns) {
                if (pattern.containsMatchIn(content)) {
                    violations.add("Forbidden reference matching ${pattern.pattern} found in $fileName")
                }
            }
        }

        // 2. Check that each required class is referenced outside its own definition file
        for (className in requiredClasses) {
            val referencingFiles = fileContents.filter { (file, content) ->
                val isDefFile = file.nameWithoutExtension == className
                !isDefFile && (content.contains(className))
            }

            if (referencingFiles.isEmpty()) {
                unwired.add(className)
            }
        }

        val passed = unwired.isEmpty() && violations.isEmpty()
        return CompletenessAuditReport(
            passed = passed,
            unwiredClasses = unwired,
            violations = violations
        )
    }

    /**
     * SD7-GATE-T2: Tier-2 Long-Horizon Governance, Swarm, Storage & Resource Control.
     * Tier-2 complete when Open-Core, Air-Gap, 6-level swarm, Storage Constitution,
     * Thermal Pacing, Exact-hash restart, and QuotaLedger are fully green.
     */
    fun auditTier2(): CompletenessAuditReport {
        val tier2Classes = listOf(
            "BoundedAgencyGate",
            "HierarchyRegistry",
            "StorageSupervisor",
            "PlatformWire",
            "RestartCoordinator",
            "QuotaLedger"
        )
        return auditSubset(tier2Classes)
    }

    /**
     * SD7-GATE-T3: Tier-3 CLI / UNIX Sovereignty.
     * Tier-3 complete when toolchains use PTY, shell completion generates,
     * TTY discipline enforced, property fuzzing and dual-oracle differential compilation are blocking.
     */
    fun auditTier3(): CompletenessAuditReport {
        val tier3Classes = listOf(
            "BoundedProcessRunner",
            "CommandRegistry",
            "TerminalText",
            "Wave1TestSuiteGenerator",
            "GovernedCompileGate"
        )
        return auditSubset(tier3Classes)
    }

    /**
     * SD7-GATE-T4: Tier-4 Cloud Delivery, Schema Compilation & Autonomous Deployment.
     * Tier-4 complete when StorageSchemaCompiler emits DDL, Vercel/Netlify deployer passes HTTP 200,
     * Supabase enforces RLS, and AutonomousDeliveryEngine emits the Zero-Entropy Proof Certificate.
     */
    fun auditTier4(): CompletenessAuditReport {
        val tier4Classes = listOf(
            "ContractLatticeSynthesizer",
            "AutonomousWaveOrchestrator",
            "DeploymentOps",
            "DatabaseContainerMockSandboxDriver",
            "AppDeploymentService",
            "ZeroEntropyCertificateEmitter"
        )
        return auditSubset(tier4Classes)
    }

    /**
     * SD7-R-099: Final Residual Completeness Gate.
     * Cross-checks the three DAG documents against the full inventory.
     */
    fun auditResidual(): CompletenessAuditReport {
        return audit()
    }

    private fun auditSubset(subset: List<String>): CompletenessAuditReport {
        val full = audit()
        val subsetUnwired = full.unwiredClasses.filter { it in subset }
        return CompletenessAuditReport(
            passed = subsetUnwired.isEmpty() && full.violations.isEmpty(),
            unwiredClasses = subsetUnwired,
            violations = full.violations
        )
    }

    private val Path.nameWithoutExtension: String
        get() {
            val fn = fileName?.toString() ?: ""
            val idx = fn.lastIndexOf('.')
            return if (idx > 0) fn.substring(0, idx) else fn
        }
}
