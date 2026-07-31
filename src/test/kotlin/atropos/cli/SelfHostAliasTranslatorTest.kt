/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SelfHostAliasTranslatorTest {
    @Test
    fun bare_alias_starts_the_canonical_self_host_run() {
        assertEquals(
            listOf("/agent", "self-host"),
            SelfHostAliasTranslator.translate(listOf("/self-host"))
        )
        assertEquals(
            listOf("/agent", "self-host"),
            SelfHostAliasTranslator.translate(listOf("self-host"))
        )
    }

    @Test
    fun explicit_subcommands_remain_deterministic() {
        assertEquals(
            listOf("/agent", "self-host", "status"),
            SelfHostAliasTranslator.translate(listOf("/self-host", "status"))
        )
        assertEquals(
            listOf("/agent", "self-host", "run", "improve", "ATROPOS"),
            SelfHostAliasTranslator.translate(listOf("/self-host", "improve", "ATROPOS"))
        )
        assertNull(SelfHostAliasTranslator.translate(listOf("/status")))
    }
}
