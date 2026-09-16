/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * B-SUP-011: Logical/Vector Clocks on Claims
 *
 * Implements vector clocks for tracking causal ordering of claims
 * across distributed agent operations.
 */
package atropos.core.supremacy

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

object VectorClocks {
    data class VectorClock(
        val clocks: Map<String, Long> = emptyMap()
    ) {
        fun increment(nodeId: String): VectorClock {
            val newClocks = clocks.toMutableMap()
            newClocks[nodeId] = (newClocks[nodeId] ?: 0) + 1
            return copy(clocks = newClocks)
        }

        fun merge(other: VectorClock): VectorClock {
            val allKeys = (clocks.keys + other.clocks.keys).distinct()
            val merged = allKeys.associateWith { key ->
                maxOf(clocks[key] ?: 0, other.clocks[key] ?: 0)
            }
            return copy(clocks = merged)
        }

        fun happensBefore(other: VectorClock): Boolean {
            val allKeys = (clocks.keys + other.clocks.keys).distinct()
            var atLeastOneLess = false
            allKeys.forEach { key ->
                val thisVal = clocks[key] ?: 0
                val otherVal = other.clocks[key] ?: 0
                if (thisVal > otherVal) return false
                if (thisVal < otherVal) atLeastOneLess = true
            }
            return atLeastOneLess
        }

        fun concurrentWith(other: VectorClock): Boolean {
            return !happensBefore(other) && !other.happensBefore(this)
        }
    }

    data class TimestampedClaim(
        val id: String,
        val claim: String,
        val vectorClock: VectorClock,
        val nodeId: String,
        val timestamp: Instant = Instant.now()
    )

    private val claims = ConcurrentHashMap<String, TimestampedClaim>()
    private val nodeClocks = ConcurrentHashMap<String, VectorClock>()

    /**
     * Records a new claim with vector clock.
     */
    fun recordClaim(claim: String, nodeId: String): TimestampedClaim {
        val clock = nodeClocks.getOrPut(nodeId) { VectorClock() }.increment(nodeId)
        nodeClocks[nodeId] = clock

        val claimObj = TimestampedClaim(
            id = java.util.UUID.randomUUID().toString(),
            claim = claim,
            vectorClock = clock,
            nodeId = nodeId
        )
        claims[claimObj.id] = claimObj
        return claimObj
    }

    /**
     * Gets the current vector clock for a node.
     */
    fun getClock(nodeId: String): VectorClock = nodeClocks.getOrPut(nodeId) { VectorClock() }

    /**
     * Checks causal ordering between two claims.
     */
    fun checkCausality(claimId1: String, claimId2: String): String? {
        val c1 = claims[claimId1] ?: return "Claim $claimId1 not found"
        val c2 = claims[claimId2] ?: return "Claim $claimId2 not found"

        return when {
            c1.vectorClock.happensBefore(c2.vectorClock) -> "$claimId1 happened before $claimId2"
            c2.vectorClock.happensBefore(c1.vectorClock) -> "$claimId2 happened before $claimId1"
            else -> "$claimId1 and $claimId2 are concurrent"
        }
    }

    /**
     * Persists vector clocks to CAS.
     */
    fun persistClocks(casDir: Path): Path {
        Files.createDirectories(casDir)
        val file = casDir.resolve("vector-clocks.json")
        // Simplified serialization
        val json = nodeClocks.map { (node, clock) ->
            """
                {
                    "node": "$node",
                    "clock": ${clock.clocks.joinToString(",") { key -> "\"$key\": ${clock.clocks[key]}" }}
                }
            """.trimIndent()
        }.joinToString(",\n", "{\n", "\n}")
        Files.writeString(file, json)
        return file
    }

    /**
     * CLI command to show clock state.
     */
    fun showClocks(): String {
        return nodeClocks.map { (node, clock) ->
            "$node: ${clock.clocks.joinToString(", ") { (key, value) -> "$key=$value" }}"
        }.joinToString("\n")
    }
}