package atropos.cli

import atropos.cli.config.ConfigurationManager
import atropos.cli.ui.AnsiTerminalEngine
import atropos.cli.ui.PlainTerminalOutput
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FactoryCommandHandlerTest {
    @Test
    fun completion_is_printed_only_after_factory_execution_succeeds() {
        val output = ByteArrayOutputStream()
        val handler = FactoryCommandHandler(
            uiEngine = engine(output, ByteArrayOutputStream()),
            runFactory = { "generated_project: $it" }
        )

        handler.execute(listOf("/factory", "run", "build", "notes"))

        val rendered = output.toString()
        assertTrue("factory run verified repository output:" in rendered)
        assertTrue("generated_project: build notes" in rendered)
    }

    @Test
    fun failed_factory_execution_never_emits_completion_claim() {
        val output = ByteArrayOutputStream()
        val errors = ByteArrayOutputStream()
        val handler = FactoryCommandHandler(
            uiEngine = engine(output, errors),
            runFactory = { error("generation failed") }
        )

        handler.execute(listOf("/factory", "run", "build", "notes"))

        assertFalse("factory run verified repository output:" in output.toString())
        assertTrue("factory run failed: generation failed" in errors.toString())
    }

    @Test
    fun an_attached_document_keeps_its_line_breaks_on_the_way_to_the_factory() {
        // The prompt was rebuilt from the word list joined with single spaces,
        // so a specification expanded in place by @mention arrived as one
        // paragraph. SpecGraph segments on structure: the same document
        // atomized to 14 with its line breaks and 0 without, and the run
        // reported SKIPPED_SOFT_FAIL:no_atoms_extracted and fell back.
        val output = ByteArrayOutputStream()
        var seen = ""
        val handler = FactoryCommandHandler(
            uiEngine = engine(output, ByteArrayOutputStream()),
            runFactory = { seen = it; "generated_project: ok" }
        )
        val document = "implement\nEngine language: Python 3.11\n- one bullet\n- another bullet"

        handler.execute(listOf("/factory", "run") + document.split(Regex("\\s+")), "/factory run $document")

        assertEquals(document, seen)
    }

    @Test
    fun a_prompt_the_original_line_does_not_match_falls_back_to_the_words() {
        // A different entry point, or an alias rewrite, means the original is
        // not the text the rest of the router acted on. The words win then.
        val output = ByteArrayOutputStream()
        var seen = ""
        val handler = FactoryCommandHandler(
            uiEngine = engine(output, ByteArrayOutputStream()),
            runFactory = { seen = it; "generated_project: ok" }
        )

        handler.execute(listOf("/factory", "run", "build", "notes"), "/factory run something else entirely")

        assertEquals("build notes", seen)
    }

    @Test
    fun no_original_line_behaves_exactly_as_before() {
        val output = ByteArrayOutputStream()
        var seen = ""
        val handler = FactoryCommandHandler(
            uiEngine = engine(output, ByteArrayOutputStream()),
            runFactory = { seen = it; "generated_project: ok" }
        )

        handler.execute(listOf("/factory", "run", "build", "notes"))

        assertEquals("build notes", seen)
    }

    private fun engine(output: ByteArrayOutputStream, errors: ByteArrayOutputStream): AnsiTerminalEngine =
        AnsiTerminalEngine(
            capabilities = ConfigurationManager(),
            plainOutput = PlainTerminalOutput(
                out = PrintStream(output),
                errors = PrintStream(errors)
            )
        )
}
