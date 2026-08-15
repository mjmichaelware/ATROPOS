/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.factory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The requirements document was written entirely as `key=value` lines, and
 * SpecGraph's `AtomService.extract_document` keys on modal requirement
 * sentences. Measured against the atomizer directly, the document ATROPOS
 * generated produced **zero** atoms while a modal-verb document produced atoms
 * from the same content — so the canonical atomizer could never have planned a
 * factory run.
 */
class FactoryRequirementStatementsTest {

    private val intent = AppIntent(
        name = "todo",
        kind = "cli",
        features = listOf("todo", "list", "stores", "tasks", "file", "mark", "done")
    )

    @Test
    fun `every statement is a modal requirement the atomizer can see`() {
        val statements = FactoryRequirementStatements.statements(intent)

        assertTrue(statements.isNotEmpty())
        statements.forEach { statement ->
            assertTrue(
                statement.contains(" MUST "),
                "\"$statement\" carries no modal verb, so it is invisible to the atomizer"
            )
            assertTrue(statement.endsWith("."), "\"$statement\" is not a sentence")
        }
    }

    @Test
    fun `every feature reaches the document as its own requirement`() {
        val rendered = FactoryRequirementStatements.render(intent)

        intent.features.forEach { feature ->
            assertTrue(rendered.contains("MUST support $feature."), "$feature never became a requirement")
        }
    }

    /**
     * These hold on every run regardless of prompt, and are stated as
     * requirements rather than as metadata so they are atomized alongside the
     * features — they are the ones that must not be quietly dropped.
     */
    @Test
    fun `the invariants are stated as requirements, not as metadata`() {
        val statements = FactoryRequirementStatements.statements(AppIntent("x", "cli", emptyList()))

        assertTrue(statements.any { it.contains("MUST compile") })
        assertTrue(statements.any { it.contains("tests MUST pass") })
        assertTrue(statements.any { it.contains("MUST NOT mutate the host repository") })
        assertTrue(statements.any { it.contains("MUST record evidence") })
    }

    /**
     * The document is hashed and that hash ties an atomization to its prompt.
     * Non-deterministic text would fail the lineage check on a document that did
     * not meaningfully change.
     */
    @Test
    fun `rendering is deterministic`() {
        assertEquals(FactoryRequirementStatements.render(intent), FactoryRequirementStatements.render(intent))
    }

    @Test
    fun `a prompt with no features still states the invariants`() {
        val rendered = FactoryRequirementStatements.render(AppIntent("app", "cli", emptyList()))

        assertTrue(rendered.contains("## Requirements"))
        assertTrue(rendered.contains("MUST compile"))
    }

    /**
     * The `me` regression, at the layer that shows it in the document: an
     * indirect-object pronoun became the application's name and half the
     * feature budget went to function words.
     */
    @Test
    fun `a politely phrased prompt does not name the app after a pronoun`() {
        val parsed = IntentParser().parse("build me a todo list app that stores tasks in a file and can mark them done")

        assertEquals("todo", parsed.name)
        listOf("me", "that", "in", "can", "them", "a", "and").forEach { word ->
            assertFalse(word in parsed.features, "\"$word\" is a function word, not a feature")
        }
        assertTrue(parsed.features.containsAll(listOf("todo", "tasks", "file", "mark", "done")))
    }

    @Test
    fun `other polite phrasings are also handled`() {
        assertEquals("invoice", IntentParser().parse("please build us an invoice tracker cli").name)
        assertEquals("notes", IntentParser().parse("can you make me a simple notes app").name)
        assertEquals("budget", IntentParser().parse("I want a budget tool").name)
    }

    @Test
    fun `a prompt of nothing but function words falls back rather than naming one`() {
        assertEquals("generated-app", IntentParser().parse("build me a simple app").name)
    }
}
