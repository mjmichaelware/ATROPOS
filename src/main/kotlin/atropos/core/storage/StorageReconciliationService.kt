/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

data class StorageReconciliation(val missingFromLedger: List<String>, val missingFromDisk: List<String>, val balanced: Boolean)

class StorageReconciliationService {
    fun reconcile(ledgerIds: Set<String>, diskIds: Set<String>): StorageReconciliation {
        val missingFromLedger = diskIds - ledgerIds
        val missingFromDisk = ledgerIds - diskIds
        return StorageReconciliation(missingFromLedger.toList().sorted(), missingFromDisk.toList().sorted(), missingFromLedger.isEmpty() && missingFromDisk.isEmpty())
    }
}
