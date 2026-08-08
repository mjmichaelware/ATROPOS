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
) {
    fun requireCompleteManifest(): OperationEndpoint {
        require(id.isNotBlank() && description.isNotBlank()) {
            "operation registry contains an unnamed endpoint"
        }
        require(manifest.owner.isNotBlank()) { "$id has no manifest owner" }
        require(manifest.input.isNotBlank()) { "$id has no manifest input" }
        require(manifest.output.isNotBlank()) { "$id has no manifest output" }
        require(manifest.errors.isNotEmpty() && manifest.errors.all(String::isNotBlank)) {
            "$id has incomplete manifest errors"
        }
        require(manifest.auth.isNotBlank()) { "$id has no manifest auth policy" }
        require(manifest.sideEffects.all(String::isNotBlank)) {
            "$id has an empty manifest side effect"
        }
        require(manifest.timeoutMs > 0L) { "$id has no positive manifest timeout" }
        require(manifest.retryPolicy.isNotBlank()) { "$id has no manifest retry policy" }
        require(manifest.testIds.isNotEmpty() && manifest.testIds.all(String::isNotBlank)) {
            "$id has no manifest test identity"
        }
        return this
    }
}
