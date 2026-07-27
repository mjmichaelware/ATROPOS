package atropos.core.endpoint

class StaticOperationRegistry : OperationRegistry {
    private val endpoints = listOf(
        OperationEndpoint("provider.groq.chat", EndpointKind.PROVIDER_CHAT, "Groq chat completions", configured = true),
        OperationEndpoint("provider.openai.chat", EndpointKind.PROVIDER_CHAT, "OpenAI chat completions", configured = true),
        OperationEndpoint("provider.anthropic.messages", EndpointKind.PROVIDER_MESSAGES, "Anthropic messages", configured = true),
        OperationEndpoint("provider.xai.chat", EndpointKind.PROVIDER_CHAT, "xAI chat completions", configured = true),
        OperationEndpoint("provider.ollama.generate", EndpointKind.PROVIDER_GENERATE, "Ollama generate"),
        OperationEndpoint("provider.ollama.tags", EndpointKind.PROVIDER_TAGS, "Ollama model list"),
        OperationEndpoint("cli.help", EndpointKind.CLI_COMMAND, "Show help", configured = true, available = true),
        OperationEndpoint("cli.status", EndpointKind.CLI_COMMAND, "Show status matrix", configured = true, available = true),
        OperationEndpoint("cli.providers", EndpointKind.CLI_COMMAND, "List providers", configured = true, available = true),
        OperationEndpoint("cli.route", EndpointKind.CLI_COMMAND, "Route decision", configured = true, available = true),
        OperationEndpoint("cli.use", EndpointKind.CLI_COMMAND, "Switch provider", configured = true, available = true),
        OperationEndpoint("cli.verify", EndpointKind.CLI_COMMAND, "Verify scope", configured = true, available = true),
        OperationEndpoint("cli.swarm_unbound", EndpointKind.CLI_COMMAND, "Swarm command is declared but unbound", configured = true),
        OperationEndpoint("cli.exit", EndpointKind.CLI_COMMAND, "Exit application", configured = true, available = true),
        OperationEndpoint("tool.kotlinc.verify", EndpointKind.TOOL_VERIFY, "Kotlin compiler check", configured = true, available = true),
        OperationEndpoint("tool.git.status", EndpointKind.TOOL_GIT, "Git status", configured = true, available = true),
        OperationEndpoint("storage.local.cas", EndpointKind.STORAGE_LOCAL, "Content-addressable storage", configured = true),
        OperationEndpoint("storage.local.config", EndpointKind.STORAGE_LOCAL, "Local configuration", configured = true, available = true)
    )

    override fun getAll(): List<OperationEndpoint> = endpoints

    override fun getById(id: String): OperationEndpoint? =
        endpoints.find { it.id == id }

    override fun getByKind(kind: EndpointKind): List<OperationEndpoint> =
        endpoints.filter { it.kind == kind }
}
