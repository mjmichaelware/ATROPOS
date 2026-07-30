package atropos.core.agent

import atropos.core.dag.DagExecutionService
import atropos.core.memory.LocalMemoryStore
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SelfHostGoalQueryServiceTest {
    @Test
    fun queries_are_read_only_and_select_only_self_host_records() {
        val root = Files.createTempDirectory("atropos-self-host-query-")
        val store = GoalRunStore(root)
        store.createGoalRun("self-host task", provider = "self-host")
        store.createGoalRun("unrelated task", provider = "provider")
        val queries = SelfHostGoalQueryService(
            store = store,
            dagService = DagExecutionService(repoRoot = root),
            memoryStore = LocalMemoryStore(root.resolve(".atropos/memory").toFile())
        )

        val selected = queries.resolve(null, requireUnfinished = true, operation = "resume")

        assertTrue(selected.ok)
        assertEquals("self-host", selected.goal?.record?.provider)
        assertEquals(1, queries.history().size)
        assertTrue(queries.unfinished().all { it.record.provider == "self-host" })
        assertFalse(queries.status("missing").status == GoalRunStatus.RUNNING)
    }
}
