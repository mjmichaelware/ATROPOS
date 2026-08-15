/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.specgraph

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * SpecGraph hashes every artifact and fingerprints the bundle, and
 * `verify_export` checks it on the producing side. The bundle then travels
 * through a signed download and a filesystem, neither of which preserves that
 * guarantee — so these check that the consuming side refuses what the producing
 * side would have.
 */
class ExportBundleVerifierTest {

    private val root: Path = createTempDirectory("specgraph-bundle")
    private val verifier = ExportBundleVerifier()

    @AfterTest
    fun cleanup() {
        Files.walk(root).sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** Writes a bundle whose manifest correctly describes its contents. */
    private fun writeBundle(
        artifacts: List<HandoffArtifact> = HandoffArtifact.requiredArtifacts(),
        body: (HandoffArtifact) -> String = { "{\"artifact\": \"${it.fileName}\"}" }
    ) {
        val entries = artifacts
            .filterNot { it == HandoffArtifact.MANIFEST }
            .map { artifact ->
                val bytes = body(artifact).toByteArray()
                Files.write(root.resolve(artifact.fileName), bytes)
                """{"name": "${artifact.fileName}", "sha256": "${sha256(bytes)}", "bytes": ${bytes.size}}"""
            }

        Files.writeString(
            root.resolve(HandoffArtifact.MANIFEST.fileName),
            """
            {
              "schema": "specgraph.export.manifest.v1",
              "export_id": "exp-1",
              "plan_id": "plan-9",
              "project_id": "proj-1",
              "bundle_fingerprint": "fingerprint-1",
              "artifacts": [${entries.joinToString(",")}]
            }
            """.trimIndent()
        )
    }

    @Test
    fun `an intact bundle verifies and reports the whole build line`() {
        writeBundle()

        val result = verifier.verify(root)

        assertTrue(result.usable, result.failures.joinToString())
        assertTrue(result.buildLineComplete)
        assertTrue(result.evidenceLine().startsWith("PASS:specgraph_bundle_verified"))
        assertEquals("plan-9", assertNotNull(result.manifest).planId)
    }

    @Test
    fun `a bundle with no manifest is unusable rather than empty`() {
        val result = verifier.verify(root)

        assertFalse(result.usable)
        assertEquals(listOf("manifest_missing"), result.failures)
        assertTrue(result.evidenceLine().startsWith("SKIPPED_SOFT_FAIL"))
    }

    @Test
    fun `a missing directory is named as such`() {
        assertEquals(
            listOf("bundle_directory_missing"),
            verifier.verify(root.resolve("absent")).failures
        )
    }

    /**
     * The expensive failure. A tampered or stale `atropos_handoff.json` parses
     * perfectly and yields a plan for the wrong work, with lineage that reads
     * correct all the way down.
     */
    @Test
    fun `a modified artifact fails its checksum and makes the bundle unusable`() {
        writeBundle()
        Files.writeString(
            root.resolve(HandoffArtifact.ATROPOS_HANDOFF.fileName),
            """{"artifact": "atropos_handoff.json", "tampered": true}"""
        )

        val result = verifier.verify(root)

        assertFalse(result.usable)
        assertTrue(result.failures.any { it.startsWith("size_mismatch:atropos_handoff.json") })
        assertFalse(HandoffArtifact.ATROPOS_HANDOFF in result.verified)
    }

    @Test
    fun `a same-length substitution is caught by the hash rather than the size`() {
        writeBundle()
        val path = root.resolve(HandoffArtifact.ATROPOS_HANDOFF.fileName)
        val original = Files.readAllBytes(path)
        val swapped = original.copyOf()
        // Same length, different content: only the digest can tell.
        swapped[swapped.size - 2] = 'X'.code.toByte()
        Files.write(path, swapped)

        val result = verifier.verify(root)

        assertTrue(result.failures.any { it == "checksum_mismatch:atropos_handoff.json" })
    }

    @Test
    fun `a truncated bundle names the artifact that did not arrive`() {
        writeBundle()
        Files.delete(root.resolve(HandoffArtifact.RESEARCH.fileName))

        val result = verifier.verify(root)

        assertFalse(result.usable)
        assertTrue(result.failures.contains("missing:research.json"))
        assertFalse(result.buildLineComplete)
        assertTrue(HandoffStage.missingFrom(result.verified).contains(HandoffStage.RESEARCH))
    }

    /**
     * A manifest is not entitled to widen the export contract just by naming
     * something else. A reader that silently skipped the extra file could not
     * tell a stray artifact from a planted one.
     */
    @Test
    fun `a manifest naming a file outside the contract is refused`() {
        writeBundle()
        val manifestPath = root.resolve(HandoffArtifact.MANIFEST.fileName)
        Files.writeString(
            manifestPath,
            Files.readString(manifestPath).replace(
                "\"artifacts\": [",
                "\"artifacts\": [{\"name\": \"payload.sh\", \"sha256\": \"${"a".repeat(64)}\", \"bytes\": 1},"
            )
        )

        val result = verifier.verify(root)

        assertFalse(result.usable)
        assertTrue(result.failures.contains("undeclared_artifact:payload.sh"))
    }

    @Test
    fun `a manifest with a malformed hash is rejected as a manifest fault`() {
        writeBundle()
        val manifestPath = root.resolve(HandoffArtifact.MANIFEST.fileName)
        val text = Files.readString(manifestPath)
        val badHash = text.replace(Regex("\"sha256\": \"[0-9a-f]{64}\""), "\"sha256\": \"short\"")
        Files.writeString(manifestPath, badHash)

        val result = verifier.verify(root)

        // Every entry became unparseable, so the manifest itself is unreadable
        // rather than the artifacts being corrupt.
        assertFalse(result.usable)
        assertEquals(listOf("manifest_unreadable"), result.failures)
    }

    @Test
    fun `a manifest of an unknown schema is not read`() {
        writeBundle()
        val manifestPath = root.resolve(HandoffArtifact.MANIFEST.fileName)
        Files.writeString(
            manifestPath,
            Files.readString(manifestPath).replace("manifest.v1", "manifest.v2")
        )

        assertEquals(listOf("manifest_unreadable"), verifier.verify(root).failures)
    }

    @Test
    fun `checksums file is optional and its absence is not a failure`() {
        writeBundle()

        val result = verifier.verify(root)

        assertTrue(result.usable)
        assertFalse(HandoffArtifact.CHECKSUMS.required)
        assertFalse(HandoffArtifact.CHECKSUMS in result.verified)
    }

    @Test
    fun `stage coverage maps each build-line stage to its carrier`() {
        writeBundle()

        val coverage = verifier.verify(root).stageCoverage

        assertEquals(HandoffStage.entries.size, coverage.size)
        assertTrue(coverage.values.all { it })
        assertEquals(HandoffArtifact.ATOMS, HandoffStage.ATOMS.carrier)
        assertEquals(HandoffArtifact.ATROPOS_HANDOFF, HandoffStage.PLAN_VERIFICATION.carrier)
    }
}
