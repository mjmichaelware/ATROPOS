package atropos.core.integration

import java.nio.file.Files
import atropos.core.policy.AgencyDecision
import atropos.core.policy.AgencyDisposition
import atropos.core.policy.ExecutionPolicyDecision
import atropos.core.policy.PolicyActionClass
import atropos.core.policy.PolicyDecisionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class McpHostManagerTest {
    @Test
    fun config_parser_handles_nested_braces_escaped_strings_and_args() {
        val root = Files.createTempDirectory("mcp-config")
        Files.writeString(root.resolve("mcp.json"), """
            {"servers":[
              {"name":"local","transport":"stdio","command":"tool-{local}",
               "args":["--label","a\"b", "{nested}"],"enabled":true,"community":false}
            ],"metadata":{"description":"braces { stay nested }"}}
        """.trimIndent())

        val server = McpHostManager(root).load().single()
        assertEquals("local", server.name)
        assertEquals("tool-{local}", server.command)
        assertEquals(listOf("--label", "a\"b", "{nested}"), server.args)
        assertTrue(server.enabled)
        assertFalse(server.community)
    }

    @Test
    fun config_parser_does_not_match_member_names_inside_string_values() {
        val root = Files.createTempDirectory("mcp-config-string")
        Files.writeString(root.resolve("mcp.json"), """
            {"description":"text containing \"servers\":[{\"name\":\"wrong\"}]",
             "servers":[{"name":"actual","enabled":true,"community":false}]}
        """.trimIndent())

        assertEquals("actual", McpHostManager(root).load().single().name)
    }

    @Test
    fun config_parser_rejects_truncated_or_trailing_root_documents() {
        val root = Files.createTempDirectory("mcp-config-invalid")
        val config = root.resolve("mcp.json")
        Files.writeString(config, "{\"servers\":[{\"name\":\"local\"}]")
        assertFailsWith<IllegalArgumentException> { McpHostManager(root).load() }
        Files.writeString(config, "{\"servers\":[]} trailing")
        assertFailsWith<IllegalArgumentException> { McpHostManager(root).load() }
        Files.writeString(config, "{\"servers\":[],}")
        assertFailsWith<IllegalArgumentException> { McpHostManager(root).load() }
        Files.writeString(config, "{\"servers\" []}")
        assertFailsWith<IllegalArgumentException> { McpHostManager(root).load() }
    }

    @Test
    fun config_parser_rejects_silent_server_drops_and_duplicate_names() {
        val root = Files.createTempDirectory("mcp-config-server-validation")
        val config = root.resolve("mcp.json")
        Files.writeString(config, """{"servers":[{"transport":"stdio"}]}""")
        assertFailsWith<IllegalArgumentException> { McpHostManager(root).load() }
        Files.writeString(config, """{"servers":[{"name":"same"},{"name":"same"}]}""")
        assertFailsWith<IllegalArgumentException> { McpHostManager(root).load() }
    }

    @Test
    fun config_parser_rejects_trailing_commas_invalid_args_and_non_boolean_flags() {
        val root = Files.createTempDirectory("mcp-config-fields")
        val config = root.resolve("mcp.json")
        Files.writeString(config, """{"servers":[{"name":"local",}]}""")
        assertFailsWith<IllegalArgumentException> { McpHostManager(root).load() }
        Files.writeString(config, """{"servers":[{"name":"local","args":["one",]}]}""")
        assertFailsWith<IllegalArgumentException> { McpHostManager(root).load() }
        Files.writeString(config, """{"servers":[{"name":"local","enabled":"yes"}]}""")
        assertFailsWith<IllegalArgumentException> { McpHostManager(root).load() }
    }

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
        assertEquals("max_tools=32 max_description_chars=4000", manager.budgetSummary())
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
        val calls = root.resolve("calls")
        val script = root.resolve("mcp-call.sh")
        Files.writeString(script, """
            #!/bin/sh
            while IFS= read -r line; do
              case "$line" in
                *'"id":1'*) printf '%s\n' '{"id":1}' ;;
                *'"id":2'*) printf '%s\n' '{"id":2,"result":{"tools":[{"name":"inspect"}]}}' ;;
                *'"id":3'*) printf '%s\n' x >> '${calls.fileName}'; printf '%s\n' '{"id":3,"result":{"content":[{"text":"ok"}]}}' ;;
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
        assertEquals(1, Files.readAllLines(calls).size)
    }

    @Test
    fun stdio_tool_call_reaps_server_process_after_response() {
        val root = Files.createTempDirectory("mcp-reap")
        val pidFile = root.resolve("pid")
        val script = root.resolve("mcp-reap.sh")
        Files.writeString(script, """
            #!/bin/sh
            printf '%s' "$$" > '${pidFile.fileName}'
            while IFS= read -r line; do
              case "$line" in
                *'\"id\":1'*) printf '%s\n' '{"id":1}' ;;
                *'\"id\":2'*) printf '%s\n' '{"id":2,"result":{"tools":[{"name":"inspect"}]}}' ;;
                *'\"id\":3'*) printf '%s\n' '{"id":3,"result":{"content":[{"text":"ok"}]}}' ;;
              esac
            done
        """.trimIndent())
        script.toFile().setExecutable(true)
        Files.writeString(root.resolve("mcp.json"),
            """{"servers":[{"name":"local","transport":"stdio","command":"./mcp-reap.sh","enabled":true,"community":false}]}""")

        McpHostManager(root).callTool("local", "inspect")

        val pid = Files.readString(pidFile).trim().toLong()
        assertFalse(ProcessHandle.of(pid).map { it.isAlive }.orElse(false), "MCP child process survived call")
    }

    @Test
    fun tool_budget_refuses_before_stdio_call_is_sent() {
        val root = Files.createTempDirectory("mcp-budget")
        val marker = root.resolve("called")
        val script = root.resolve("mcp-budget.sh")
        Files.writeString(script, """
            #!/bin/sh
            while IFS= read -r line; do
              case "$line" in
                *'\"id\":1'*) printf '%s\n' '{"id":1}' ;;
                *'\"id\":2'*) printf '%s\n' '{"id":2,"result":{"tools":[{"name":"first"},{"name":"second"}]}}' ;;
                *'\"id\":3'*) touch '${marker.fileName}'; printf '%s\n' '{"id":3}' ;;
              esac
            done
        """.trimIndent())
        script.toFile().setExecutable(true)
        Files.writeString(root.resolve("mcp.json"),
            """{"servers":[{"name":"local","transport":"stdio","command":"./mcp-budget.sh","enabled":true,"community":false}]}""")

        val failure = runCatching {
            McpHostManager(root).callTool(
                serverName = "local",
                toolName = "second",
                operation = "inspect",
                toolBudget = McpToolBudget(maxTools = 1)
            )
        }.exceptionOrNull()
        assertTrue(failure?.message?.contains("not advertised within the configured tool budget") == true)
        assertTrue(!Files.exists(marker))
    }

    @Test
    fun memory_mcp_cannot_write_authority_paths_even_when_outer_gate_allows() {
        val root = Files.createTempDirectory("mcp-memory-authority")
        val marker = root.resolve("started")
        val script = root.resolve("memory.sh")
        Files.writeString(script, "#!/bin/sh\ntouch '${marker.fileName}'\n")
        script.toFile().setExecutable(true)
        Files.writeString(root.resolve("mcp.json"),
            """{"servers":[{"name":"memory-local","transport":"stdio","command":"./memory.sh","enabled":true,"community":false}]}""")
        val manager = McpHostManager(
            root,
            territoryBridge = McpTerritoryBridge(
                setOf("write"),
                gate = { proposal ->
                    AgencyDecision(
                        proposal = proposal,
                        policyDecision = ExecutionPolicyDecision(
                            id = "test",
                            decision = PolicyDecisionType.ALLOW,
                            actionClass = PolicyActionClass.FILE_MUTATION,
                            destructive = false,
                            reason = "test allow"
                        ),
                        disposition = AgencyDisposition.ALLOWED,
                        reason = "test allow"
                    )
                }
            )
        )
        val failure = runCatching {
            manager.callTool(
                serverName = "memory-local",
                toolName = "write",
                operation = "write",
                territoryPaths = listOf(".atropos/governance/ledger.tsv")
            )
        }.exceptionOrNull()
        assertTrue(failure?.message?.contains("memory MCP cannot write") == true)
        assertTrue(!Files.exists(marker))
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
    fun unhealthy_server_is_refused_before_tool_process_starts() {
        val root = Files.createTempDirectory("mcp-unhealthy-call")
        val marker = root.resolve("started")
        val script = root.resolve("mcp-call.sh")
        Files.writeString(script, "#!/bin/sh\ntouch '${marker.fileName}'\n")
        script.toFile().setExecutable(true)
        Files.writeString(root.resolve("mcp.json"), """
            {"servers":[{"name":"local","transport":"stdio","command":"./mcp-call.sh","enabled":true,"community":false}]}
        """.trimIndent())

        val failure = runCatching {
            McpHostManager(root, probe = { McpHealth.UNHEALTHY }).callTool("local", "inspect")
        }.exceptionOrNull()

        assertTrue(failure?.message?.contains("not healthy") == true)
        assertTrue(!Files.exists(marker))
        assertTrue(Files.readString(root.resolve(".atropos/mcp/health.tsv")).contains("local\tUNHEALTHY"))
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
    fun search_cannot_bypass_the_existing_mcp_territory_gate() {
        val root = Files.createTempDirectory("mcp-search-gate")
        Files.writeString(root.resolve("mcp.json"), """
            {"servers":[{"name":"local-files","transport":"stdio","command":"local","enabled":false,"community":false}]}
        """.trimIndent())
        val manager = McpHostManager(
            root,
            territoryBridge = McpTerritoryBridge(setOf("inspect")) { proposal ->
                val decision = ExecutionPolicyDecision(
                    id = "search-denied",
                    decision = PolicyDecisionType.DENY,
                    actionClass = PolicyActionClass.FILE_MUTATION,
                    destructive = false,
                    reason = "search denied in fixture"
                )
                AgencyDecision(proposal, decision, AgencyDisposition.POLICY_BLOCKED, decision.reason)
            }
        )

        val failure = assertFailsWith<IllegalArgumentException> { manager.search("files") }
        assertTrue(failure.message.orEmpty().contains("search refused by policy"))
    }

    @Test
    fun unsupported_transport_is_reported_and_never_spawned() {
        val root = Files.createTempDirectory("mcp-unsupported-transport")
        Files.writeString(root.resolve("mcp.json"),
            """{"servers":[{"name":"bad","transport":"telnet","command":"would-run","enabled":true,"community":false}]}""")
        val status = McpHostManager(root).statuses().single()
        assertEquals(McpHealth.UNTESTED, status.health)
        assertTrue(status.reason.contains("unsupported MCP transport"))
        val failure = runCatching { McpHostManager(root).callTool("bad", "inspect") }.exceptionOrNull()
        assertTrue(failure?.message?.contains("unsupported MCP transport") == true)
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
            remoteRequest = { _, body ->
                requests += body
                when {
                    body.contains("\"method\":\"initialize\"") -> "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}"
                    body.contains("\"method\":\"tools/list\"") -> "{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{\"tools\":[{\"name\":\"inspect\"}]}}"
                    else -> "{\"jsonrpc\":\"2.0\",\"id\":3,\"result\":{\"content\":[{\"text\":\"ok\"}]}}"
                }
            }
        ).callTool("remote", "inspect")

        assertEquals(4, requests.size)
        assertTrue(requests[0].contains("\"method\":\"initialize\""))
        assertTrue(requests[1].contains("notifications/initialized"))
        assertTrue(requests[2].contains("\"method\":\"tools/list\""))
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
        assertEquals(3, requests.size)
        assertTrue(requests[1].contains("notifications/initialized"))
        assertTrue(requests[2].contains("tools/list"))
    }

    @Test
    fun sse_frames_are_normalized_before_the_existing_json_rpc_probe() {
        val root = Files.createTempDirectory("mcp-sse-probe")
        Files.writeString(root.resolve("mcp.json"),
            """{"servers":[{"name":"remote","transport":"sse","url":"https://mcp.example.test/events","enabled":true,"community":false}]}""")
        val requests = mutableListOf<String>()
        val status = McpHostManager(
            root,
            localOnly = false,
            remoteRequest = { _, body ->
                requests += body
                if (body.contains("\"id\":1")) "event: message\ndata: {\"id\":1}\n\n"
                else "data: {\"id\":2,\"result\":{\"tools\":[{\"name\":\"inspect\"}]}}\n"
            }
        ).statuses().single()

        assertEquals(McpHealth.HEALTHY, status.health)
        assertEquals(3, requests.size)
        assertTrue(requests[1].contains("notifications/initialized"))
        assertTrue(requests[2].contains("tools/list"))
    }

    @Test
    fun remote_response_is_refused_before_tool_call_when_over_bound() {
        val root = Files.createTempDirectory("mcp-remote-bound")
        Files.writeString(root.resolve("mcp.json"),
            """{"servers":[{"name":"remote","transport":"http","url":"https://mcp.example.test/rpc","enabled":true,"community":false}]}""")
        val failure = assertFailsWith<IllegalArgumentException> {
            McpHostManager(
                root,
                localOnly = false,
                remoteRequest = { _, _ -> "x".repeat(128) }
            ).callTool("remote", "inspect", maxResponseBytes = 64)
        }
        assertTrue(failure.message.orEmpty().contains("bounded response size"))
    }

    @Test
    fun malformed_tool_arguments_are_refused_before_transport() {
        val root = Files.createTempDirectory("mcp-arguments")
        Files.writeString(root.resolve("mcp.json"),
            """{"servers":[{"name":"remote","transport":"http","url":"https://mcp.example.test/rpc","enabled":true,"community":false}]}""")
        var called = false
        val failure = assertFailsWith<IllegalArgumentException> {
            McpHostManager(
                root,
                localOnly = false,
                probe = { McpHealth.HEALTHY },
                remoteRequest = { _, _ -> called = true; "{}" }
            ).callTool("remote", "inspect", argumentsJson = "{}\n,\"injected\":true")
        }
        assertTrue(failure.message.orEmpty().contains("JSON object"))
        assertTrue(!called)
    }

    @Test
    fun mismatched_tool_argument_delimiters_are_refused() {
        assertFailsWith<IllegalArgumentException> {
            McpConfigParser.requireJsonObject("{\"value\":]}")
        }
    }
}
