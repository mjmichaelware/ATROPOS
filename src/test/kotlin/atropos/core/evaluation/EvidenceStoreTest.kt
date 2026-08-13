/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.evaluation

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The store is what makes "evidence-backed" mean something. Source Doc 3 §4.1
 * requires every metric to link to raw immutable evidence; §4.4 requires that
 * scoring be auditable. Both collapse if evidence can be revised after a metric
 * cited it, or if a citation can point at nothing and still look supported.
 */
class EvidenceStoreTest {

    private fun store(): Pair<EvidenceStore, Path> {
        val root = Files.createTempDirectory("atropos-evidence-")
        return EvidenceStore(repoRoot = root) to root
    }

    @Test
    fun `the same content yields the same hash and is stored once`() {
        val (store, _) = store()

        val first = store.put("verifier finding: territory ok")
        val second = store.put("verifier finding: territory ok")

        assertEquals(first, second)
        assertEquals(1, store.count())
    }

    @Test
    fun `different content yields different hashes`() {
        val (store, _) = store()

        assertNotEquals(store.put("a"), store.put("b"))
        assertEquals(2, store.count())
    }

    @Test
    fun `stored content comes back exactly`() {
        val (store, _) = store()
        val content = "line one\nline two\twith a tab\nünïcödé"

        assertEquals(content, store.get(store.put(content)))
    }

    @Test
    fun `an unheld hash is null rather than an error`() {
        val (store, _) = store()

        assertNull(store.get("f".repeat(64)))
        assertFalse(store.has("f".repeat(64)))
    }

    /**
     * A secret hashed into immutable storage is a secret that cannot be
     * removed, so redaction has to happen before the hash rather than on read.
     */
    @Test
    fun `a secret is redacted before it is hashed`() {
        val (store, _) = store()

        val hash = store.put("calling with api_key=sk-abcdefghijklmnop")
        val stored = store.get(hash)!!

        assertFalse(stored.contains("sk-abcdefghijklmnop"), "the secret must not be on disk")

        // The hash is of the redacted bytes, not the raw ones: storing the
        // already-redacted form must land on the same object rather than a
        // second one. If the hash were of the raw input, these would differ and
        // the store would hold two copies of one finding.
        assertEquals(hash, store.put(stored))
        assertEquals(1, store.count())
        assertTrue(store.verify(listOf(hash)).intact, "a redacted object still verifies against its hash")
    }

    @Test
    fun `verification confirms every cited hash is present`() {
        val (store, _) = store()
        val hashes = store.putAll(listOf("one", "two", "three"))

        val verification = store.verify(hashes)

        assertTrue(verification.intact)
        assertEquals(3, verification.cited)
        assertTrue(verification.render().contains("3 evidence object(s) verified"))
    }

    @Test
    fun `a citation to nothing is reported missing rather than assumed fine`() {
        val (store, _) = store()
        val real = store.put("real")

        val verification = store.verify(listOf(real, "0".repeat(64)))

        assertFalse(verification.intact)
        assertEquals(listOf("0".repeat(64)), verification.missing)
        assertEquals(emptyList(), verification.corrupt)
    }

    /**
     * Content changing after a metric cited it is tampering, not an omission,
     * and the two are reported separately because they call for different
     * responses.
     */
    @Test
    fun `content changed after citation is reported corrupt, distinctly from missing`() {
        val (store, root) = store()
        val hash = store.put("original finding")

        val objectPath = root.resolve(".atropos/evidence").resolve(hash.take(2)).resolve(hash)
        Files.writeString(objectPath, "tampered finding")

        val verification = store.verify(listOf(hash))

        assertFalse(verification.intact)
        assertEquals(listOf(hash), verification.corrupt)
        assertEquals(emptyList(), verification.missing)
        assertTrue(verification.render().contains("corrupt"))
    }

    @Test
    fun `a partial write is never visible under a completed hash`() {
        val (store, root) = store()
        store.put("content")

        val evidence = root.resolve(".atropos/evidence")
        val partials = Files.walk(evidence).use { stream ->
            stream.filter { it.fileName.toString().endsWith(".partial") }.count()
        }

        assertEquals(0L, partials, "staging files must not survive a completed put")
    }

    @Test
    fun `hashes fan out so a large store stays listable`() {
        val (store, root) = store()
        repeat(20) { store.put("object $it") }

        val topLevel = Files.list(root.resolve(".atropos/evidence")).use { it.count() }

        assertTrue(topLevel > 1, "all objects in one directory would not scale on phone storage")
        assertEquals(20, store.count())
    }

    @Test
    fun `an empty citation list verifies vacuously but supports nothing`() {
        val (store, _) = store()

        assertTrue(store.verify(emptyList()).intact)
        assertFalse(
            AtroposMetric(MetricId.TERRITORY_SAFETY, 1.0, 100).supported,
            "a metric with no citations is unsupported however clean the store is"
        )
    }
}
