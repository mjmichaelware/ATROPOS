/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.specgraph

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Checks an export bundle against its own manifest before anything reads it.
 *
 * SpecGraph goes to considerable trouble to make its exports checkable — a
 * SHA-256 per artifact, a bundle fingerprint over the sorted metadata, and a
 * separate `export_proof_summary.json` — and `verify_export` performs exactly
 * this check on the producing side. Repeating it on the consuming side is not
 * redundant: the bundle travels through a signed download and a filesystem
 * between the two, and neither preserves the guarantee.
 *
 * Fail-closed. A bundle that cannot be verified is not planned from. The
 * failure mode this prevents is the expensive one — a truncated or stale
 * `atropos_handoff.json` parses perfectly well and yields a plan for the wrong
 * work, with lineage that looks correct all the way down.
 */
class ExportBundleVerifier {

    /**
     * Verifies [bundleRoot] against the `manifest.json` inside it.
     *
     * Reads files whole. The blueprint artifacts run to hundreds of kilobytes
     * and the JSON graphs larger still, but hashing requires every byte anyway,
     * and a bundle that does not fit in memory does not fit in a context window
     * either — [ExportBundleReader] is where selective loading belongs.
     */
    fun verify(bundleRoot: Path): BundleVerification {
        if (!Files.isDirectory(bundleRoot, LinkOption.NOFOLLOW_LINKS)) {
            return BundleVerification.unusable("bundle_directory_missing")
        }

        val manifestPath = bundleRoot.resolve(HandoffArtifact.MANIFEST.fileName)
        if (!Files.isRegularFile(manifestPath, LinkOption.NOFOLLOW_LINKS)) {
            return BundleVerification.unusable("manifest_missing")
        }

        val manifest = runCatching { Files.readString(manifestPath) }
            .getOrNull()
            ?.let(ExportManifest::parse)
            ?: return BundleVerification.unusable("manifest_unreadable")

        val failures = mutableListOf<String>()
        val verified = mutableSetOf<HandoffArtifact>()

        for (entry in manifest.entries) {
            val artifact = entry.artifact
            if (artifact == null) {
                // Declared by the manifest but outside the export contract.
                // Reported, never read: the contract is the agreement about what
                // a bundle may contain, and a manifest is not entitled to widen
                // it just by naming something else.
                failures += "undeclared_artifact:${entry.name}"
                continue
            }

            val path = bundleRoot.resolve(artifact.fileName)
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                failures += "missing:${artifact.fileName}"
                continue
            }

            val bytes = runCatching { Files.readAllBytes(path) }.getOrNull()
            if (bytes == null) {
                failures += "unreadable:${artifact.fileName}"
                continue
            }

            if (bytes.size.toLong() != entry.byteLength) {
                failures += "size_mismatch:${artifact.fileName}" +
                    " expected=${entry.byteLength} actual=${bytes.size}"
                continue
            }

            val actual = sha256(bytes)
            if (actual != entry.sha256) {
                failures += "checksum_mismatch:${artifact.fileName}"
                continue
            }

            verified += artifact
        }

        // The manifest is itself an artifact of the bundle, and a bundle whose
        // index verifies everything except its own presence has verified
        // nothing an attacker could not have arranged.
        verified += HandoffArtifact.MANIFEST

        val missingRequired = HandoffArtifact.requiredArtifacts()
            .filterNot { it in verified }
            .filterNot { it == HandoffArtifact.MANIFEST }

        missingRequired.forEach { failures += "required_absent:${it.fileName}" }

        return BundleVerification(
            root = bundleRoot,
            manifest = manifest,
            verified = verified,
            failures = failures.toList()
        )
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
}

/**
 * The result of checking a bundle.
 *
 * [usable] is the only question callers should ask before reading. It is false
 * whenever any declared artifact failed, not merely when the handoff did —
 * partial trust in a content-addressed bundle is not a coherent position, since
 * one artifact failing its hash says the bundle is not the bundle the manifest
 * describes.
 */
data class BundleVerification(
    val root: Path?,
    val manifest: ExportManifest?,
    val verified: Set<HandoffArtifact>,
    val failures: List<String>
) {
    val usable: Boolean
        get() = manifest != null && failures.isEmpty()

    /** Which stages of the build line this bundle carries, verified. */
    val stageCoverage: Map<HandoffStage, Boolean>
        get() = HandoffStage.coverageOf(verified)

    /** True when every stage of the SpecGraph build line arrived intact. */
    val buildLineComplete: Boolean
        get() = usable && HandoffStage.complete(verified)

    /**
     * One evidence line, in the project's PASS/SKIPPED_SOFT_FAIL vocabulary.
     *
     * Names the stage coverage on success rather than only the artifact count,
     * because "the bundle verified" and "the bundle carried the whole build
     * line" are different claims and only the second one is the goal.
     */
    fun evidenceLine(): String = when {
        manifest == null ->
            "SKIPPED_SOFT_FAIL:specgraph_bundle_unusable ${failures.firstOrNull() ?: "unknown"}"
        failures.isNotEmpty() ->
            "FAIL:specgraph_bundle_invalid failures=${failures.size} " +
                "first=${failures.first()}"
        else ->
            "PASS:specgraph_bundle_verified artifacts=${verified.size} " +
                "fingerprint=${manifest.bundleFingerprint} " +
                "plan=${manifest.planId} " +
                HandoffStage.render(verified)
    }

    companion object {
        /** A bundle that could not be opened far enough to check anything. */
        fun unusable(reason: String): BundleVerification = BundleVerification(
            root = null,
            manifest = null,
            verified = emptySet(),
            failures = listOf(reason)
        )
    }
}
