package atropos.core.agent

import atropos.core.recovery.RestartCoordinator
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

class SelfHostStateSnapshotRecorderTest {
    @Test
    fun captureEvidencePersistsSnapshotAndReportsCounts() {
        val root = Files.createTempDirectory("atropos-self-host-snapshot-recorder-")
        val store = GoalRunStore(root)
        val goal = store.createGoalRun("snapshot proof", provider = "self-host")
        val recorder = SelfHostStateSnapshotRecorder(
            RestartCoordinator(root, goalRunStore = store)
        )

        val line = recorder.captureEvidence("unit", goal.id)

        assertTrue(line.startsWith("state_snapshot reason=unit"), line)
        assertTrue(line.contains("goals=1"), line)
        assertTrue(line.contains("goal=${goal.id}"), line)
        assertTrue(line.contains("hash=") && line.contains("node=none"), line)
        assertTrue(RestartCoordinator(root, goalRunStore = store).latestSnapshot() != null)
    }
}
