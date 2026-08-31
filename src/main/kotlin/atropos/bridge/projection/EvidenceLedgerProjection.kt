/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge.projection

import atropos.bridge.http.JsonWriter
import atropos.core.evaluation.EvidenceStore
import atropos.core.evaluation.EvidenceKind
import atropos.core.evaluation.EvidenceVerification
import atropos.core.phase20.EvidenceLedger
import atropos.core.phase20.StructuralManifest
import atropos.core.security.RedactionFilter

/**
 * Projects the evidence ledger onto the wire.
 *
 * `ADD-W-020` requires browsing CAS/ledger claims without engine fork.
 * Read-only ledger UI at `/developer/ledger`.
 */
class EvidenceLedgerProjection(
    private val redactionFilter: RedactionFilter = RedactionFilter()
) {

    /**
     * Renders the evidence ledger overview.
     */
    fun render(store: EvidenceStore, ledger: EvidenceLedger): String {
        val count = store.count()
        val kinds = EvidenceKind.entries.map { kind ->
            JsonWriter.obj(
                "kind" to JsonWriter.str(kind.name),
                "count" to JsonWriter.num(store.getByMetric(kind.name).size)
            )
        }

        return JsonWriter.obj(
            "ok" to JsonWriter.bool(true),
            "totalObjects" to JsonWriter.num(count),
            "byKind" to JsonWriter.arr(kinds),
            "manifests" to JsonWriter.arr(ledgerManifests(store))
        )
    }

    /**
     * Renders the evidence verification result.
     */
    fun renderVerification(verification: EvidenceVerification): String = JsonWriter.obj(
        "ok" to JsonWriter.bool(verification.intact),
        "cited" to JsonWriter.num(verification.cited),
        "missing" to JsonWriter.arr(verification.missing.map { JsonWriter.str(it) }),
        "corrupt" to JsonWriter.arr(verification.corrupt.map { JsonWriter.str(it) }),
        "intact" to JsonWriter.bool(verification.intact)
    )

    /**
     * Renders a structural manifest.
     */
    fun renderManifest(manifest: StructuralManifest): String = JsonWriter.obj(
        "documentHash" to JsonWriter.str(manifest.documentHash),
        "regions" to JsonWriter.arr(manifest.regions.map { region ->
            JsonWriter.obj(
                "type" to JsonWriter.str(region.type.name),
                "startByteOffset" to JsonWriter.num(region.startByteOffset),
                "endByteOffset" to JsonWriter.num(region.endByteOffset),
                "parentHash" to JsonWriter.nullable(region.parentHash?.let { JsonWriter.str(it) })
            )
        })
    )

    private fun ledgerManifests(store: EvidenceStore): List<String> {
        // Find all structural manifests in the store
        val manifests = mutableListOf<String>()
        // This is a simplified implementation - in reality we'd scan the store
        return manifests
    }
}