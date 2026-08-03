package atropos.cli

import atropos.cli.config.ConfigurationManager
import atropos.cli.ui.AnsiTerminalEngine
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.test.Test
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

    private fun engine(output: ByteArrayOutputStream, errors: ByteArrayOutputStream): AnsiTerminalEngine =
        AnsiTerminalEngine(
            capabilities = ConfigurationManager(),
            out = PrintStream(output),
            errors = PrintStream(errors)
        )
}
