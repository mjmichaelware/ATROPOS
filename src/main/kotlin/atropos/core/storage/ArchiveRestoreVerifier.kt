/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

class ArchiveRestoreVerifier(private val checksums: ChecksumScrubber = ChecksumScrubber()) {
    fun verify(bytes: ByteArray, expectedSha256: String): Boolean = checksums.matches(bytes, expectedSha256)
}
