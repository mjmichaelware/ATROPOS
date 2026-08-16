/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.specgraph

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.time.Instant

/**
 * Reads artifacts out of a bundle that has already verified.
 *
 * Construction requires a [BundleVerification], not a path. That is the whole
 * design: there is no way to obtain a reader for an unverified bundle, so a
 * caller cannot skip the check by reaching for the file directly and no reader
 * has to re-state the precondition in its own documentation.
 *
 * Reads are per-artifact rather than whole-bundle. `implementation_blueprint.md`
 * and the JSON graphs run to hundreds of kilobytes each; a caller wanting the
 * plan should not pay for the blueprint, and a caller wanting context for one
 * prompt should not load the plan.
 */
class ExportBundleReader private constructor(
    private val root: Path,
    val verification: BundleVerification
) {

    /** The verified manifest. Non-null: an unverified bundle yields no reader. */
    val manifest: ExportManifest
        get() = requireNotNull(verification.manifest) { "a verified bundle always has a manifest" }

    /**
     * The artifact's bytes as text, or null when it is not in this bundle.
     *
     * Null rather than an empty string. An artifact SpecGraph did not write and
     * an artifact it wrote empty lead to different conclusions, and a caller
     * treating the second as the first would report a stage missing that in
     * fact arrived with nothing in it.
     */
    fun text(artifact: HandoffArtifact): String? {
        if (artifact !in verification.verified) return null
        val path = root.resolve(artifact.fileName)
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return null
        return runCatching { Files.readString(path) }.getOrNull()
    }

    /** The parsed handoff, or null when it is absent or of another schema. */
    fun handoff(): HandoffDocument? = text(HandoffArtifact.ATROPOS_HANDOFF)?.let(HandoffParser::parse)

    /** Translates the verified handoff through the canonical ATROPOS DAG owner. */
    fun executionDag(repoRoot: Path, now: Instant = Instant.now()): Translation? =
        handoff()?.let { HandoffDagTranslator().translate(it, repoRoot, now) }

    /**
     * The implementation blueprint, preferring markdown.
     *
     * Both forms carry the same content; the markdown one keeps the structure a
     * model can navigate. The plain-text fallback exists for contexts that
     * cannot take markdown, so falling back to it is a degradation rather than
     * a failure.
     */
    fun blueprint(): String? =
        text(HandoffArtifact.IMPLEMENTATION_BLUEPRINT_MD)
            ?: text(HandoffArtifact.IMPLEMENTATION_BLUEPRINT_TXT)

    /**
     * The declared size of an artifact without reading it.
     *
     * For deciding whether something fits a context budget before spending the
     * read. The manifest states every artifact's length, so this is free.
     */
    fun byteLength(artifact: HandoffArtifact): Long? = manifest.entryFor(artifact)?.byteLength

    /**
     * Artifacts that fit within [budgetBytes], largest first.
     *
     * The ordering is deliberate. Given a budget, the most useful thing to
     * include is the largest artifact that fits — the blueprint over the project
     * stub — and a caller filling greedily from smallest would spend its budget
     * on metadata.
     */
    fun withinBudget(budgetBytes: Long): List<HandoffArtifact> =
        verification.verified
            .mapNotNull { artifact -> byteLength(artifact)?.let { artifact to it } }
            .filter { it.second <= budgetBytes }
            .sortedByDescending { it.second }
            .map { it.first }

    companion object {

        /**
         * Opens [bundleRoot], verifying it first.
         *
         * @return null when the bundle does not verify. The caller falls back to
         *   the internal planner, which is a normal path — a machine with no
         *   SpecGraph export is not a machine with a fault.
         */
        fun open(bundleRoot: Path, verifier: ExportBundleVerifier = ExportBundleVerifier()): ExportBundleReader? {
            val verification = verifier.verify(bundleRoot)
            if (!verification.usable) return null
            return ExportBundleReader(bundleRoot, verification)
        }

        /**
         * Opens a bundle and says why when it cannot.
         *
         * The diagnostic form. [open] returning null is right for a caller with
         * a fallback; a caller reporting to an operator needs the failure list,
         * because "no SpecGraph plan was used" and "the SpecGraph plan failed
         * its checksum" require completely different responses.
         */
        fun openOrExplain(
            bundleRoot: Path,
            verifier: ExportBundleVerifier = ExportBundleVerifier()
        ): Pair<ExportBundleReader?, BundleVerification> {
            val verification = verifier.verify(bundleRoot)
            val reader = if (verification.usable) ExportBundleReader(bundleRoot, verification) else null
            return reader to verification
        }
    }
}
