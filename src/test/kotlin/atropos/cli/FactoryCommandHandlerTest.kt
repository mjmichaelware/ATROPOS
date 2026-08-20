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
    fun a_different_command_line_falls_back_to_the_words() {
        // The original has to be the line these tokens were lexed from, and
        // the head is what establishes that.
        val output = ByteArrayOutputStream()
        var seen = ""
        val handler = FactoryCommandHandler(
            uiEngine = engine(output, ByteArrayOutputStream()),
            runFactory = { seen = it; "generated_project: ok" }
        )

        handler.execute(listOf("/factory", "run", "build", "notes"), "/agent ask something entirely")

        assertEquals("build notes", seen)
    }

    @Test
    fun the_lexer_normalising_the_body_does_not_discard_the_document() {
        // Comparing the whole collapsed body against the joined tokens looked
        // safer and was useless: the lexer normalises quoting and punctuation,
        // so on any real document the two differed somewhere and the
        // structured text was thrown away every time. Measured: the same
        // build specification atomizes to 282 with its line breaks and 1
        // without.
        val output = ByteArrayOutputStream()
        var seen = ""
        val handler = FactoryCommandHandler(
            uiEngine = engine(output, ByteArrayOutputStream()),
            runFactory = { seen = it; "generated_project: ok" }
        )
        val document = "implement\nStack:\n  app/\n    main.py\n\n\"quoted\" and (punctuated)"

        // Tokens as a lexer would hand them over: quoting resolved, so they no
        // longer match the raw body character for character.
        handler.execute(
            listOf("/factory", "run", "implement", "Stack:", "app/", "main.py", "quoted", "and", "punctuated"),
            "/factory run $document"
        )

        assertEquals(document, seen)
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
