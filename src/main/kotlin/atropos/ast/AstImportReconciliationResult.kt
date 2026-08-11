package atropos.ast

import java.nio.file.Path

data class AstImportReconciliationResult(
    val file: Path,
    val packageName: String,
    val packagePathInvariantHolds: Boolean,
    val resolutions: List<AstImportResolution>,
    val violations: List<AstImportViolation> = emptyList()
) {
    fun render(): String = buildString {
        appendLine("ast-imports:")
        appendLine("  file: ${file.toString().replace('\\', '/')}")
        appendLine("  package: $packageName")
        appendLine("  package_path_invariant: $packagePathInvariantHolds")
        resolutions.forEach { resolution ->
            appendLine(
                "  import ${resolution.importPath} status=${resolution.status.name.lowercase()} " +
                    "matches=${resolution.matches.joinToString(",").ifBlank { "none" }}"
            )
        }
        violations.forEach { violation ->
            appendLine(
                "  violation ${violation.rule} imports=${violation.imports.joinToString(",")} " +
                    "evidence=${violation.evidence} remediation=${violation.remediation}"
            )
        }
    }.trimEnd()
}
