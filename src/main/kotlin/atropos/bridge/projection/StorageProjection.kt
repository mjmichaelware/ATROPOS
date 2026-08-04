/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.projection

import atropos.bridge.http.JsonWriter
import atropos.core.storage.StorageConstitution

/**
 * Projects the storage constitution onto the wire.
 *
 * `SUP.STOR.GLOBAL-BYTE-CEILING` requires storage to be a *visible* resource,
 * which means the surface has to be able to show the ceiling, the usage and
 * what could be freed — not just a percentage. A bar with no reclaim target
 * tells the operator they are in trouble without telling them what to do.
 */
class StorageProjection {
    fun render(constitution: StorageConstitution): String = JsonWriter.obj(
        "ok" to JsonWriter.bool(true),
        "usedBytes" to JsonWriter.num(constitution.usedBytes),
        "ceilingBytes" to JsonWriter.num(constitution.ceilingBytes),
        "remainingBytes" to JsonWriter.num(constitution.remainingBytes),
        "fractionUsed" to JsonWriter.num(constitution.fractionUsed),
        "reclaimableBytes" to JsonWriter.num(constitution.reclaimableBytes()),
        "classes" to JsonWriter.arr(
            constitution.classes.map { storageClass ->
                JsonWriter.obj(
                    "id" to JsonWriter.str(storageClass.id),
                    "tier" to JsonWriter.str(storageClass.tier.canonical),
                    "bytes" to JsonWriter.num(storageClass.bytes),
                    // Emitted so a surface never offers to reclaim the active
                    // run's evidence.
                    "reclaimable" to JsonWriter.bool(storageClass.tier.reclaimable)
                )
            }
        )
    )
}
