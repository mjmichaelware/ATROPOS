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

    @Test
    fun AndroidEngineBridge_fetches_workspace_tree_successfully() {
        val engine = AndroidEngineBridge(
            discovery = BridgeDiscovery(candidates = listOf(8787)) { _ -> BridgeResult.Ok("{}") },
            http = object : BridgeHttpApi {
                override fun get(url: String): BridgeResult =
                    if (url.contains("/v1/workspace/tree")) {
                        BridgeResult.Ok("{\"ok\":true,\"root\":\".\",\"entries\":[\"src/Main.kt\",\"README.md\"]}")
                    } else BridgeResult.Unreachable("unexpected")

                override fun post(url: String, body: String): BridgeResult = BridgeResult.Unreachable("not used")
            }
        )

        val outcome = engine.fetchWorkspaceTree()
        assert(outcome is WorkspaceTreeOutcome.Success)
        val success = outcome as WorkspaceTreeOutcome.Success
        assertEquals(2, success.entries.size)
        assertEquals("src/Main.kt", success.entries[0])
    }

    @Test
    fun AndroidEngineBridge_reads_and_writes_workspace_file() {
        var writtenBody = ""
        val engine = AndroidEngineBridge(
            discovery = BridgeDiscovery(candidates = listOf(8787)) { _ -> BridgeResult.Ok("{}") },
            http = object : BridgeHttpApi {
                override fun get(url: String): BridgeResult =
                    if (url.contains("/v1/workspace/file")) {
                        BridgeResult.Ok("{\"ok\":true,\"path\":\"test.kt\",\"content\":\"println(1)\"}")
                    } else BridgeResult.Unreachable("unexpected")

                override fun post(url: String, body: String): BridgeResult {
                    writtenBody = body
                    return BridgeResult.Ok("{\"ok\":true}")
                }
            }
        )

        val read = engine.readWorkspaceFile("test.kt")
        assert(read is WorkspaceFileOutcome.Read)
        assertEquals("println(1)", (read as WorkspaceFileOutcome.Read).content)

        val write = engine.writeWorkspaceFile("test.kt", "println(2)")
        assert(write is WorkspaceFileOutcome.Written)
        assert(writtenBody.contains("println(2)"))
    }
}
