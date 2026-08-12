package atropos.core.factory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IntentParserTest {
    private val parser = IntentParser()

    @Test
    fun parses_general_cli_intent_without_product_specific_routes() {
        val intent = parser.parse("Build a simple notes CLI with tests")

        assertEquals("notes", intent.name)
        assertEquals("cli", intent.kind)
        assertTrue("notes" in intent.features)
        assertTrue(parser.isAppRequest("create a notes app"))
    }

    @Test
    fun classifies_surface_kind_and_excludes_surface_words_from_identity() {
        val web = parser.parse("Generate a web website dashboard")
        val service = parser.parse("Implement a weather API service")

        assertEquals("dashboard", web.name)
        assertEquals("web", web.kind)
        assertEquals("weather", service.name)
        assertEquals("service", service.kind)
        assertTrue("web" !in web.features)
        assertTrue("api" !in service.features)
    }
}
