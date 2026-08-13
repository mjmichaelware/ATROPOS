/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.observability

import atropos.cli.ui.design.RunState
import atropos.core.journal.EventCategory
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Source Doc 3 §5.2 requires a full run to be exportable as Markdown *and*
 * JSON. Two exporters over one assembled shape, so the two exports of a run can
 * never disagree about what the run did — which they would if each walked the
 * journal itself.
 */
class RunExportTest {

    private val at = Instant.parse("2026-08-13T10:00:00Z")

    private fun event(
        sequence: Long,
        category: EventCategory = EventCategory.COMMAND,
        payload: String = "./gradlew test",
        requirement: String? = "SD3#5.2@L67-68",
        provider: String? = "groq",
        role: ExecutionRole = ExecutionRole.WORKER
    ) = ExecutionEvent(
        sequence = sequence,
        timestamp = at.plusSeconds(sequence),
        role = role,
        category = category,
        state = RunState.RUNNING,
        payload = payload,
        provider = provider,
        task = "close item 96",
        requirement = requirement,
        source = "MarkdownExporter.kt",
        runId = "run-1"
    )

    private fun export(vararg events: ExecutionEvent) =
        RunExport.of("run-1", events.toList(), at)

    @Test
    fun `cards are derived from events so the two cannot disagree`() {
        val run = export(event(1), event(2, EventCategory.DIFF, "--- a\n+++ b"))

        assertEquals(2, run.cardCount)
        assertEquals(CardKind.COMMAND, run.cards[0].kind)
        assertEquals(CardKind.DIFF, run.cards[1].kind)
    }

    @Test
    fun `events that are not card-worthy produce no card`() {
        val run = export(event(1, EventCategory.HEARTBEAT, "tick"), event(2))

        assertEquals(2, run.eventCount)
        assertEquals(1, run.cardCount, "a heartbeat is an event, not a card")
    }

    @Test
    fun `trace completeness counts events with all seven fields`() {
        val run = export(event(1), event(2, requirement = null))

        assertEquals(0.5, run.traceCompleteness)
        assertEquals(listOf(2L), run.incompleteEvents().map { it.sequence })
    }

    @Test
    fun `an empty run is complete but says so alongside its count`() {
        val run = export()

        assertEquals(1.0, run.traceCompleteness)
        assertEquals(0, run.eventCount, "completeness alone would be misleading")
    }

    @Test
    fun `requirements and providers are distinct and in first-seen order`() {
        val run = export(
            event(1, requirement = "A", provider = "groq"),
            event(2, requirement = "B", provider = "groq"),
            event(3, requirement = "A", provider = "gemini")
        )

        assertEquals(listOf("A", "B"), run.requirements())
        assertEquals(listOf("groq", "gemini"), run.providers())
    }

    @Test
    fun `roles are ordered by hierarchy, not by appearance`() {
        val run = export(
            event(1, role = ExecutionRole.WORKER),
            event(2, role = ExecutionRole.DIRECTOR),
            event(3, role = ExecutionRole.AUDITOR)
        )

        assertEquals(
            listOf(ExecutionRole.DIRECTOR, ExecutionRole.WORKER, ExecutionRole.AUDITOR),
            run.roles()
        )
    }

    @Test
    fun `failures are identified by outcome, not by kind alone`() {
        val run = export(event(1, EventCategory.ERROR, "boom"), event(2))

        assertEquals(listOf(1L), run.failures().map { it.sequence })
    }

    @Test
    fun `a card copies its body and nothing the renderer added`() {
        val card = OutputCard.from(event(1))!!
        val rendered = CardRenderer().renderFull(card)

        assertEquals("./gradlew test", card.copyText())
        assertTrue(rendered.contains("./gradlew test"))
        assertFalse(card.copyText().contains("Command #1"), "chrome must not reach the clipboard")
        assertFalse(card.copyText().contains("-----"))
    }

    @Test
    fun `a preview shortens but a copy never does`() {
        val body = (1..40).joinToString("\n") { "line $it" }
        val card = OutputCard.from(event(1, EventCategory.STDOUT, body))!!

        val preview = CardRenderer(previewLines = 5).renderPreview(card)

        assertTrue(preview.contains("35 more lines"))
        assertEquals(body, card.copyText(), "the clipboard gets all forty lines")
    }

    @Test
    fun `a fence inside a body cannot end its own code block`() {
        val body = "before\n```\ninside\n```\nafter"
        val card = OutputCard.from(event(1, EventCategory.STDOUT, body))!!

        val markdown = CardRenderer().renderMarkdown(card)
        val fences = Regex("(?m)^```").findAll(markdown).count()

        assertEquals(2, fences, "only the exporter's own fences may appear at line start")
    }

    @Test
    fun `markdown leads with failures because that is why an export is opened`() {
        val run = export(event(1), event(2, EventCategory.ERROR, "compile failed"))

        val markdown = MarkdownExporter().export(run)

        assertTrue(markdown.indexOf("## Failures") < markdown.indexOf("## Output"))
        assertTrue(markdown.contains("compile failed") || markdown.contains("Error #2"))
    }

    @Test
    fun `markdown names the events missing provenance rather than only counting them`() {
        val run = export(event(1), event(2, requirement = null))

        val markdown = MarkdownExporter().export(run)

        assertTrue(markdown.contains("1 of 2 events are missing required provenance"))
        assertTrue(markdown.contains("`#2` missing requirement"))
    }

    @Test
    fun `a payload with a pipe cannot break the trace table`() {
        val run = export(event(1, EventCategory.COMMAND, "grep x | wc -l"))

        val markdown = MarkdownExporter().export(run)
        // The card body also contains the command, and it comes first; the
        // trace row is the one shaped like a table row.
        val traceRow = markdown.lines().first { it.startsWith("| ") && it.contains("wc -l") }

        assertTrue(traceRow.contains("\\|"), "a literal pipe must be escaped inside a cell")
        assertEquals(
            8,
            traceRow.count { it == '|' } - traceRow.split("\\|").size + 1,
            "an escaped pipe must not add a column"
        )
    }

    @Test
    fun `the same run exports byte-identically twice`() {
        val run = export(event(1), event(2, EventCategory.DIFF, "--- a"))

        assertEquals(MarkdownExporter().export(run), MarkdownExporter().export(run))
        assertEquals(JsonExporter().export(run), JsonExporter().export(run))
    }

    @Test
    fun `json writes null explicitly rather than omitting the key`() {
        val run = export(event(1, requirement = null, provider = null))

        val json = JsonExporter().export(run)

        assertTrue(json.contains("\"requirement\": null"))
        assertTrue(json.contains("\"provider\": null"))
    }

    @Test
    fun `json escapes control characters so a damaged payload stays valid`() {
        val run = export(event(1, EventCategory.STDOUT, "a\u0001b\u0002c\td"))

        val json = JsonExporter().export(run)

        assertTrue(json.contains("\\u0001"))
        assertTrue(json.contains("\\u0002"))
        assertTrue(json.contains("\\t"))
        assertFalse(json.contains('\u0001'), "no raw control character may survive into JSON")
    }

    @Test
    fun `json escapes quotes and backslashes in a body`() {
        val run = export(event(1, EventCategory.STDOUT, """path "C:\x" ok"""))

        val json = JsonExporter().export(run)

        assertTrue(json.contains("""\"C:\\x\""""))
    }

    @Test
    fun `json carries the completeness flags a consumer would otherwise recompute`() {
        val run = export(event(1), event(2, requirement = null))

        val json = JsonExporter().export(run)

        assertTrue(json.contains("\"provenanceComplete\": true"))
        assertTrue(json.contains("\"provenanceComplete\": false"))
        assertTrue(json.contains("\"missingProvenance\": [\"requirement\"]"))
        assertTrue(json.contains("\"traceCompleteness\": 0.500"))
    }

    @Test
    fun `an empty run still exports valid documents`() {
        val run = export()

        val markdown = MarkdownExporter().export(run)
        val json = JsonExporter().export(run)

        assertTrue(markdown.contains("# Run run-1"))
        assertTrue(json.trimEnd().endsWith("}"))
        assertTrue(json.contains("\"events\": [\n  ],") || json.contains("\"events\": ["))
    }
}
