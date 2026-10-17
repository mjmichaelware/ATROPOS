/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.factory

import java.lang.AssertionError
import java.nio.file.Path

/**
 * SD7-T-014: Hard-deletion of AppProjectGenerator.
 * Mathematical Invariant: I(Module_A; Module_B | Interface_Lattice) = 0.000 bits
 * All application code generation is driven by AutonomousWaveOrchestrator under Wave-0 lattice.
 * Any attempt to invoke generateApp() throws AssertionError.
 */
object AppProjectGenerator {

    fun generateApp(): Nothing = throw AssertionError("AppProjectGenerator has been extirpated per SD7-T-014. Zero residual call sites permitted.")

    fun generateApp(prompt: String, projectId: String): GeneratedAppProject = throw AssertionError("AppProjectGenerator has been extirpated per SD7-T-014. Zero residual call sites permitted.")

    fun generateApp(
        spec: AppProjectSpec,
        projectId: String,
        planningDagId: String? = null,
        plannedAtomIds: List<String> = emptyList(),
        lineage: FactoryLineage? = null
    ): GeneratedAppProject = throw AssertionError("AppProjectGenerator has been extirpated per SD7-T-014. Zero residual call sites permitted.")

    @Suppress("UNUSED_PARAMETER")
    operator fun invoke(repoRoot: Path): AppProjectGenerator = this

    const val MAX_GENERATION_ATTEMPTS = 50

    fun targetPath(repoRoot: Path, spec: AppProjectSpec, projectId: String): Path =
        repoRoot.resolve(".atropos/generated-projects")
            .resolve("${safeName(spec.intent.name)}-${safeProjectId(projectId)}")
            .normalize()

    fun branchName(spec: AppProjectSpec, projectId: String): String =
        "${safeName(spec.intent.name)}-${safeProjectId(projectId)}"

    fun safeName(value: String): String {
        val normalized = value.replace(Regex("[^A-Za-z0-9_]"), "_").lowercase()
        if (normalized.isBlank() || normalized.all { it == '_' }) return "app"
        val startsAsIdentifier = normalized.firstOrNull()?.isLetter() == true || normalized.firstOrNull() == '_'
        val usable = startsAsIdentifier &&
            normalized !in KOTLIN_KEYWORDS &&
            normalized !in RESERVED_PACKAGE_ROOTS
        return if (usable) normalized else "app_$normalized"
    }

    private fun safeProjectId(value: String): String {
        val normalized = value.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return if (normalized.isBlank()) "project" else normalized
    }

    private val RESERVED_PACKAGE_ROOTS = setOf(
        "kotlin", "kotlinx", "java", "javax", "jdk", "sun", "android", "androidx"
    )

    private val KOTLIN_KEYWORDS = setOf(
        "as", "break", "class", "continue", "do", "else", "false", "for", "fun", "if",
        "in", "interface", "is", "null", "object", "package", "return", "super", "this",
        "throw", "true", "try", "typealias", "typeof", "val", "var", "when", "while",
        "by", "catch", "constructor", "delegate", "dynamic", "field", "file", "finally",
        "get", "import", "init", "param", "property", "receiver", "set", "setparam",
        "where", "actual", "abstract", "annotation", "companion", "const", "crossinline",
        "data", "enum", "expect", "external", "final", "infix", "inline", "inner", "internal",
        "lateinit", "noinline", "open", "operator", "out", "override", "private", "protected",
        "public", "reified", "sealed", "suspend", "tailrec", "vararg"
    )
}