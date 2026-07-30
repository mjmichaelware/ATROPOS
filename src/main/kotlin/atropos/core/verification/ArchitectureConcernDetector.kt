/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.verification

/** Finds concern categories present in one source file. */
class ArchitectureConcernDetector {
    fun detect(text: String): Set<ArchitectureConcern> =
        ArchitectureConcern.entries
            .filter { concern -> concern.markers.any { marker -> marker.containsMatchIn(text) } }
            .toSet()
}
