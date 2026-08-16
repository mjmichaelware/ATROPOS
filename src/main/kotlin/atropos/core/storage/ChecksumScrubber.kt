/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

import java.security.MessageDigest

class ChecksumScrubber {
    fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    fun matches(bytes: ByteArray, expected: String): Boolean =
        sha256(bytes).equals(expected, ignoreCase = true)
}
