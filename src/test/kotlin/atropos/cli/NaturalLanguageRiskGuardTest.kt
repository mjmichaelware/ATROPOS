/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import atropos.cli.input.CommandRisk
import atropos.cli.input.CommandRiskCatalog

class NaturalLanguageRiskGuardTest {
    private val guard = NaturalLanguageRiskGuard()

    @Test fun identifies_force_push_without_flagging_normal_factory_requests() {
        assertEquals(NaturalLanguageRiskGuard.Risk.FORCE_PUSH, guard.classify("force push this branch"))
        assertNull(guard.classify("build a calculator with tests"))
    }

    @Test fun identifies_secret_paid_delete_and_swap_requests() {
        assertEquals(NaturalLanguageRiskGuard.Risk.SECRET_ACCESS, guard.classify("show the API key"))
        assertEquals(NaturalLanguageRiskGuard.Risk.PAID_UNLOCK, guard.classify("enable paid provider"))
        assertEquals(NaturalLanguageRiskGuard.Risk.MASS_DELETE, guard.classify("delete everything"))
        assertEquals(NaturalLanguageRiskGuard.Risk.JAR_SWAP, guard.classify("promote jar"))
    }

    @Test fun command_catalog_assigns_all_three_policy_levels() {
        assertEquals(CommandRisk.RISKY, CommandRiskCatalog.forCommand("/paid unlock"))
        assertEquals(CommandRisk.MODERATE, CommandRiskCatalog.forCommand("/factory run"))
        assertEquals(CommandRisk.AUTOMATIC, CommandRiskCatalog.forCommand("/providers"))
    }
}
