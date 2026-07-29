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
        store.createGoalRun("snapshot proof", provider = "self-host")
        val recorder = SelfHostStateSnapshotRecorder(
            RestartCoordinator(root, goalRunStore = store)
        )

        val line = recorder.captureEvidence("unit")

        assertTrue(line.startsWith("state_snapshot reason=unit"), line)
        assertTrue(line.contains("goals=1"), line)
        assertTrue(RestartCoordinator(root, goalRunStore = store).latestSnapshot() != null)
    }
}
