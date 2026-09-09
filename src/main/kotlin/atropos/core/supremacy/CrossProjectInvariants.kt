/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * B-SUP-024: Cross-Project Invariant Mining
 *
 * Mines invariants that hold across multiple projects
 * to detect universal patterns and anti-patterns.
 */
package atropos.core.supremacy

import java.nio.file.Files
import java.nio.file.Path

object CrossProjectInvariants {
    data class Invariant(
        val id: String = java.util.UUID.randomUUID().toString(),
        val pattern: String,        // e.g., "all functions have tests"
        val projects: List<String>, // Projects where this holds
        val counterexamples: Int = 0,
        val confidence: Double = 1.0
    ) {
        val support: Int = projects.size
    }

    data class ProjectProfile(
        val projectId: String,
        val metrics: Map<String, Double> = emptyMap(),
        val patterns: Set<String> = emptySet()
    )

    private val invariants = mutableListOf<Invariant>()
    private val projectProfiles = mutableMapOf<String, ProjectProfile>()

    /**
     * Adds a project profile for mining.
     */
    fun addProject(profile: ProjectProfile) {
        projectProfiles[profile.projectId] = profile
    }

    /**
     * Mines invariants across all registered projects.
     */
    fun mineInvariants(minSupport: Int = 2, minConfidence: Double = 0.8): List<Invariant> {
        val allPatterns = projectProfiles.values.flatMap { it.patterns }.toSet()
        val newInvariants = mutableListOf<Invariant>()

        for (pattern in allPatterns) {
            val supportingProjects = projectProfiles.values
                .filter { it.patterns.contains(pattern) }
                .map { it.projectId }
                .toList()

            if (supportingProjects.size >= minSupport) {
                val confidence = supportingProjects.size.toDouble() / projectProfiles.size
                if (confidence >= minConfidence) {
                    newInvariants.add(Invariant(
                        pattern = pattern,
                        projects = supportingProjects,
                        confidence = confidence
                    ))
                }
            }
        }

        invariants.addAll(newInvariants)
        return newInvariants
    }

    /**
     * Gets all mined invariants.
     */
    fun getInvariants(): List<Invariant> = invariants.toList()

    /**
     * Persists invariants to CAS.
     */
    fun persistInvariants(casDir: Path): Path {
        Files.createDirectories(casDir)
        val file = casDir.resolve("cross-project-invariants.json")
        val json = invariants.map { i ->
            """
                {
                    "id": "${i.id}",
                    "pattern": "${i.pattern}",
                    "projects": [${i.projects.joinToString(", ") { "\"$it\"" }}],
                    "counterexamples": ${i.counterexamples},
                    "confidence": ${i.confidence},
                    "support": ${i.support}
                }
            """.trimIndent()
        }.joinToString(",\n", "[\n", "\n]")
        Files.writeString(file, json)
        return file
    }

    /**
     * CLI command to show invariants.
     */
    fun showInvariants(): String {
        return invariants.joinToString("\n") { "${it.pattern} (${it.support}/${"%.0f".format(it.confidence * 100)}%)" }
    }
}