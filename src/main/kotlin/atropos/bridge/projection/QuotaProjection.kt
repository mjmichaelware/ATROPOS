/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.projection

import atropos.bridge.http.JsonWriter
import atropos.core.provider.ProviderDescriptorRegistry
import atropos.core.provider.QuotaLedger

/**
 * Read-only wire projection of the existing quota ledger.
 *
 * This deliberately emits accounting metadata only. Provider credentials and
 * endpoint configuration belong to the activation/vault owners and never cross
 * this projection boundary.
 */
class QuotaProjection(
    private val registry: ProviderDescriptorRegistry,
    private val ledger: QuotaLedger
) {
    fun render(): String {
        val records = ledger.all().associateBy { it.providerId }
        val providers = registry.getAll().mapNotNull { descriptor ->
            records[descriptor.id]?.let { record ->
                JsonWriter.obj(
                    "id" to JsonWriter.str(descriptor.id),
                    "billingClass" to JsonWriter.str(descriptor.billingClass().name),
                    "state" to JsonWriter.str(record.state.name),
                    "configured" to JsonWriter.bool(record.configured),
                    "verified" to JsonWriter.bool(record.verified),
                    "usedRequests" to JsonWriter.num(record.usedRequests),
                    "usedTokens" to JsonWriter.num(record.usedTokens),
                    "resetAtEpochMs" to (record.resetAtEpochMs?.let(JsonWriter::num) ?: "null"),
                    "cooldownUntilEpochMs" to (record.cooldownUntilEpochMs?.let(JsonWriter::num) ?: "null"),
                    "paidLocked" to JsonWriter.bool(record.paidLocked)
                )
            }
        }
        return JsonWriter.obj(
            "readable" to JsonWriter.bool(true),
            "providers" to JsonWriter.arr(providers)
        )
    }
}
