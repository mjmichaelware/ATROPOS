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
        "ZeroEntropyCertificateEmitter"
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

    private val Path.nameWithoutExtension: String
        get() {
            val fn = fileName?.toString() ?: ""
            val idx = fn.lastIndexOf('.')
            return if (idx > 0) fn.substring(0, idx) else fn
        }
}
