package atropos.core.agent

import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class AgentJobStoreTest {
    @Test
    fun latest_and_listJobs_prefer_most_recently_updated_job() {
        val repoRoot = Files.createTempDirectory("atropos-agent-job-order-")
        val base = Instant.parse("2026-07-27T08:50:00Z")
        var tick = 0L
        val store = AgentJobStore(repoRoot, clock = { base.plusSeconds(tick++) })

        val older = store.createJob("older job", "groq")
        val newer = store.createJob("newer job", "groq")
        val warmedOlder = store.update(
            older.copy(
                status = AgentJobStatus.REPAIRING,
                updatedAt = base.plusSeconds(10)
            )
        )

        val listed = store.listJobs(2)
        assertEquals(listOf(warmedOlder.id, newer.id), listed.map { it.id })
        assertEquals(warmedOlder.id, store.latest()?.id)
        assertEquals(warmedOlder.id, store.resolve("latest")?.id)
    }
}
