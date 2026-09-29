/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * B-SUP-008: Provider Arbitrage Engine
 *
 * Detects and exploits price differences between providers for the same capability,
 * enabling cost optimization through provider switching.
 */
package atropos.core.supremacy

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

object ProviderArbitrageEngine {
    data class ProviderPrice(
        val provider: String,
        val capability: String,
        val pricePerToken: Double,
        val latencyMs: Long,
        val timestamp: Instant = Instant.now()
    )

    data class ArbitrageOpportunity(
        val capability: String,
        val buyProvider: String,
        val sellProvider: String,
        val priceDiff: Double,
        val profitMargin: Double
    )

    private val prices = mutableListOf<ProviderPrice>()

    /**
     * Records a provider price quote.
     */
    fun recordPrice(price: ProviderPrice) {
        prices.add(price)
    }

    /**
     * Detects arbitrage opportunities for a capability.
     */
    fun detectOpportunities(capability: String, minMargin: Double = 0.05): List<ArbitrageOpportunity> {
        val capabilityPrices = prices.filter { it.capability == capability }
        val opportunities = mutableListOf<ArbitrageOpportunity>()

        for (i in capabilityPrices.indices) {
            for (j in i + 1 until capabilityPrices.size) {
                val p1 = capabilityPrices[i]
                val p2 = capabilityPrices[j]
                val diff = Math.abs(p1.pricePerToken - p2.pricePerToken)
                val avgPrice = (p1.pricePerToken + p2.pricePerToken) / 2
                val margin = if (avgPrice > 0) diff / avgPrice else 0.0

                if (margin >= minMargin) {
                    val (buy, sell) = if (p1.pricePerToken < p2.pricePerToken) p1 to p2 else p2 to p1
                    opportunities.add(ArbitrageOpportunity(
                        capability = capability,
                        buyProvider = buy.provider,
                        sellProvider = sell.provider,
                        priceDiff = diff,
                        profitMargin = margin
                    ))
                }
            }
        }

        return opportunities.sortedByDescending { it.profitMargin }
    }

    /**
     * Persists arbitrage opportunities to CAS.
     */
    fun persistOpportunities(casDir: Path, opportunities: List<ArbitrageOpportunity>): Path {
        Files.createDirectories(casDir)
        val file = casDir.resolve("arbitrage-opportunities.json")
        val json = opportunities.map { o ->
            """
                {
                    "capability": "${o.capability}",
                    "buyProvider": "${o.buyProvider}",
                    "sellProvider": "${o.sellProvider}",
                    "priceDiff": ${o.priceDiff},
                    "profitMargin": ${o.profitMargin}
                }
            """.trimIndent()
        }.joinToString(",\n", "[\n", "\n]")
        Files.writeString(file, json)
        return file
    }

    /**
     * CLI command to show arbitrage opportunities.
     */
    fun showOpportunities(opportunities: List<ArbitrageOpportunity>): String {
        return opportunities.joinToString("\n") {
            "${it.capability}: buy ${it.buyProvider} -> sell ${it.sellProvider} (${"%.2f".format(it.profitMargin * 100)}% margin)"
        }
    }
}