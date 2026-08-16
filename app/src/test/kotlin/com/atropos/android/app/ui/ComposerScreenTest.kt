/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.ui

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class ComposerScreenTest {
    @Test
    fun ComposerScreen_disables_empty_send_and_allows_natural_language() {
        assertNotNull(::ComposerScreen)
        assertFalse(canSendComposerInput("   "))
        assertTrue(canSendComposerInput("build a calculator"))
    }
}
