/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * B-SUP-009: Quota Futures/Reservation
 *
 * Implements quota reservation system for future provider usage,
 * enabling guaranteed capacity for critical operations.
 */
package atropos.core.supremacy

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

object QuotaFutures {
    data class QuotaReservation(
        val id: String,
        val capability: String,
        val provider: String,
        val tokensReserved: Long,
        val expiresAt: Instant,
        val costDollars: Double,
        val status: Status = Status.ACTIVE
    ) {
        enum class Status { ACTIVE, EXPIRED, CONSUMED, CANCELLED }

        val isValid: Boolean
            get() = status == Status.ACTIVE && Instant.now().isBefore(expiresAt)
    }

    private val reservations = mutableListOf<QuotaReservation>()

    /**
     * Reserves quota for future use.
     */
    fun reserve(
        capability: String,
        provider: String,
        tokens: Long,
        durationHours: Long,
        costDollars: Double
    ): QuotaReservation {
        val reservation = QuotaReservation(
            id = java.util.UUID.randomUUID().toString(),
            capability = capability,
            provider = provider,
            tokensReserved = tokens,
            expiresAt = Instant.now().plusSeconds(durationHours * 3600),
            costDollars = costDollars
        )
        reservations.add(reservation)
        return reservation
    }

    /**
     * Consumes a reservation.
     */
    fun consume(reservationId: String): Boolean {
        val reservation = reservations.find { it.id == reservationId }
        if (reservation == null || !reservation.isValid) return false
        // In real impl, update status to CONSUMED
        return true
    }

    /**
     * Gets available reservations for a capability.
     */
    fun availableFor(capability: String): List<QuotaReservation> {
        return reservations.filter { it.capability == capability && it.isValid }
    }

    /**
     * Persists reservations to CAS.
     */
    fun persistReservations(casDir: Path): Path {
        Files.createDirectories(casDir)
        val file = casDir.resolve("quota-reservations.json")
        val json = reservations.map { r ->
            """
                {
                    "id": "${r.id}",
                    "capability": "${r.capability}",
                    "provider": "${r.provider}",
                    "tokensReserved": ${r.tokensReserved},
                    "expiresAt": "${r.expiresAt}",
                    "costDollars": ${r.costDollars},
                    "status": "${r.status}"
                }
            """.trimIndent()
        }.joinToString(",\n", "[\n", "\n]")
        Files.writeString(file, json)
        return file
    }

    /**
     * CLI command to show active reservations.
     */
    fun showReservations(): String {
        return reservations.filter { it.isValid }.joinToString("\n") {
            "  ${it.capability} @ ${it.provider}: ${it.tokensReserved} tokens (expires ${it.expiresAt})"
        }
    }
}