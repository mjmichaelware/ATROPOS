/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.ui

import org.junit.Assert.assertTrue
import org.junit.Test

class MobileComponentsTest {
    @Test
    fun `approval card callbacks propagate correctly`() {
        var approved = false
        var rejected = false

        val onApprove: (String) -> Unit = { approved = true }
        val onReject: (String) -> Unit = { rejected = true }
        
        onApprove("id-1")
        assertTrue(approved)
        
        onReject("id-1")
        assertTrue(rejected)
    }
}
