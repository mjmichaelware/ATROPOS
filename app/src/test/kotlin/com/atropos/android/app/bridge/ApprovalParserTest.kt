/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.bridge

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApprovalParserTest {

    @Test
    fun reads_the_fields_a_person_needs_to_decide() {
        val approvals = ApprovalParser.parse(
            """{"ok":true,"pending":[{"id":"apr-1","proposalId":"p-9","actor":"self-host",""" +
                """"operation":"apply patch","territory":["src/main/kotlin"],""" +
                """"reason":"mutates the engine","requestedAt":"2026-08-15T05:00:00Z","pending":true}]}"""
        )

        val approval = approvals.single()
        assertEquals("apr-1", approval.id)
        assertEquals("apply patch", approval.operation)
        assertEquals("self-host", approval.actor)
        assertEquals(listOf("src/main/kotlin"), approval.territory)
        assertEquals("mutates the engine", approval.reason)
    }

    @Test
    fun an_absent_pending_flag_is_read_as_pending() {
        // The endpoint only returns pending approvals, so a missing flag means
        // pending. Defaulting to false would silently drop a decision the
        // operator owes.
        val approvals = ApprovalParser.parse("""{"ok":true,"pending":[{"id":"apr-2"}]}""")
        assertTrue(approvals.single().pending)
    }

    @Test
    fun an_empty_territory_stays_empty_rather_than_becoming_every_path() {
        val approvals = ApprovalParser.parse(
            """{"ok":true,"pending":[{"id":"apr-3","territory":[]}]}"""
        )
        assertTrue(approvals.single().territory.isEmpty())
    }

    @Test
    fun an_unreadable_body_yields_nothing_rather_than_an_invented_approval() {
        // A client that invented an approval would ask a person to authorise
        // something the engine never proposed.
        assertTrue(ApprovalParser.parse("not json").isEmpty())
        assertTrue(ApprovalParser.parse("""{"ok":false}""").isEmpty())
        assertTrue(ApprovalParser.parse("""{"ok":true}""").isEmpty())
    }
}
