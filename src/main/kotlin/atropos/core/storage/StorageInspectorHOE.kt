/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

/** Operator-facing projection; it does not make storage decisions. */
class StorageInspectorHOE {
    fun render(constitution: StorageConstitution, watermark: WatermarkDecision): String = buildString {
        appendLine(constitution.render())
        appendLine("watermark allowed=${watermark.allowed} emergency=${watermark.emergency}")
        appendLine("reclaimable=${constitution.reclaimableBytes()}")
    }.trimEnd()
}
