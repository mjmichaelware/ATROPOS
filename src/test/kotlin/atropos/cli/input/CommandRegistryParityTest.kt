/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.input

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards the one property that keeps the command palette honest: a slash
 * command the router accepts must be reachable from the palette, from
 * tab-completion and from `/help`.
 *
 * All three of those surfaces read [CommandRegistry] and nothing else
 * (`CommandPaletteRenderer` calls `slashMatches`, `CommandCompleter` calls
 * `commands`, `AnsiTerminalEngine.renderHelp` calls `helpLines`), so registry
 * parity with the router is the whole check.
 *
 * The router's set of families is **parsed out of the router source**, not
 * copied into this file. A hand-copied list is exactly the failure mode that
 * let eight shipped families (`/project`, `/projects`, `/ci`, `/assets`,
 * `/security`, `/tests`, `/ops`, `/swarm`) go undiscoverable in the first
 * place: a second list drifts silently, a parser cannot.
 */
class CommandRegistryParityTest {

    @Test
    fun every_routed_slash_family_is_registered() {
        val routed = routedFamilies()
        val registered = CommandRegistry.families()

        val missing = (routed - registered - CommandRegistry.unboundFamilies).sorted()

        assertTrue(
            missing.isEmpty(),
            "CommandRouter routes these slash families but CommandRegistry does not " +
                "advertise them, so they are invisible to the palette, to tab-completion " +
                "and to /help: $missing. Add a CommandEntry for each, or — if the family " +
                "cannot actually do what its name implies — name it in " +
                "CommandRegistry.unboundFamilies with the reason."
        )
    }

    @Test
    fun no_registered_command_is_unroutable() {
        val routed = routedFamilies()

        val orphaned = CommandRegistry.families()
            .filterNot { it in routed }
            .sorted()

        assertTrue(
            orphaned.isEmpty(),
            "CommandRegistry advertises these slash families but CommandRouter does not " +
                "route them, so the palette offers actions that resolve to " +
                "'unknown command': $orphaned"
        )
    }

    @Test
    fun exempt_families_are_routed_and_never_advertised() {
        val routed = routedFamilies()

        CommandRegistry.unboundFamilies.forEach { family ->
            assertTrue(
                family in routed,
                "$family is exempted from palette parity but the router no longer routes " +
                    "it — drop it from CommandRegistry.unboundFamilies"
            )
            assertTrue(
                CommandRegistry.families().none { it == family },
                "$family is both exempted and advertised; pick one"
            )
        }
    }

    @Test
    fun newly_registered_families_reach_all_three_surfaces() {
        // The families this atom closed. Each must be visible through every
        // surface, not merely present in the entry list.
        val closed = listOf(
            "/project",
            "/projects",
            "/ci",
            "/assets",
            "/security",
            "/tests",
            "/ops"
        )

        val help = CommandRegistry.helpLines()
        val completions = CommandRegistry.commands()

        closed.forEach { family ->
            assertTrue(
                completions.contains(family),
                "$family is not offered by tab-completion (CommandRegistry.commands)"
            )
            assertTrue(
                CommandRegistry.slashMatches(family).any { it.command == family },
                "$family is not matched by the command palette (slashMatches)"
            )

            // helpLines maps entries 1:1, so the aligned index is the exact
            // help row for this command — no assumption about its formatting.
            val index = CommandRegistry.entries.indexOfFirst { it.command == family }
            assertTrue(index >= 0, "$family has no CommandEntry")
            assertTrue(
                help[index].contains(family) &&
                    help[index].contains(CommandRegistry.entries[index].description),
                "$family does not appear with its description in /help " +
                    "(CommandRegistry.helpLines): '${help[index]}'"
            )
        }
    }

    @Test
    fun router_source_parse_is_not_silently_empty() {
        val routed = routedFamilies()

        // If the parse degrades to nothing or near-nothing the parity tests
        // above would pass vacuously, so assert the shape of the parse itself.
        assertTrue(
            routed.size >= 30,
            "parsed only ${routed.size} routed families from CommandRouter.kt — the " +
                "branch-label parse in this test has broken and parity is no longer " +
                "being checked"
        )
        assertTrue("/help" in routed, "parse missed /help; routed=$routed")
        assertTrue("/agent" in routed, "parse missed /agent; routed=$routed")
        assertEquals(
            emptyList<String>(),
            routed.filterNot { it.startsWith("/") },
            "parse produced non-slash tokens"
        )
    }

    private companion object {
        const val ROUTER_RELATIVE = "src/main/kotlin/atropos/cli/CommandRouter.kt"

        /** Start of the router's single dispatch `when`. */
        const val DISPATCH_MARKER = "when (tokens.first().lowercase())"

        /** First declaration after the dispatch `when`. */
        const val DISPATCH_END = "private fun switchProvider"

        /**
         * A `when` branch label line: one or more string literals, comma
         * separated, followed by `->`. Nothing else in Kotlin looks like this,
         * and every routed family is declared exactly this way.
         */
        val BRANCH_LABEL = Regex(
            """^\s*("[^"]*"\s*(?:,\s*"[^"]*"\s*)*)->""",
            RegexOption.MULTILINE
        )

        val LITERAL = Regex(""""([^"]*)"""")
    }

    /**
     * Derives the router's routed slash families by reading its source. Kept
     * deliberately narrow: only the dispatch `when` is scanned, so an unrelated
     * `when` added elsewhere in the router cannot pollute the result.
     */
    private fun routedFamilies(): Set<String> {
        val source = Files.readString(routerSource())

        val start = source.indexOf(DISPATCH_MARKER)
        assertTrue(
            start >= 0,
            "could not find '$DISPATCH_MARKER' in $ROUTER_RELATIVE — the router's " +
                "dispatch has been restructured and this parity guard must be updated " +
                "to match it"
        )

        val end = source.indexOf(DISPATCH_END, start)
        assertTrue(
            end > start,
            "could not find '$DISPATCH_END' after the dispatch when in $ROUTER_RELATIVE"
        )

        val dispatch = source.substring(start, end)

        return BRANCH_LABEL.findAll(dispatch)
            .flatMap { match -> LITERAL.findAll(match.groupValues[1]) }
            .map { it.groupValues[1].trim().lowercase() }
            // `exit` is routed as a bare word, not a slash command; it is not a
            // palette entry and is not expected to be one.
            .filter { it.startsWith("/") }
            .toSet()
    }

    /**
     * Locates the router source relative to the test's working directory,
     * walking up so the test does not depend on Gradle's choice of working
     * directory.
     */
    private fun routerSource(): Path {
        val origin = Path.of("").toAbsolutePath().normalize()
        var directory: Path? = origin
        var depth = 0
        while (directory != null && depth < 6) {
            val candidate = directory.resolve(ROUTER_RELATIVE)
            if (Files.isRegularFile(candidate)) return candidate
            directory = directory.parent
            depth++
        }
        throw AssertionError(
            "could not locate $ROUTER_RELATIVE from working directory $origin"
        )
    }
}
