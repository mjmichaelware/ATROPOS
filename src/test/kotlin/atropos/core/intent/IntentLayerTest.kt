/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.intent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IntentLayerTest {

    @Test
    fun `CanonicalVerb has 13 verbs`() {
        assertEquals(13, CanonicalVerb.values().size)
    }

    @Test
    fun `ActionRegistry holds metadata for all 13 verbs`() {
        CanonicalVerb.values().forEach { verb ->
            val meta = ActionRegistry.get(verb)
            assertNotNull(meta)
            assertEquals(verb, meta.verb)
        }
    }

    @Test
    fun `AliasResolver resolves direct keywords and aliases`() {
        assertEquals(CanonicalVerb.GOAL, AliasResolver.resolve("/goal"))
        assertEquals(CanonicalVerb.GOAL, AliasResolver.resolve("goal"))
        assertEquals(CanonicalVerb.GOAL, AliasResolver.resolve("g"))
        assertEquals(CanonicalVerb.STATUS, AliasResolver.resolve("/status"))
        assertEquals(CanonicalVerb.STATUS, AliasResolver.resolve("state"))
        assertNull(AliasResolver.resolve("unknown"))
    }

    @Test
    fun `CommandConsolidator consolidates verb aliases to canonical keywords`() {
        val input = listOf("g", "run", "goal-123")
        val output = CommandConsolidator.consolidate(input)
        assertEquals(listOf("/goal", "run", "goal-123"), output)

        val unresolvable = listOf("run-tests", "now")
        assertEquals(unresolvable, CommandConsolidator.consolidate(unresolvable))
    }

    @Test
    fun `NlPhraseMapper maps phrases to canonical verbs`() {
        assertEquals(CanonicalVerb.GRILL_ME, NlPhraseMapper.mapPhrase("please interview me now"))
        assertEquals(CanonicalVerb.STORAGE, NlPhraseMapper.mapPhrase("disk garbage collect clean"))
        assertNull(NlPhraseMapper.mapPhrase("hello world"))
    }

    @Test
    fun `ArgumentGuidance provides inline usage directions`() {
        val guidance = ArgumentGuidance.getGuidance("sched")
        assertNotNull(guidance)
        assertTrue(guidance.contains("/schedule"))
        assertTrue(guidance.contains("cron-expr"))
    }
}
