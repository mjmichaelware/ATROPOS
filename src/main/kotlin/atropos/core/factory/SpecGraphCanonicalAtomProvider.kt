/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.factory

import atropos.core.AtroposRepoRootLocator
import atropos.core.AtroposConfig
import atropos.core.ProviderCascadeRouter
import atropos.core.ProviderFactory
import atropos.core.planning.CanonicalAtomProvider
import atropos.core.planning.CanonicalAtomSet
import atropos.core.planning.AtomDimension
import atropos.core.specgraph.ExportBundleReader
import java.nio.file.Files
import java.nio.file.Path

/**
 * Supplies the planner with atoms from the canonical SpecGraph atomizer.
 *
 * The factory side of [CanonicalAtomProvider]. Planning declares the need;
 * this satisfies it, so planning never imports the factory.
 *
 * Returning null is the ordinary path on a machine with no SpecGraph
 * installed, and it must stay cheap: [SpecGraphAtomizer] checks
 * `SPECGRAPH_ROOT` before touching the filesystem, so an unconfigured
 * repository pays one environment read per source rather than a subprocess.
 */
class SpecGraphCanonicalAtomProvider(
    private val repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val atomizer: SpecGraphAtomizer = SpecGraphAtomizer(),
    /**
     * Where the provenance line is recorded.
     *
     * Defaults to the thinking stream rather than to nothing. A no-op default
     * meant that during planning — the one place these atoms actually decide
     * the DAG — every per-source atomization was silent: whether SpecGraph
     * planned a document or the internal extractor did could not be established
     * from any artifact the run produced. That is the exact question this line
     * exists to answer, and the run reported a node count either way.
     */
    private val evidenceSink: (String) -> Unit = { line ->
        atropos.core.thinking.Thinking.stream.emit(atropos.core.thinking.ThinkingDepth.L2, line)
    },
    /** Test seam; production uses the canonical provider cascade below. */
    private val dimensionCompletion: ((String, String) -> String)? = null
) : CanonicalAtomProvider {

    private data class DimensionClassification(
        val response: String,
        val provider: String,
        val attempts: Int,
        val failures: String
    )

    override fun atomsFor(
        projectId: String,
        sourcePath: String,
        content: String,
        promptFingerprint: String,
        promptSpans: String
    ): CanonicalAtomSet? {
        inspectVerifiedExport(repoRoot)?.let(evidenceSink)
        val atomization = atomizer.atomizeToRecords(
            repoRoot = repoRoot,
            projectId = projectId,
            source = content,
            promptFingerprint = promptFingerprint,
            promptSpans = promptSpans
        )

        // Recorded whether or not it succeeded. "Which atomizer planned this"
        // is the first question asked of any artifact after the fact, and a
        // fallback that left no trace would make every plan look canonical.
        evidenceSink("specgraph source=$sourcePath ${atomization.evidenceLine}")

        if (!atomization.usable) {
            evidenceSink(
                "specgraph source=$sourcePath degraded_mode=internal_dag_fallback " +
                    "reason=${atomization.evidenceLine}"
            )
            return null
        }

        val mapped = atomization.atoms.map { record ->
            record.toInternalAtom(
                projectId = projectId,
                documentId = atomization.documentId,
                promptFingerprint = promptFingerprint,
                promptSpans = promptSpans,
                sourceDocumentSha256 = atomization.sourceSha256
            )
        }

        // The same stage model the internal extractor uses. Applied here too so
        // a plan orders the same way whichever atomizer produced it -- otherwise
        // the canonical path, which is the one meant to be authoritative, is the
        // only one that emits an edgeless graph.
        val staged = atropos.core.planning.InternalAtomDependencyModel.withStageDependencies(mapped)

        // Canonical SpecGraph dimensions are transport data, not a substitute
        // for the provider-backed dimension contract. Every atom is classified
        // so partial or collapsed upstream labels cannot silently become a
        // false-green execution plan.
        val finalStaged = fillDimensions(staged, promptFingerprint, sourcePath)
        evidenceSink(
            "specgraph source=$sourcePath provider filled atom dimensions " +
                "count=${finalStaged.size} categories=${finalStaged.map { it.dimension }.distinct().size}"
        )

        return CanonicalAtomSet(
            atoms = finalStaged,
            provenance = "canonical_specgraph document=${atomization.documentId} " +
                "atoms=${finalStaged.size} source_sha256=${atomization.sourceSha256} " +
                atropos.core.planning.InternalAtomDependencyModel.render(finalStaged)
        )
    }

    internal fun fillDimensions(
        atoms: List<atropos.core.planning.InternalAtom>,
        promptFingerprint: String,
        sourcePath: String
    ): List<atropos.core.planning.InternalAtom> = atoms.map { atom ->
        val prompt = """
            Classify this software requirement into exactly one ATROPOS AtomDimension.
            Return only the exact enum constant, with no markdown or explanation.
            Categories: ${AtomDimension.entries.joinToString(", ") { it.name }}
            Prompt fingerprint: $promptFingerprint
            Source: $sourcePath
            Requirement: ${atom.statement}
        """.trimIndent()
        val classification = runCatching {
            dimensionCompletion?.invoke(prompt, "System: classify one requirement")?.let {
                DimensionClassification(
                    response = it,
                    provider = "injected",
                    attempts = 1,
                    failures = "none"
                )
            } ?: completeThroughProviderCascade(prompt)
        }.getOrElse { failure ->
            if (failure.message?.startsWith("NO_ELIGIBLE_DIMENSION_PROVIDER") == true) {
                throw failure
            }
            throw IllegalStateException(
                "SPECGRAPH_DIMENSION_FILL_REQUIRED: provider unavailable for atom=${atom.id}; " +
                    "configure an eligible provider and retry (${failure.message ?: failure.javaClass.simpleName})",
                failure
            )
        }
        evidenceSink(
            "specgraph source=$sourcePath atom=${atom.id} dimension_provider=${classification.provider} " +
                "attempts=${classification.attempts} failures=${classification.failures}"
        )
        val classified = parseDimension(classification.response)
            ?: throw IllegalStateException(
                "SPECGRAPH_DIMENSION_FILL_REQUIRED: provider returned no valid AtomDimension " +
                    "for atom=${atom.id}; response must be one exact enum constant"
            )
        atom.copy(dimension = classified)
    }

    override fun fillDimensionsForFallback(
        atoms: List<atropos.core.planning.InternalAtom>,
        promptFingerprint: String,
        sourcePath: String
    ): List<atropos.core.planning.InternalAtom>? {
        return try {
            fillDimensions(atoms, promptFingerprint, sourcePath)
        } catch (failure: IllegalStateException) {
            if (failure.message?.startsWith("NO_ELIGIBLE_DIMENSION_PROVIDER") == true) {
                evidenceSink(
                    "specgraph source=$sourcePath fallback dimensions=deterministic_keyword_classification " +
                        "reason=${failure.message}"
                )
                null
            } else {
                throw failure
            }
        }
    }

    private fun completeThroughProviderCascade(prompt: String): DimensionClassification {
        val config = AtroposConfig.load()
        val result = ProviderCascadeRouter(
            ProviderFactory(config),
            healthyProviderIds = { ProviderOnboardingService().healthyProviderIds() },
            localOnly = { config.runtime.localOnly }
        ).completeWithCascade(
            requestedProvider = config.runtime.defaultProvider,
            prompt = prompt,
            context = "System: classify one requirement into the supplied closed vocabulary.",
            acceptResponse = { response -> parseDimension(response) != null }
        )
        if (result.queued || result.response.isBlank()) {
            val attempted = result.errors.joinToString(",") { error ->
                "${error.provider}:${error.type.name.lowercase()}"
            }.ifBlank { "none" }
            val prefix = if (
                result.errors.isEmpty() ||
                result.errors.all { it.type == atropos.core.FailureType.MISSING_KEY }
            ) {
                "NO_ELIGIBLE_DIMENSION_PROVIDER"
            } else {
                "SPECGRAPH_DIMENSION_FILL_REQUIRED"
            }
            throw IllegalStateException(
                "$prefix: no eligible provider completed the AtomDimension classification; " +
                    "attempted=$attempted; configure or repair an eligible provider and retry" +
                    result.queueReason?.let { "; queue=$it" }.orEmpty()
            )
        }
        return DimensionClassification(
            response = result.response,
            provider = result.providerName,
            attempts = result.errors.size + 1,
            failures = result.errors.joinToString(",") { error ->
                "${error.provider}:${error.type.name.lowercase()}"
            }.ifBlank { "none" }
        )
    }

    internal fun parseDimension(response: String): AtomDimension? {
        val normalized = response.trim().uppercase()
        return AtomDimension.entries.firstOrNull { it.name == normalized }
    }

    /**
     * Consume an already-exported, verified SpecGraph handoff when one is
     * available. Export ingestion is optional: a missing or invalid export is
     * an explicit soft-fail and the canonical atomizer below remains the
     * normal fallback. The reader and [atropos.core.specgraph.HandoffDagTranslator]
     * stay the sole owners of bundle verification and graph translation.
     */
    private fun inspectVerifiedExport(repoRoot: Path): String? {
        val configured = System.getenv("ATROPOS_SPECGRAPH_EXPORT")
            ?.trim()
            ?.takeIf(String::isNotBlank)
            ?.let { Path.of(it) }
        val candidate = configured ?: repoRoot.resolve(".atropos/specgraph/export")
        val normalized = candidate.toAbsolutePath().normalize()
        if (!Files.isDirectory(normalized)) {
            // Silent only when nobody asked for an export. A configured path
            // that is not there is a real soft failure and gets a line; the
            // default path simply not existing is the ordinary case, and
            // reporting it on every atomisation would bury the one trace the
            // fallback leaves behind in noise.
            return if (configured != null) {
                "specgraph_export SKIPPED_SOFT_FAIL:missing path=${normalized.fileName}"
            } else {
                null
            }
        }
        val (reader, verification) = ExportBundleReader.openOrExplain(normalized)
        if (reader == null) {
            return "specgraph_export SKIPPED_SOFT_FAIL:${verification.failures.firstOrNull() ?: "unusable"}"
        }
        val translation = reader.executionDag(repoRoot)
        return "specgraph_export ${translation?.evidenceLine() ?: "SKIPPED_SOFT_FAIL:handoff_missing"}"
    }
}
