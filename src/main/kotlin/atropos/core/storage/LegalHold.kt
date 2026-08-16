/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

data class LegalHold(val objectId: String, val authority: String, val reason: String) {
    init { require(objectId.isNotBlank() && authority.isNotBlank() && reason.isNotBlank()) }
}

class LegalHoldStore {
    private val holds = linkedMapOf<String, LegalHold>()

    fun place(hold: LegalHold) { holds[hold.objectId] = hold }

    fun release(objectId: String): LegalHold? = holds.remove(objectId)

    fun isHeld(objectId: String): Boolean = objectId in holds

    fun snapshot(): List<LegalHold> = holds.values.toList()
}
