/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.specgraph

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The reader's contract is that it cannot exist for an unverified bundle. These
 * check that boundary holds, and that the large artifacts can be reached
 * selectively rather than by loading the whole export.
 */
class ExportBundleReaderTest {

    private val root: Path = createTempDirectory("specgraph-reader")

    @AfterTest
    fun cleanup() {
        Files.walk(root).sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
    }

    private val handoffJson = """
        {"schema":"specgraph.atropos.handoff.v1","producer":"specgraph-foundry",
         "project":{"id":"proj-1","slug":"demo","name":"Demo"},
         "plan":{"id":"plan-9","status":"VERIFIED","input_fingerprint":"fp",
                 "authority_graph_id":"ga","execution_graph_id":"ge"},
         "execution":{"graph_id":"ge","nodes":[{"id":"n1","node_key":"k","node_type":"CONTRACT",
                      "title":"t","status":"PENDING","payload_json":"{}"}],
                      "edges":[],"ready_node_ids":["n1"]},
         "requirements":[],"routing_law":["LOCAL_TOOLCHAIN"],
         "execution_contract":{"authority_owner":"specgraph-foundry","runtime_owner":"atropos",
           "source_authority_is_immutable":true,"execution_graph_must_be_acyclic":true,
           "implementation_requires_verification":true}}
    """.trimIndent()

    private val blueprint = "# Implementation Blueprint\n\n" + "Detail line.\n".repeat(200)

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun writeBundle() {
        val bodies = HandoffArtifact.requiredArtifacts()
            .filterNot { it == HandoffArtifact.MANIFEST }
            .associateWith { artifact ->
                when (artifact) {
                    HandoffArtifact.ATROPOS_HANDOFF -> handoffJson
                    HandoffArtifact.IMPLEMENTATION_BLUEPRINT_MD -> blueprint
                    HandoffArtifact.IMPLEMENTATION_BLUEPRINT_TXT -> "Implementation Blueprint\n"
                    else -> """{"artifact":"${artifact.fileName}"}"""
                }
            }

        val entries = bodies.map { (artifact, body) ->
            val bytes = body.toByteArray()
            Files.write(root.resolve(artifact.fileName), bytes)
            """{"name":"${artifact.fileName}","sha256":"${sha256(bytes)}","bytes":${bytes.size}}"""
        }

        Files.writeString(
            root.resolve(HandoffArtifact.MANIFEST.fileName),
            """{"schema":"specgraph.export.manifest.v1","export_id":"e","plan_id":"plan-9",
                "project_id":"proj-1","bundle_fingerprint":"fp-1","artifacts":[${entries.joinToString(",")}]}"""
        )
    }

    @Test
    fun `a verified bundle opens and yields its handoff`() {
        writeBundle()

        val reader = assertNotNull(ExportBundleReader.open(root))

        assertEquals("plan-9", reader.manifest.planId)
        assertEquals("plan-9", assertNotNull(reader.handoff()).plan.id)
    }

    /**
     * The boundary the type exists to enforce: no reader for an unverified
     * bundle, so no caller can skip the check.
     */
    @Test
    fun `a corrupt bundle yields no reader at all`() {
        writeBundle()
        Files.writeString(root.resolve(HandoffArtifact.ATROPOS_HANDOFF.fileName), "tampered")

        assertNull(ExportBundleReader.open(root))
    }

    @Test
    fun `the diagnostic form says why it refused`() {
        writeBundle()
        Files.delete(root.resolve(HandoffArtifact.RESEARCH.fileName))

        val (reader, verification) = ExportBundleReader.openOrExplain(root)

        assertNull(reader)
        assertTrue(verification.failures.contains("missing:research.json"))
    }

    @Test
    fun `the blueprint is reachable without loading the rest of the bundle`() {
        writeBundle()

        val reader = assertNotNull(ExportBundleReader.open(root))

        assertEquals(blueprint, reader.blueprint())
    }

    @Test
    fun `sizes are known from the manifest without reading the file`() {
        writeBundle()

        val reader = assertNotNull(ExportBundleReader.open(root))

        assertEquals(
            blueprint.toByteArray().size.toLong(),
            reader.byteLength(HandoffArtifact.IMPLEMENTATION_BLUEPRINT_MD)
        )
    }

    /**
     * Given a budget, the most useful artifact is the largest that fits. Filling
     * from smallest would spend the budget on metadata stubs.
     */
    @Test
    fun `budgeted selection returns the largest artifacts that fit, first`() {
        writeBundle()
        val reader = assertNotNull(ExportBundleReader.open(root))

        val selected = reader.withinBudget(blueprint.toByteArray().size.toLong())

        assertEquals(HandoffArtifact.IMPLEMENTATION_BLUEPRINT_MD, selected.first())
        assertTrue(selected.size > 1, "smaller artifacts still fit the same budget")
    }

    @Test
    fun `a budget smaller than everything selects nothing rather than failing`() {
        writeBundle()

        assertEquals(emptyList(), assertNotNull(ExportBundleReader.open(root)).withinBudget(1))
    }
}
