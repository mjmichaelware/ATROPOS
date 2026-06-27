package atropos.core.endpoint

data class OperationEndpoint(
    val id: String,
    val kind: EndpointKind,
    val description: String,
    val configured: Boolean = false,
    val available: Boolean = false
)
