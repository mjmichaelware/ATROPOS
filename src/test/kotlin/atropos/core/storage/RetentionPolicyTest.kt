/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.storage

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `SUP.STOR.RETENTION-TIERS`: storage behaviour is declarative and every
 * transition is covered. The transition that matters most is the one that does
 * not happen — a referenced item stays `HOT` under any pressure.
 */
class RetentionPolicyTest {

    private val policy = RetentionPolicy()

    @Test
    fun `age walks an unreferenced class through the tiers`() {
        assertEquals(
            RetentionTier.WARM,
            policy.tierFor("worktrees", Duration.ofHours(1), referenced = false, pressure = 0.0)
        )
        assertEquals(
            RetentionTier.COLD,
            policy.tierFor("worktrees", Duration.ofDays(4), referenced = false, pressure = 0.0)
        )
        assertEquals(
            RetentionTier.DELETE,
            policy.tierFor("worktrees", Duration.ofDays(90), referenced = false, pressure = 0.0)
        )
    }

    @Test
    fun `a referenced item is HOT under emergency pressure`() {
        assertEquals(
            RetentionTier.HOT,
            policy.tierFor("worktrees", Duration.ofDays(365), referenced = true, pressure = 1.0)
        )
    }

    @Test
    fun `pressure accelerates the walk but never skips the reference check`() {
        val calm = policy.tierFor("evidence", Duration.ofDays(5), referenced = false, pressure = 0.0)
        val squeezed = policy.tierFor("evidence", Duration.ofDays(5), referenced = false, pressure = 0.99)

        assertEquals(RetentionTier.WARM, calm)
        assertEquals(RetentionTier.COLD, squeezed)
        assertEquals(
            RetentionTier.HOT,
            policy.tierFor("evidence", Duration.ofDays(5), referenced = true, pressure = 0.99)
        )
    }

    @Test
    fun `secrets and config are never reclaimed at any age`() {
        listOf("secrets", "config", "territory").forEach { name ->
            assertEquals(
                RetentionTier.HOT,
                policy.tierFor(name, Duration.ofDays(3650), referenced = false, pressure = 1.0),
                "$name must never become collectable"
            )
        }
    }

    @Test
    fun `an undeclared class gets the conservative rule`() {
        assertEquals(RetentionRule.CONSERVATIVE, policy.ruleFor("something-new"))
        assertEquals(
            RetentionTier.WARM,
            policy.tierFor("something-new", Duration.ofDays(10), referenced = false, pressure = 0.0)
        )
    }

    @Test
    fun `HOT never appears in the reclaim order`() {
        assertTrue(RetentionTier.HOT !in RetentionTier.RECLAIM_ORDER)
        assertEquals(
            listOf(RetentionTier.DELETE, RetentionTier.COLD, RetentionTier.WARM),
            RetentionTier.RECLAIM_ORDER
        )
    }

    @Test
    fun `a clock that went backwards yields zero age rather than a negative one`() {
        val future = Instant.now().plusSeconds(600)

        assertEquals(Duration.ZERO, ageOf(future))
    }
}
