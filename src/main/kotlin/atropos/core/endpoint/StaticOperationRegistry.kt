package atropos.core.endpoint

class StaticOperationRegistry : OperationRegistry {
    private val endpoints = listOf(
        endpoint("cli.help", EndpointKind.CLI_COMMAND, "Show help", configured = true, available = true),
        endpoint("cli.status", EndpointKind.CLI_COMMAND, "Show status matrix", configured = true, available = true),
        endpoint("cli.providers", EndpointKind.CLI_COMMAND, "List providers", configured = true, available = true),
        endpoint("cli.route", EndpointKind.CLI_COMMAND, "Route decision", configured = true, available = true),
        endpoint("cli.use", EndpointKind.CLI_COMMAND, "Switch provider", configured = true, available = true),
        endpoint("cli.verify", EndpointKind.CLI_COMMAND, "Verify scope", configured = true, available = true),
        endpoint(
            "cli.agent_dag_supervise",
            EndpointKind.CLI_COMMAND,
            "Supervise a planning DAG",
            configured = true,
            available = true,
            sideEffects = listOf("read-dag-state", "write-dag-state", "write-director-observation")
        ),
        endpoint(
            "cli.agent_worker_propose",
            EndpointKind.CLI_COMMAND,
            "Submit a bounded worker code proposal",
            configured = true,
            available = true,
            sideEffects = listOf("write-worker-proposal", "write-patch-evidence")
        ),
        endpoint("cli.swarm_unbound", EndpointKind.CLI_COMMAND, "Swarm command is declared but unbound", configured = true),
        endpoint("cli.exit", EndpointKind.CLI_COMMAND, "Exit application", configured = true, available = true),
        endpoint("tool.kotlinc.verify", EndpointKind.TOOL_VERIFY, "Kotlin compiler check", configured = true, available = true),
        endpoint("tool.git.status", EndpointKind.TOOL_GIT, "Git status", configured = true, available = true),
        endpoint("storage.local.cas", EndpointKind.STORAGE_LOCAL, "Content-addressable storage", configured = true),
        endpoint("storage.local.config", EndpointKind.STORAGE_LOCAL, "Local configuration", configured = true, available = true)
    )

    init {
        validateManifestSet(endpoints)
    }

    private fun endpoint(
        id: String,
        kind: EndpointKind,
        description: String,
        configured: Boolean = false,
        available: Boolean = false,
        sideEffects: List<String>? = null
    ): OperationEndpoint = OperationEndpoint(
        id = id,
        kind = kind,
        description = description,
        configured = configured,
        available = available,
        manifest = EndpointManifest(
            owner = ownerFor(id, kind),
            input = "typed ${kind.name.lowercase()} request",
            output = "typed ${kind.name.lowercase()} result",
            errors = listOf("authorization", "timeout", "malformed", "unavailable"),
            auth = "policy-bound",
            sideEffects = sideEffects ?: when (kind) {
                EndpointKind.TOOL_GIT -> listOf("read-git-state")
                EndpointKind.STORAGE_LOCAL -> listOf("read-local-state", "write-local-state")
                else -> listOf("none")
            },
            timeoutMs = 30_000,
            retryPolicy = "bounded-none",
            testIds = listOf("OperationEndpointManifestTest.every_registered_operation_exposes_a_complete_manifest")
        )
    )

    private fun ownerFor(id: String, kind: EndpointKind): String = when {
        id.startsWith("provider.") -> "ProviderAdapterRegistry"
        id == "cli.agent_dag_supervise" -> "AgentDagCommandHandler"
        id == "cli.agent_worker_propose" -> "AgentWorkerCommandHandler"
        id.startsWith("cli.") -> "CommandRouter"
        id == "tool.kotlinc.verify" -> "GovernedCompileGate"
        id == "tool.git.status" -> "ShellCommandRunner"
        id == "storage.local.cas" -> "CloudLakehouseSyncEngine"
        id == "storage.local.config" -> "ConfigurationManager"
        else -> kind.name
    }

    override fun getAll(): List<OperationEndpoint> = endpoints

    override fun getById(id: String): OperationEndpoint? =
        endpoints.find { it.id == id }

    override fun getByKind(kind: EndpointKind): List<OperationEndpoint> =
        endpoints.filter { it.kind == kind }

    private fun validateManifestSet(registered: List<OperationEndpoint>) {
        require(registered.map { it.id }.distinct().size == registered.size) {
            "operation registry contains duplicate endpoint ids"
        }
        registered.forEach { operation ->
            operation.requireCompleteManifest()
        }
    }
}
