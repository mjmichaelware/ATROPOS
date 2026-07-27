package atropos.core.endpoint

enum class EndpointKind {
    PROVIDER_CHAT,
    PROVIDER_MESSAGES,
    PROVIDER_GENERATE,
    PROVIDER_TAGS,
    CLI_COMMAND,
    TOOL_VERIFY,
    TOOL_GIT,
    STORAGE_LOCAL,
    LAKEHOUSE
}
