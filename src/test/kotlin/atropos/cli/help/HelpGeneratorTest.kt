/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.help

import atropos.cli.input.CommandEntry
import atropos.cli.input.CommandRisk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `SUP.UX.HELP-GENERATOR`: three disclosure levels, all derived from the
 * registry so help cannot drift from implementation.
 */
class HelpGeneratorTest {

    private val sample = listOf(
        CommandEntry("/status", "engine status", "Orient", risk = CommandRisk.AUTOMATIC),
        CommandEntry("/status wide", "full status matrix", "Orient", risk = CommandRisk.AUTOMATIC),
        CommandEntry(
            "/self-host run",
            "run the self-build loop",
            "Self-host",
            aliases = listOf("/agent self-host run"),
            risk = CommandRisk.RISKY,
            example = "/self-host run build yourself"
        )
    )

    private val source = object : HelpSource {
        override fun entries(): List<CommandEntry> = sample
        override fun search(query: String): List<CommandEntry> =
            sample.filter { it.command.contains(query, ignoreCase = true) }
    }

    private val generator = HelpGenerator(source)

    @Test
    fun `summary names categories and stays short`() {
        val lines = generator.render(HelpLevel.SUMMARY)

        assertTrue(lines.any { it.startsWith("Orient") })
        assertTrue(lines.any { it.startsWith("Self-host") })
        assertTrue(lines.any { it.contains("'/help full'") })
    }

    @Test
    fun `summary shows the shortest command in a family first`() {
        val orient = generator.render(HelpLevel.SUMMARY).first { it.startsWith("Orient") }

        assertTrue(orient.indexOf("/status ") < orient.indexOf("/status wide"))
    }

    @Test
    fun `full lists every command with its description`() {
        val lines = generator.render(HelpLevel.FULL)

        sample.forEach { entry ->
            assertTrue(
                lines.any { it.contains(entry.command) && it.contains(entry.description) },
                "${entry.command} missing from full help"
            )
        }
    }

    @Test
    fun `full does not show policy class`() {
        assertFalse(generator.render(HelpLevel.FULL).any { it.contains("risky") })
    }

    @Test
    fun `expert shows what each command can touch`() {
        val lines = generator.render(HelpLevel.EXPERT)

        assertTrue(lines.any { it.contains("risky") && it.contains("/self-host run") })
        assertTrue(lines.any { it.contains("automatic") && it.contains("/status") })
        assertTrue(lines.any { it.contains("also: /agent self-host run") })
        assertTrue(lines.any { it.contains("e.g. /self-host run build yourself") })
    }

    @Test
    fun `a filter narrows every level`() {
        val lines = generator.render(HelpLevel.FULL, "self-host")

        assertTrue(lines.any { it.contains("/self-host run") })
        assertFalse(lines.any { it.contains("/status") })
    }

    @Test
    fun `an unmatched filter suggests a broader one instead of showing nothing`() {
        val lines = generator.render(HelpLevel.FULL, "zzzz")

        assertTrue(lines.first().contains("No command matches"))
        assertTrue(lines.any { it.contains("/help") })
    }

    @Test
    fun `an unknown level falls back to summary rather than failing`() {
        assertEquals(HelpLevel.SUMMARY, HelpLevel.fromCanonical("gibberish"))
        assertEquals(HelpLevel.SUMMARY, HelpLevel.fromCanonical(null))
        assertEquals(HelpLevel.EXPERT, HelpLevel.fromCanonical(" Expert "))
    }

    @Test
    fun `help contains no command name the source did not supply`() {
        val rendered = generator.render(HelpLevel.EXPERT).joinToString("\n")

        val slashWords = Regex("""/[a-z-]+""").findAll(rendered).map { it.value }.toSet()
        val known = sample.flatMap { listOf(it.command) + it.aliases }
            .flatMap { it.split(' ') }
            .toSet() + "/help"

        slashWords.forEach { word ->
            assertTrue(
                known.any { it.startsWith(word) },
                "'$word' appears in help but came from nowhere in the registry"
            )
        }
    }
}
