/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

data class ObjectPin(val objectId: String, val reason: String) {
    init { require(objectId.isNotBlank() && reason.isNotBlank()) }
}

class ObjectPinStore {
    private val pins = linkedMapOf<String, ObjectPin>()

    fun pin(pin: ObjectPin) { pins[pin.objectId] = pin }

    fun unpin(objectId: String): ObjectPin? = pins.remove(objectId)

    fun isPinned(objectId: String): Boolean = objectId in pins

    fun snapshot(): List<ObjectPin> = pins.values.toList()
}
