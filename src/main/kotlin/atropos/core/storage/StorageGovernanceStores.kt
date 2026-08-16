/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

/** The storage supervisor's typed access point for governance state. */
data class StorageGovernanceStores(
    val accounting: StorageAccountingLedger,
    val references: ObjectReferenceGraph,
    val leases: ObjectLeaseStore,
    val pins: ObjectPinStore,
    val legalHolds: LegalHoldStore,
    val tombstones: TombstoneStore
)
