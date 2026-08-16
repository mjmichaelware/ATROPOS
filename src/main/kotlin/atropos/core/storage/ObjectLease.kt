/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

import java.time.Instant

data class ObjectLease(
    val objectId: String,
    val holderId: String,
    val expiresAt: Instant
) {
    init {
        require(objectId.isNotBlank() && holderId.isNotBlank())
    }

    fun isActive(now: Instant): Boolean = now.isBefore(expiresAt)
}

/** Single-owner lease view used to protect objects during active work. */
class ObjectLeaseStore {
    private val leases = linkedMapOf<String, ObjectLease>()

    fun acquire(lease: ObjectLease, now: Instant): Boolean {
        val current = leases[lease.objectId]
        if (current != null && current.isActive(now) && current.holderId != lease.holderId) return false
        leases[lease.objectId] = lease
        return true
    }

    fun release(objectId: String, holderId: String): Boolean =
        if (leases[objectId]?.holderId == holderId) leases.remove(objectId) != null else false

    fun active(objectId: String, now: Instant): ObjectLease? =
        leases[objectId]?.takeIf { it.isActive(now) }

    fun snapshot(): List<ObjectLease> = leases.values.toList()
}
