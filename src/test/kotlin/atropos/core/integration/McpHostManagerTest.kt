package atropos.core.integration

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class McpHostManagerTest {
    @Test
    fun community_servers_are_disabled_and_tool_results_get_evidence() {
        val root = Files.createTempDirectory("mcp-host")
        Files.writeString(root.resolve("mcp.json"), """
            {"servers":[
              {"name":"community","transport":"stdio","command":"tool","enabled":true,"community":true},
              {"name":"local","transport":"stdio","command":"tool","enabled":true,"community":false}
            ]}
        """.trimIndent())
        val manager = McpHostManager(root, probe = { McpHealth.HEALTHY })
        val statuses = manager.statuses().associateBy { it.server.name }
        assertEquals(McpHealth.UNTESTED, statuses.getValue("community").health)
        assertEquals(McpHealth.HEALTHY, statuses.getValue("local").health)
        assertTrue(Files.readString(root.resolve(".atropos/mcp/health.tsv")).contains("local\tHEALTHY"))
        assertEquals(1, manager.boundedTools(listOf(McpToolDescriptor("a"), McpToolDescriptor("b")), McpToolBudget(1)).size)
        assertEquals("short", manager.boundedTools(listOf(McpToolDescriptor("a", "long-description")), McpToolBudget(maxDescriptionChars = 5)).single().description)
        val evidence = manager.recordToolResult("local", "inspect", "result")
        assertNotNull(evidence.sha256)
        assertTrue(Files.isRegularFile(evidence.path))

        val redacted = manager.recordToolResult("local", "inspect", "api_key=secret-value")
        assertTrue(!Files.readString(redacted.path).contains("secret-value"))
    }

    @Test
    fun default_stdio_probe_uses_configured_root_and_requires_both_handshake_ids() {
        val root = Files.createTempDirectory("mcp-probe")
        val script = root.resolve("mcp-probe.sh")
        Files.writeString(script, "#!/bin/sh\nprintf '%s\\n' '{\"id\":1}' '{\"id\":2}'\n")
        script.toFile().setExecutable(true)
        Files.writeString(root.resolve("mcp.json"), """
            {"servers":[
              {"name":"probe","transport":"stdio","command":"./mcp-probe.sh","enabled":true,"community":false}
            ]}
        """.trimIndent())
        val status = McpHostManager(root, localOnly = false).statuses().single()
        assertEquals(McpHealth.HEALTHY, status.health)
    }

    @Test
    fun local_stdio_tool_call_is_bounded_and_persisted_as_evidence() {
        val root = Files.createTempDirectory("mcp-call")
        val script = root.resolve("mcp-call.sh")
        Files.writeString(script, """
            #!/bin/sh
            while IFS= read -r line; do
              case "$line" in
                *'"id":1'*) printf '%s\n' '{"id":1}' ;;
                *'"id":2'*) printf '%s\n' '{"id":2,"result":{"tools":[{"name":"inspect"}]}}' ;;
                *'"id":3'*) printf '%s\n' '{"id":3,"result":{"content":[{"text":"ok"}]}}' ;;
              esac
            done
        """.trimIndent())
        script.toFile().setExecutable(true)
        Files.writeString(root.resolve("mcp.json"), """
            {"servers":[{"name":"local","transport":"stdio","command":"./mcp-call.sh","enabled":true,"community":false}]}
        """.trimIndent())
        val result = McpHostManager(root, localOnly = true).callTool("local", "inspect")
        assertTrue(result.response.contains("\"id\":3"))
        assertNotNull(result.evidence.sha256)
        assertTrue(Files.isRegularFile(result.evidence.path))
    }

    @Test
    fun markitdown_operation_is_admitted_by_the_same_bounded_mcp_owner() {
        val root = Files.createTempDirectory("mcp-markitdown-gate")
        val script = root.resolve("mcp-call.sh")
        Files.writeString(script, """
            #!/bin/sh
            while IFS= read -r line; do
              case "$line" in
                *'\"id\":1'*) printf '%s\n' '{"id":1}' ;;
                *'\"id\":2'*) printf '%s\n' '{"id":2,"result":{"tools":[{"name":"convert_to_markdown"}]}}' ;;
                *'\"id\":3'*) printf '%s\n' '{"id":3,"result":{"content":[{"text":"# ok"}]}}' ;;
              esac
            done
        """.trimIndent())
        script.toFile().setExecutable(true)
        Files.writeString(root.resolve("mcp.json"),
            """{"servers":[{"name":"markitdown","transport":"stdio","command":"./mcp-call.sh","enabled":true,"community":false}]}""")

        val result = McpHostManager(root).callTool(
            serverName = "markitdown",
            toolName = "convert_to_markdown",
            operation = "convert_to_markdown"
        )
        assertTrue(result.response.contains("\"id\":3"))
    }

    @Test
    fun direct_tool_call_refuses_unexposed_operation_before_starting_process() {
        val root = Files.createTempDirectory("mcp-call-gate")
        val marker = root.resolve("started")
        val script = root.resolve("mcp-call.sh")
        Files.writeString(script, "#!/bin/sh\ntouch '${marker.fileName}'\n")
        script.toFile().setExecutable(true)
        Files.writeString(root.resolve("mcp.json"), """
            {"servers":[{"name":"local","transport":"stdio","command":"./mcp-call.sh","enabled":true,"community":false}]}
        """.trimIndent())
        val failure = runCatching { McpHostManager(root).callTool("local", "write") }.exceptionOrNull()
        assertTrue(failure?.message?.contains("territory bridge") == true)
        assertTrue(!Files.exists(marker))
    }

    @Test
    fun local_only_persists_remote_refusal_without_probing() {
        val root = Files.createTempDirectory("mcp-remote")
        Files.writeString(root.resolve("mcp.json"),
            """{"servers":[{"name":"remote","transport":"http","command":"should-not-run","enabled":true,"community":false}]}""")
        val status = McpHostManager(root, localOnly = true, probe = { error("remote probe must not run") }).statuses().single()
        assertEquals(McpHealth.UNTESTED, status.health)
        assertTrue(status.reason.contains("localOnly"))
        assertTrue(Files.readString(root.resolve(".atropos/mcp/health.tsv")).contains("remote\tUNTESTED"))
    }

    @Test
    fun search_is_configured_only_and_hides_remote_and_unallowlisted_community_entries() {
        val root = Files.createTempDirectory("mcp-search")
        Files.writeString(root.resolve("mcp.json"), """
            {"servers":[
              {"name":"local-files","transport":"stdio","command":"local","enabled":false,"community":false},
              {"name":"remote-files","transport":"http","command":"remote","enabled":true,"community":false},
              {"name":"community-files","transport":"stdio","command":"community","enabled":true,"community":true}
            ]}
        """.trimIndent())

        val results = McpHostManager(root, localOnly = true).search("files")

        assertEquals(listOf("local-files"), results.map { it.name })
        assertTrue(results.single().enabled.not())
    }

    @Test
    fun allowlisted_http_server_uses_the_same_evidence_and_call_gate() {
        val root = Files.createTempDirectory("mcp-http-call")
        Files.writeString(root.resolve("mcp.json"), """
            {"servers":[{"name":"remote","transport":"http","url":"https://mcp.example.test/rpc","enabled":true,"community":false}]}
        """.trimIndent())
        val requests = mutableListOf<String>()
        val result = McpHostManager(
            root,
            localOnly = false,
            probe = { McpHealth.HEALTHY },
            remoteRequest = { _, body -> requests += body; "{\"jsonrpc\":\"2.0\",\"id\":3,\"result\":{\"content\":[{\"text\":\"ok\"}]}}" }
        ).callTool("remote", "inspect")

        assertEquals(3, requests.size)
        assertTrue(requests.last().contains("tools/call"))
        assertTrue(requests.last().contains("\"name\":\"inspect\""))
        assertTrue(requests.last().contains("\"arguments\":{}"))
        assertNotNull(result.evidence.sha256)
    }

    @Test
    fun default_http_probe_runs_initialize_and_tools_list() {
        val root = Files.createTempDirectory("mcp-http-probe")
        Files.writeString(root.resolve("mcp.json"), """
            {"servers":[{"name":"remote","transport":"streamable-http","url":"https://mcp.example.test/rpc","enabled":true,"community":false}]}
        """.trimIndent())
        val requests = mutableListOf<String>()
        val status = McpHostManager(
            root,
            localOnly = false,
            remoteRequest = { _, body ->
                requests += body
                if (body.contains("\"id\":1")) "{\"id\":1}" else "{\"id\":2,\"result\":{\"tools\":[{\"name\":\"inspect\"}]}}"
            }
        ).statuses().single()

        assertEquals(McpHealth.HEALTHY, status.health)
        assertEquals(2, requests.size)
    }
}
