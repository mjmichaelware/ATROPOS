/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

/** Tracks durable object references so reclaim never guesses reachability. */
class ObjectReferenceGraph {
    private val references = linkedMapOf<String, LinkedHashSet<String>>()

    fun reference(ownerId: String, objectId: String) {
        require(ownerId.isNotBlank() && objectId.isNotBlank())
        references.getOrPut(ownerId) { linkedSetOf() }.add(objectId)
    }

    fun unreference(ownerId: String, objectId: String): Boolean =
        references[ownerId]?.remove(objectId) == true

    fun referencedBy(objectId: String): List<String> =
        references.filterValues { objectId in it }.keys.toList()

    fun isReferenced(objectId: String): Boolean = referencedBy(objectId).isNotEmpty()

    fun snapshot(): Map<String, Set<String>> = references.mapValues { it.value.toSet() }
}
