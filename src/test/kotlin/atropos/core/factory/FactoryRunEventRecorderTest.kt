package atropos.core.factory

import atropos.core.journal.EventJournalService
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue

class FactoryRunEventRecorderTest {
    @Test
    fun preview_stage_is_recorded_in_the_canonical_factory_journal() {
        val root = Files.createTempDirectory("atropos-factory-events-")
        val journal = EventJournalService(root)
        val recorder = FactoryRunEventRecorder(journal)

        recorder.recordPreview(
            runId = "factory-test",
            state = "INSPECTED",
            impactedSymbols = 3,
            browserStatus = "UNSUPPORTED",
            dagId = "dag-test",
            promptFingerprint = "pf-test"
        )

        val transcript = journal.transcript("factory-test", 10)
        assertTrue(transcript.contains("factory_preview"), transcript)
        assertTrue(transcript.contains("impacted_symbols=3"), transcript)
        assertTrue(transcript.contains("browser=UNSUPPORTED"), transcript)
    }
}
