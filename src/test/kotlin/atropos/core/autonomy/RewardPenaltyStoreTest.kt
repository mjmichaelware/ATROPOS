package atropos.core.autonomy

import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.Test

class RewardPenaltyStoreTest {

    @Test
    fun `test record and query rewards`() {
        val tempDir = Files.createTempDirectory("reward-store-test-").toFile()
        try {
            val store = RewardPenaltyStore(tempDir)
            store.recordReward("agent-1", "action-1", 1.0, "reason 1")
            store.recordPenalty("agent-1", "action-2", -1.0, "reason 2")

            val agentSignals = store.queryByAgent("agent-1")
            assertEquals(2, agentSignals.size)

            val actionSignals = store.queryByAction("action-1")
            assertEquals(1, actionSignals.size)
            assertEquals(RewardPenaltyStore.SignalType.REWARD, actionSignals[0].type)
            assertEquals(1.0, actionSignals[0].value)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
