/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

class RemoteQuotaGuard(private val quotaBytes: Long) {
    init { require(quotaBytes >= 0) }

    fun admit(currentBytes: Long, requestedBytes: Long): Boolean =
        currentBytes >= 0 && requestedBytes >= 0 && currentBytes <= quotaBytes - requestedBytes

    fun remaining(currentBytes: Long): Long = (quotaBytes - currentBytes).coerceAtLeast(0)
}
