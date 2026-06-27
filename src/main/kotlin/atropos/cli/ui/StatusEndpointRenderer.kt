package atropos.cli.ui

import atropos.core.endpoint.EndpointKind
import atropos.core.endpoint.OperationRegistry

class StatusEndpointRenderer(
    private val registry: OperationRegistry
) {
    fun render(): String {
        val out = mutableListOf<String>()
        out += "endpoints: ${registry.getAll().size}"

        for (kind in EndpointKind.entries) {
            val endpoints = registry.getByKind(kind)
            if (endpoints.isEmpty()) continue

            out += kind.name.lowercase()
            endpoints.forEach { endpoint ->
                val state = when {
                    endpoint.configured && endpoint.available -> "ready"
                    endpoint.configured -> "declared"
                    else -> "unconfigured"
                }
                out += "  $state  ${endpoint.id}  ${endpoint.description}"
            }
        }

        return out.joinToString("\n")
    }
}
