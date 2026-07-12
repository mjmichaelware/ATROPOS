package atropos.core.dag

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DagServiceTest {
    @Test
    fun addAndRetrieveNode() {
        val dir = Files.createTempDirectory("dag-test-")
        val store = DagStore(dir)
        val svc = DagService(store, dir)

        val node = DAGNode(requirementId = "req-1", dependencies = emptyList())
        val saved = svc.addNode(node)
        assertEquals(node.requirementId, saved.requirementId)
        assertEquals(saved.id, svc.getNode(saved.id)?.id)
    }

    @Test
    fun runnableNodesRequiresCompletedDeps() {
        val dir = Files.createTempDirectory("dag-runnable-")
        val store = DagStore(dir)
        val svc = DagService(store, dir)

        val dep = svc.addNode(DAGNode(requirementId = "dep-req", state = DAGNodeState.COMPLETED))
        val blocked = svc.addNode(DAGNode(requirementId = "blocked-req", dependencies = listOf("nonexistent")))

        val runnable = svc.runnableNodes()
        assertTrue(runnable.none { it.id == blocked.id })
    }

    @Test
    fun cycleDetectionFindsCycles() {
        val dir = Files.createTempDirectory("dag-cycle-")
        val store = DagStore(dir)
        val svc = DagService(store, dir)

        val a = svc.addNode(DAGNode(requirementId = "req-a"))
        val b = svc.addNode(DAGNode(requirementId = "req-b"))

        val nodeA = svc.getNode(a.id)!!.copy(dependencies = listOf(b.id))
        svc.addNode(nodeA)
        val nodeB = svc.getNode(b.id)!!.copy(dependencies = listOf(a.id))
        svc.addNode(nodeB)

        val cycles = svc.detectCycles()
        assertTrue(cycles.isNotEmpty())
    }

    @Test
    fun dagSnapshotShowsCurrentState() {
        val dir = Files.createTempDirectory("dag-snap-")
        val store = DagStore(dir)
        val svc = DagService(store, dir)

        svc.addNode(DAGNode(requirementId = "r1", state = DAGNodeState.COMPLETED))
        svc.addNode(DAGNode(requirementId = "r2", state = DAGNodeState.PENDING))

        val snap = svc.dagSnapshot()
        assertEquals(2, snap.nodes.size)
        assertTrue(snap.sourceFingerprint.isNotBlank())
    }
}
