package atropos.core.worktree

import java.nio.file.Path
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorktreeRecordCodecTest {

    private val codec = WorktreeRecordCodec()
    private val metaFile = Path.of("/tmp/wt-1.meta")

    private fun record(
        dirtyEvidence: String? = null,
        territory: List<String> = listOf("src/main"),
        appliedPatches: List<String> = emptyList(),
        verified: Boolean = false
    ) = WorktreeRecord(
        id = "wt-1",
        jobId = "job-9",
        worktreePath = Path.of("/tmp/wt-1"),
        baselineCommit = "abc123",
        territory = territory,
        dirtyEvidence = dirtyEvidence,
        appliedPatches = appliedPatches,
        verified = verified,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-01-02T00:00:00Z"),
        metaFile = metaFile
    )

    private fun roundTrip(source: WorktreeRecord): WorktreeRecord =
        assertNotNull(codec.decode(codec.encode(source).trimEnd().split("\n"), metaFile))

    @Test
    fun `a record survives a round trip`() {
        val decoded = roundTrip(record(territory = listOf("src/main", "docs")))
        assertEquals("wt-1", decoded.id)
        assertEquals("job-9", decoded.jobId)
        assertEquals("abc123", decoded.baselineCommit)
        assertEquals(listOf("src/main", "docs"), decoded.territory)
        assertEquals(Instant.parse("2026-01-01T00:00:00Z"), decoded.createdAt)
    }

    @Test
    fun `multi-line dirty evidence survives instead of truncating the record`() {
        val evidence = " M src/Main.kt\n?? untracked.kt\n M docs/README.md"
        val decoded = roundTrip(record(dirtyEvidence = evidence))
        assertEquals(
            evidence,
            decoded.dirtyEvidence,
            "written raw, line two would parse as a new field and the rest would be lost"
        )
    }

    @Test
    fun `a value containing an equals sign is not truncated`() {
        val decoded = roundTrip(record(dirtyEvidence = "key=value=more"))
        assertEquals("key=value=more", decoded.dirtyEvidence)
    }

    @Test
    fun `patch excerpts containing commas stay one entry`() {
        val decoded = roundTrip(record(appliedPatches = listOf("diff --git a/x,y b/x,y", "second")))
        assertEquals(listOf("diff --git a/x,y b/x,y", "second"), decoded.appliedPatches)
    }

    @Test
    fun `absent evidence decodes as null rather than an empty string`() {
        assertNull(roundTrip(record(dirtyEvidence = null)).dirtyEvidence)
    }

    @Test
    fun `verified survives when true`() {
        assertTrue(roundTrip(record(verified = true)).verified)
    }

    @Test
    fun `an unreadable verified flag reads as not verified`() {
        val decoded = codec.decode(
            listOf("id=wt-1", "worktreePath=/tmp/wt-1", "verified=maybe"),
            metaFile
        )
        assertNotNull(decoded)
        assertFalse(
            decoded.verified,
            "an unparseable flag must never present itself as a verification that happened"
        )
    }

    @Test
    fun `a missing timestamp falls back to the epoch rather than throwing`() {
        val decoded = assertNotNull(codec.decode(listOf("id=wt-1", "worktreePath=/tmp/wt-1"), metaFile))
        assertEquals(Instant.EPOCH, decoded.createdAt)
    }

    @Test
    fun `lines without a separator are ignored`() {
        val decoded = assertNotNull(
            codec.decode(listOf("garbage", "=leading", "id=wt-1", "worktreePath=/tmp/wt-1"), metaFile)
        )
        assertEquals("wt-1", decoded.id)
    }

    @Test
    fun `the meta file is taken from the caller, not the content`() {
        val elsewhere = Path.of("/tmp/other.meta")
        val decoded = assertNotNull(codec.decode(listOf("id=wt-1", "worktreePath=/tmp/wt-1"), elsewhere))
        assertEquals(elsewhere, decoded.metaFile)
    }
}
