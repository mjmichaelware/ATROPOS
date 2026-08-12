/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.bridge

import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidEngineBridgeTest {
    @Test
    fun AndroidEngineBridge_reads_sessions_through_the_existing_http_boundary() {
        val engine = AndroidEngineBridge(
            discovery = BridgeDiscovery(candidates = listOf(8787)) { url ->
                if (url.endsWith("/v1/health")) BridgeResult.Ok("{}") else BridgeResult.Unreachable("unexpected")
            },
            http = object : BridgeHttpApi {
                override fun get(url: String): BridgeResult =
                    if (url.endsWith("/v1/sessions")) {
                        BridgeResult.Ok("{\"sessions\":[{\"id\":\"s1\",\"title\":\"Notes\",\"updatedAt\":\"now\"}]}")
                    } else BridgeResult.Unreachable("unexpected")

                override fun post(url: String, body: String): BridgeResult =
                    BridgeResult.Unreachable("not used")
            }
        )

        assertEquals("s1", engine.sessions().single().id)
    }
}
