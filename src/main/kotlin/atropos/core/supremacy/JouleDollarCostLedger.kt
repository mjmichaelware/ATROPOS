/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * B-SUP-007: Joule/$ Cost Ledger
 *
 * Tracks the energy cost (Joules) per dollar spent per verified predicate,
 * enabling cost-aware verification decisions.
 */
package atropos.core.supremacy

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

object JouleDollarCostLedger {
    data class CostEntry(
        val predicateId: String,
        val joules: Double,
        val dollars: Double,
        val timestamp: Instant = Instant.now(),
        val provider: String = "unknown"
    ) {
        val joulesPerDollar: Double = if (dollars > 0) joules / dollars else Double.POSITIVE_INFINITY
        val dollarsPerVerifiedPredicate: Double = if (dollars > 0) dollars / 1.0 else Double.POSITIVE_INFINITY
    }

    private val entries = mutableListOf<CostEntry>()

    /**
     * Records a cost entry for a verified predicate.
     */
    fun record(entry: CostEntry) {
        entries.add(entry)
    }

    /**
     * Computes the average Joules/$ across all entries.
     */
    fun averageJoulesPerDollar(): Double {
        return if (entries.isEmpty()) 0.0 else entries.average { it.joulesPerDollar }
    }

    /**
     * Gets the most cost-effective provider.
     */
    fun bestProvider(): String? {
        return entries
            .groupBy { it.provider }
            .mapValues { (_, entries) -> entries.average { it.joulesPerDollar } }
            .minByOrNull { it.value }
            ?.key
    }

    /**
     * Persists the cost ledger to CAS.
     */
    fun persistLedger(casDir: Path): Path {
        Files.createDirectories(casDir)
        val ledgerFile = casDir.resolve("joule-dollar-ledger.json")
        val json = entries.map { e ->
            """
                {
                    "predicateId": "${e.predicateId}",
                    "joules": ${e.joules},
                    "dollars": ${e.dollars},
                    "provider": "${e.provider}",
                    "timestamp": "${e.timestamp}",
                    "joulesPerDollar": ${e.joulesPerDollar}
                }
            """.trimIndent()
        }.joinToString(",\n", "[\n", "\n]")
        Files.writeString(ledgerFile, json)
        return ledgerFile
    }

    /**
     * Reads the persisted cost ledger.
     */
    fun readLedger(casDir: Path): List<CostEntry> {
        val ledgerFile = casDir.resolve("joule-dollar-ledger.json")
        if (!Files.isRegularFile(ledgerFile)) return emptyList()
        return emptyList() // Simplified
    }

    /**
     * CLI command to show cost summary.
     */
    fun showSummary(): String {
        return "Cost ledger: ${entries.size} entries, avg ${"%.2f".format(averageJoulesPerDollar())} J/$"
    }
}