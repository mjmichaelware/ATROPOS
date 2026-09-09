/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * B-SUP-012: Time-Travel Agent State Replay
 *
 * Implements state replay capability for debugging and verification,
 * allowing the agent to replay its execution from any checkpoint.
 */
package atropos.core.supremacy

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

object TimeTravelReplay {
    data class Checkpoint(
        val id: String,
        val stateSnapshot: String, // Serialized agent state
        val vectorClock: VectorClocks.VectorClock,
        val metadata: Map<String, String> = emptyMap(),
        val timestamp: Instant = Instant.now()
    )

    private val checkpoints = mutableListOf<Checkpoint>()

    /**
     * Creates a new checkpoint.
     */
    fun createCheckpoint(
        stateSnapshot: String,
        vectorClock: VectorClocks.VectorClock,
        metadata: Map<String, String> = emptyMap()
    ): Checkpoint {
        val checkpoint = Checkpoint(
            id = java.util.UUID.randomUUID().toString(),
            stateSnapshot = stateSnapshot,
            vectorClock = vectorClock,
            metadata = metadata
        )
        checkpoints.add(checkpoint)
        return checkpoint
    }

    /**
     * Replays from a specific checkpoint.
     */
    fun replayFrom(checkpointId: String): String? {
        val checkpoint = checkpoints.find { it.id == checkpointId }
        return checkpoint?.stateSnapshot
    }

    /**
     * Gets all checkpoints in time range.
     */
    fun getCheckpoints(since: Instant? = null, until: Instant? = null): List<Checkpoint> {
        return checkpoints.filter { cp ->
            (since == null || cp.timestamp.isAfter(since)) &&
            (until == null || cp.timestamp.isBefore(until))
        }
    }

    /**
     * Persists checkpoints to CAS.
     */
    fun persistCheckpoints(casDir: Path): Path {
        Files.createDirectories(casDir)
        val file = casDir.resolve("time-travel-checkpoints.json")
        val json = checkpoints.map { cp ->
            """
                {
                    "id": "${cp.id}",
                    "stateSnapshot": "${cp.stateSnapshot.replace("\"", "\\\"")}",
                    "timestamp": "${cp.timestamp}",
                    "metadata": ${cp.metadata.joinToString(",") { "\"$it.key\": \"$it.value\"" }}
                }
            """.trimIndent()
        }.joinToString(",\n", "[\n", "\n]")
        Files.writeString(file, json)
        return file
    }

    /**
     * CLI command to list checkpoints.
     */
    fun listCheckpoints(): String {
        return checkpoints.joinToString("\n") { "${it.id} @ ${it.timestamp} - ${it.metadata}" }
    }
}