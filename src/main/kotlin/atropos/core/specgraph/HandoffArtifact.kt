/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.specgraph

/**
 * Top-level rather than a companion constant: Kotlin initialises an enum's
 * entries before its companion object, so an entry cannot reference one.
 */
private const val JSON = "application/json"

/**
 * The SpecGraph export contract: the files `ExportService.export_plan` writes.
 *
 * Mirrors `specgraph_foundry/http_api/artifact_storage.py`'s
 * `validate_artifact_name` allowlist exactly. That list is the boundary — the
 * HTTP API refuses any name outside it — so a name this enum does not know is a
 * name the producing side would also have rejected, and treating it as unknown
 * here is the same answer arrived at independently rather than a guess.
 *
 * A closed enum rather than raw strings because the names are load-bearing in
 * three places at once: the manifest declares them, the signed-download
 * response returns them, and the verifier checks them. Three string literals
 * that must agree eventually stop agreeing.
 */
enum class HandoffArtifact(
    /** The file name, exactly as the export writes it. */
    val fileName: String,
    /** Media type, matching `media_type_for_name`. */
    val mediaType: String,
    /**
     * Whether a bundle without this file is unusable.
     *
     * [MANIFEST] and [ATROPOS_HANDOFF] are required because nothing can be
     * verified without the first or executed without the second. The rest are
     * required too, but for a weaker reason — the export always writes them, so
     * an absent one means a truncated download rather than a valid partial
     * bundle. [CHECKSUMS] is the exception: it is allowlisted by the API but not
     * produced by `_build_bundle`, so its absence is normal.
     */
    val required: Boolean = true
) {
    PROJECT("project.json", JSON),
    SOURCES("sources.json", JSON),
    ATOMS("atoms.json", JSON),
    RESEARCH("research.json", JSON),
    AUTHORITY_GRAPH("authority_graph.json", JSON),
    EXECUTION_GRAPH("execution_graph.json", JSON),
    TRACEABILITY("traceability.json", JSON),
    INTEGRATION_BINDINGS("integration_bindings.json", JSON),
    EXPORT_PROOF_SUMMARY("export_proof_summary.json", JSON),

    /**
     * The file addressed to ATROPOS by name.
     *
     * Schema `specgraph.atropos.handoff.v1`, carrying the execution DAG, the
     * requirement traceability, the integration bindings, the routing law and an
     * execution contract whose `runtime_owner` is `atropos`. It was produced on
     * every export and read by nothing on this side.
     */
    ATROPOS_HANDOFF("atropos_handoff.json", JSON),

    /** The human- and model-readable plan. Large, and the reason to stream it. */
    IMPLEMENTATION_BLUEPRINT_MD("implementation_blueprint.md", "text/markdown"),

    /** The same blueprint flattened, for contexts that cannot take markdown. */
    IMPLEMENTATION_BLUEPRINT_TXT("implementation_blueprint.txt", "text/plain"),

    /** The bundle index. Nothing else may be trusted before this one verifies. */
    MANIFEST("manifest.json", JSON),

    /** Allowlisted by the API but not written by `_build_bundle`. */
    CHECKSUMS("checksums.sha256", "text/plain", required = false);

    /** True when this artifact is one of the two large context documents. */
    val isBlueprint: Boolean
        get() = this == IMPLEMENTATION_BLUEPRINT_MD || this == IMPLEMENTATION_BLUEPRINT_TXT

    companion object {
        private val byFileName = entries.associateBy { it.fileName }

        /** The artifact with this file name, or null when outside the contract. */
        fun of(fileName: String): HandoffArtifact? = byFileName[fileName.trim()]

        /** The artifacts a complete bundle must contain. */
        fun requiredArtifacts(): List<HandoffArtifact> = entries.filter { it.required }

        /**
         * Names present in [names] that the export contract does not define.
         *
         * An undeclared file in a bundle directory is reported rather than
         * ignored. The directory is content the bundle's own manifest vouches
         * for; something it does not name arrived from somewhere else, and a
         * reader that silently skips it cannot tell a stray file from a planted
         * one.
         */
        fun unknownNames(names: Collection<String>): List<String> =
            names.filter { of(it) == null }.sorted()
    }
}
