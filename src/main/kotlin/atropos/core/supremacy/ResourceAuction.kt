/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * B-SUP-021: Resource Auction
 *
 * Implements auction mechanisms for allocating scarce resources
 * (context, quota, disk) among competing agents.
 */
package atropos.core.supremacy

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

object ResourceAuction {
    sealed class ResourceType {
        object CONTEXT : ResourceType()
        object QUOTA : ResourceType()
        object DISK : ResourceType()

        companion object {
            val values: List<ResourceType> = listOf(CONTEXT, QUOTA, DISK)
        }
    }

    data class Bid(
        val id: String = java.util.UUID.randomUUID().toString(),
        val bidder: String,
        val resource: ResourceType,
        val amount: Double,
        val maxPrice: Double,
        val timestamp: Instant = Instant.now()
    )

    data class AuctionResult(
        val resource: ResourceType,
        val winner: String?,
        val winningBid: Double?,
        val clearingPrice: Double,
        val allBids: List<Bid>
    )

    private val auctions = mutableMapOf<ResourceType, MutableList<Bid>>()

    /**
     * Places a bid for a resource.
     */
    fun bid(bidder: String, resource: ResourceType, amount: Double, maxPrice: Double): Bid {
        val bid = Bid(bidder = bidder, resource = resource, amount = amount, maxPrice = maxPrice)
        auctions.getOrPut(resource) { mutableListOf() }.add(bid)
        return bid
    }

    /**
     * Runs an auction for a resource (second-price sealed bid).
     */
    fun runAuction(resource: ResourceType): AuctionResult {
        val bids: List<Bid> = auctions[resource]?.toList() ?: emptyList()
        val validBids = bids.filter { it.maxPrice > 0 }.sortedByDescending { it.maxPrice }

        val winner = validBids.firstOrNull()
        val clearingPrice = validBids.getOrNull(1)?.maxPrice ?: 0.0

        // Clear the auction
        auctions[resource]?.clear()

        return AuctionResult(
            resource = resource,
            winner = winner?.bidder,
            winningBid = winner?.maxPrice,
            clearingPrice = clearingPrice,
            allBids = validBids
        )
    }

    /**
     * Gets all bids for a resource.
     */
    fun getBids(resource: ResourceType): List<Bid> = auctions[resource]?.toList() ?: emptyList()

    /**
     * Persists auction results to CAS.
     */
    fun persistResults(casDir: Path): Path {
        Files.createDirectories(casDir)
        val file = casDir.resolve("resource-auctions.json")
        val json = auctions.flatMap { (resource, bids) ->
            bids.map { bid ->
                """
                    {
                        "resource": "${resource}",
                        "bidder": "${bid.bidder}",
                        "amount": ${bid.amount},
                        "maxPrice": ${bid.maxPrice},
                        "timestamp": "${bid.timestamp}"
                    }
                """.trimIndent()
            }
        }.joinToString(",\n", "[\n", "\n]")
        Files.writeString(file, json)
        return file
    }

    /**
     * CLI command to show auction status.
     */
    fun showStatus(): String {
        return ResourceType.values.joinToString("\n") { resource ->
            val bids = getBids(resource)
            "$resource: ${bids.size} bids${if (bids.isNotEmpty()) " (top: ${bids.maxByOrNull { it.maxPrice }?.bidder})" else ""}"
        }
    }
}