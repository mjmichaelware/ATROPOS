package atropos.core.dag

import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ExecutionDagStoreTest {
    @Test
    fun `createDag rejects real cycles`() {
        val root = Files.createTempDirectory("atropos-execution-dag-cycle-")
        val store = DagStore(root)
        val now = Instant.parse("2026-07-27T00:00:00Z")

        val cycle = listOf(
            DagNode(
                id = "a",
                label = "A",
                dependencies = listOf("b"),
                createdAt = now,
                updatedAt = now,
                metaFile = root.resolve("a.meta")
            ),
            DagNode(
                id = "b",
                label = "B",
                dependencies = listOf("a"),
                createdAt = now,
                updatedAt = now,
                metaFile = root.resolve("b.meta")
            )
        )

        val error = assertFailsWith<IllegalArgumentException> {
            store.createDag("cycle", cycle)
        }
        assertTrue(error.message?.contains("acyclic") == true)
    }

    @Test
    fun `createDag persists and reads execution nodes separately from authority graph`() {
        val root = Files.createTempDirectory("atropos-execution-dag-store-")
        val store = DagStore(root)
        val now = Instant.parse("2026-07-27T00:00:00Z")

        store.saveNode(
            DAGNode(
                requirementId = "req-1",
                hash = "abc"
            )
        )

        val dag = store.createDag(
            label = "execution",
            nodes = listOf(
                DagNode(
                    id = "n1",
                    label = "root",
                    createdAt = now,
                    updatedAt = now,
                    metaFile = root.resolve("n1.meta")
                ),
                DagNode(
                    id = "n2",
                    label = "child",
                    dependencies = listOf("n1"),
                    createdAt = now,
                    updatedAt = now,
                    metaFile = root.resolve("n2.meta")
                )
            ),
            projectId = "project-test"
        )

        val reloaded = store.readDag(dag.id)
        assertNotNull(reloaded)
        assertEquals("project-test", reloaded.projectId)
        assertEquals(2, reloaded.nodes.size)
        assertTrue(reloaded.findReadyNodes().any { it.id == "n1" })
        assertEquals(1, store.loadNodes().size, "authority DAG nodes must remain in their original store")
    }

    @Test
    fun `claimNode records owner and lease expiry`() {
        val root = Files.createTempDirectory("atropos-execution-dag-claim-")
        val store = DagStore(root)
        val now = Instant.parse("2026-07-27T00:00:00Z")
        val dag = store.createDag(
            label = "claims",
            nodes = listOf(
                DagNode(
                    id = "n1",
                    label = "root",
                    createdAt = now,
                    updatedAt = now,
                    metaFile = root.resolve("n1.meta")
                )
            )
        )

        val claimed = store.claimNode("n1", owner = "tester", leaseDurationSeconds = 30)
        assertNotNull(claimed)
        assertEquals(DagNodeState.CLAIMED, claimed.state)
        assertEquals("tester", claimed.claimOwner)
        assertNotNull(claimed.claimToken)
        assertNotNull(claimed.claimExpiresAt)
    }
}
