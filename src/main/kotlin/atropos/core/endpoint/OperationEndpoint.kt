package atropos.core.endpoint

data class EndpointManifest(
    val owner: String,
    val input: String,
    val output: String,
    val errors: List<String>,
    val auth: String,
    val sideEffects: List<String>,
    val timeoutMs: Long,
    val retryPolicy: String,
    val testIds: List<String>
)

data class OperationEndpoint(
    val id: String,
    val kind: EndpointKind,
    val description: String,
    val configured: Boolean = false,
    val available: Boolean = false,
    val manifest: EndpointManifest = EndpointManifest(
        owner = "unassigned",
        input = "typed operation request",
        output = "typed operation result",
        errors = listOf("typed failure"),
        auth = "policy-bound",
        sideEffects = emptyList(),
        timeoutMs = 30_000,
        retryPolicy = "bounded-none",
        testIds = emptyList()
    )
)
