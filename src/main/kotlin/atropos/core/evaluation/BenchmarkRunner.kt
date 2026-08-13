/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.evaluation

import atropos.core.AtroposRepoRootLocator
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Collects benchmark results, and refuses to invent the ones that were not run.
 *
 * Source Doc 3 §4.3 names `BenchmarkRunner` as its own file and item 56 names
 * the four benchmarks. What this does *not* do is execute them: SWE-bench
 * Verified needs a dataset and hours of compute, and a class that pretended to
 * run it on a phone would be exactly the fake-success path AGENTS.md §0.6
 * forbids.
 *
 * What it does is own the contract. Results arrive from a harness — a CI job, a
 * recorded run, a file dropped in `.atropos/benchmarks/` — and this validates
 * their provenance, stores their evidence, and reports every benchmark in the
 * catalogue including the ones with no result. A benchmark absent from a report
 * reads as not applicable; a benchmark reported as `notRun` reads as work
 * outstanding, and only the second is true.
 *
 * Item 66 requires that "metric definitions, fixtures, environments, and scoring
 * must be repeatable and auditable". A result missing its benchmark version,
 * environment fingerprint, source fingerprint or evidence is therefore not a
 * weaker result — it is rejected, and says which field was missing.
 */
class BenchmarkRunner(
    repoRoot: Path = AtroposRepoRootLocator.resolve(),
    private val evidenceStore: EvidenceStore = EvidenceStore(repoRoot)
) {
    private val root: Path = repoRoot.resolve(".atropos/benchmarks").normalize()

    /**
     * Records a result after checking it can be reproduced.
     *
     * @return the stored result with its evidence hashes attached, or a
     *   rejection naming what was missing.
     */
    fun record(result: BenchmarkResult, rawOutput: String): BenchmarkRecording {
        if (result.notRun) {
            return BenchmarkRecording.rejected(result, "a result that was not run cannot be recorded")
        }
        val hash = evidenceStore.put(rawOutput, EvidenceKind.TEST_RESULT)
        val stored = result.copy(evidenceHashes = result.evidenceHashes + hash)
        if (!stored.reproducible) {
            return BenchmarkRecording.rejected(
                stored,
                "not reproducible: missing " + stored.missingProvenance().joinToString(", ")
            )
        }
        persist(stored)
        return BenchmarkRecording.accepted(stored)
    }

    /**
     * Every benchmark in the catalogue, with results where they exist.
     *
     * Ids with no recorded result come back as [BenchmarkResult.notRun] rather
     * than being omitted, so a report can never read as complete by virtue of
     * having skipped the hard ones.
     */
    fun report(): List<BenchmarkResult> {
        val recorded = load().associateBy { it.id }
        return BenchmarkId.entries.map { id ->
            recorded[id] ?: BenchmarkResult.notRun(id, "no recorded run")
        }
    }

    /** How much of the benchmark set has any result at all. */
    fun coverage(): BenchmarkCoverage {
        val results = report()
        val run = results.filterNot { it.notRun }
        return BenchmarkCoverage(
            total = results.size,
            run = run.size,
            competitive = run.count { it.competitive },
            reproducible = run.count { it.reproducible }
        )
    }

    private fun load(): List<BenchmarkResult> {
        if (!Files.isDirectory(root)) return emptyList()
        return Files.list(root).use { stream ->
            stream.filter { it.fileName.toString().endsWith(".tsv") }
                .map { runCatching { decode(Files.readString(it, StandardCharsets.UTF_8)) }.getOrNull() }
                .toList()
        }.filterNotNull()
    }

    private fun persist(result: BenchmarkResult) {
        runCatching {
            Files.createDirectories(root)
            Files.writeString(
                root.resolve(result.id.canonical + ".tsv"),
                encode(result),
                StandardCharsets.UTF_8
            )
        }
    }

    private fun encode(result: BenchmarkResult): String = listOf(
        result.id.canonical,
        result.score.toString(),
        result.resolved.toString(),
        result.total.toString(),
        result.benchmarkVersion,
        result.environmentFingerprint,
        result.sourceFingerprint,
        result.evidenceHashes.joinToString(","),
        result.detail
    ).joinToString("\t") { it.replace('\t', ' ').replace('\n', ' ') }

    private fun decode(line: String): BenchmarkResult? {
        // Trailing newline only. `trim()` would also strip the trailing tab of
        // an empty final field, leaving eight columns instead of nine and
        // silently discarding every result whose detail was blank -- which is
        // the default.
        val parts = line.trimEnd('\n', '\r').split('\t')
        if (parts.size < 9) return null
        val id = BenchmarkId.of(parts[0]) ?: return null
        return BenchmarkResult(
            id = id,
            score = parts[1].toDoubleOrNull() ?: return null,
            resolved = parts[2].toIntOrNull() ?: 0,
            total = parts[3].toIntOrNull() ?: 0,
            benchmarkVersion = parts[4],
            environmentFingerprint = parts[5],
            sourceFingerprint = parts[6],
            evidenceHashes = parts[7].split(',').filter { it.isNotBlank() },
            detail = parts[8]
        )
    }
}

/** Whether a result was accepted, and why not when it was not. */
data class BenchmarkRecording(
    val result: BenchmarkResult,
    val accepted: Boolean,
    val reason: String
) {
    companion object {
        fun accepted(result: BenchmarkResult) =
            BenchmarkRecording(result, true, "recorded with ${result.evidenceHashes.size} evidence object(s)")

        fun rejected(result: BenchmarkResult, reason: String) =
            BenchmarkRecording(result, false, reason)
    }
}

/**
 * How much of the benchmark set has been run.
 *
 * Reported alongside scores because a strong score on one benchmark out of five
 * is a different claim from a strong score on five, and a report showing only
 * the former invites the latter reading.
 */
data class BenchmarkCoverage(
    val total: Int,
    val run: Int,
    val competitive: Int,
    val reproducible: Int
) {
    fun render(): String =
        "$run of $total benchmarks run · $competitive competitive · $reproducible reproducible"
}
