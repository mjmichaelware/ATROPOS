/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.specgraph

import atropos.core.json.JsonStringField

/**
 * A parsed `manifest.json` — the bundle's own index of what it contains.
 *
 * Schema `specgraph.export.manifest.v1`. Each entry carries the artifact's
 * SHA-256 and byte length, and the manifest as a whole carries a
 * `bundle_fingerprint` over the sorted artifact metadata. That structure is what
 * makes a bundle checkable rather than merely present: the manifest states what
 * the files must hash to, so a truncated download and a tampered one are the
 * same detectable condition.
 *
 * Parsed rather than trusted. The manifest is the *claim*; [ExportBundleVerifier]
 * is what turns it into a fact.
 */
data class ExportManifest(
    val schema: String,
    val exportId: String,
    val planId: String,
    val projectId: String,
    val bundleFingerprint: String,
    val entries: List<ManifestEntry>
) {
    /** The artifacts this manifest declares, as contract members. */
    val declared: Set<HandoffArtifact>
        get() = entries.mapNotNull { HandoffArtifact.of(it.name) }.toSet()

    /** Names the manifest declares that are outside the export contract. */
    val undeclaredNames: List<String>
        get() = HandoffArtifact.unknownNames(entries.map { it.name })

    /** The entry for [artifact], or null when the manifest omits it. */
    fun entryFor(artifact: HandoffArtifact): ManifestEntry? =
        entries.firstOrNull { it.name == artifact.fileName }

    /** Total declared size, for deciding whether a bundle should be streamed. */
    val totalBytes: Long
        get() = entries.sumOf { it.byteLength }

    companion object {

        /** The only schema this reader understands. */
        const val SCHEMA = "specgraph.export.manifest.v1"

        /**
         * Parses manifest text, or null when it is not a manifest.
         *
         * Null rather than a thrown exception or an empty manifest: an
         * unparseable index and an index declaring nothing lead to opposite
         * conclusions, and an empty [ExportManifest] would let a caller conclude
         * a bundle verified when in fact nothing was checked.
         */
        fun parse(json: String): ExportManifest? {
            val schema = JsonStringField.text(json, "schema") ?: return null
            if (schema != SCHEMA) return null

            val artifactsBody = JsonStringField.arrayBody(json, "artifacts") ?: return null
            val entries = JsonStringField.objectsIn(artifactsBody).mapNotNull(ManifestEntry::parse)
            if (entries.isEmpty()) return null

            return ExportManifest(
                schema = schema,
                exportId = JsonStringField.text(json, "export_id").orEmpty(),
                planId = JsonStringField.text(json, "plan_id").orEmpty(),
                projectId = JsonStringField.text(json, "project_id").orEmpty(),
                bundleFingerprint = JsonStringField.text(json, "bundle_fingerprint").orEmpty(),
                entries = entries
            )
        }
    }
}

/**
 * One artifact's declared identity.
 *
 * [byteLength] is checked alongside [sha256] even though the hash subsumes it.
 * A length mismatch localises the fault immediately — a truncated transfer
 * rather than a substituted file — and costs one comparison.
 */
data class ManifestEntry(
    val name: String,
    val sha256: String,
    val byteLength: Long
) {
    /** The contract member this entry names, or null when outside it. */
    val artifact: HandoffArtifact?
        get() = HandoffArtifact.of(name)

    companion object {
        /**
         * Parses one entry object body, or null when it is not usable.
         *
         * A hash of the wrong length is rejected here rather than at comparison
         * time. An empty or truncated `sha256` would otherwise be compared
         * against a real digest, fail, and be reported as a corrupt *artifact*
         * when the corrupt thing is the manifest.
         */
        fun parse(objectBody: String): ManifestEntry? {
            val name = JsonStringField.text(objectBody, "name")?.trim().orEmpty()
            if (name.isEmpty()) return null
            val sha256 = JsonStringField.text(objectBody, "sha256")?.trim()?.lowercase().orEmpty()
            if (sha256.length != 64 || !sha256.all { it in '0'..'9' || it in 'a'..'f' }) return null
            val bytes = JsonStringField.longValue(objectBody, "bytes")
                ?: JsonStringField.longValue(objectBody, "byte_length")
                ?: return null
            if (bytes < 0) return null
            return ManifestEntry(name = name, sha256 = sha256, byteLength = bytes)
        }
    }
}
