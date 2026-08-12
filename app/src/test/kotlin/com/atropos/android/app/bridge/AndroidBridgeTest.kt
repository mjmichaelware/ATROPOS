/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.bridge

import kotlin.test.Test
import kotlin.test.assertIs

class AndroidBridgeTest {
    @Test
    fun AndroidBridge_converts_invalid_transport_into_unreachable_result() {
        assertIs<BridgeResult.Unreachable>(AndroidBridge.get("not a URL"))
    }
}
