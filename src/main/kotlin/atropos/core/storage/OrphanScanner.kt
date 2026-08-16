/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

class OrphanScanner(private val references: ObjectReferenceGraph) {
    fun scan(objectIds: Iterable<String>): List<String> = objectIds.filterNot(references::isReferenced).distinct()
}
