/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.integration

/** One read-only catalog for first-party integration ownership and transport. */
data class IntegrationDescriptor(
    val id: String,
    val transport: String,
    val enabledByDefault: Boolean,
    val capabilities: List<String>
)

object IntegrationRegistry {
    private val descriptors = listOf(
        IntegrationDescriptor("github", "https", true, listOf("issues", "pull_requests", "checks")),
        IntegrationDescriptor("mcp", "stdio_or_http", true, listOf("tools", "evidence", "allowlist")),
        IntegrationDescriptor("sentry", "https", true, listOf("issues", "stack_frames", "repair_proposals"))
    )

    fun all(): List<IntegrationDescriptor> = descriptors

    fun requireRegistered(id: String): IntegrationDescriptor = descriptors.firstOrNull { it.id == id }
        ?: error("integration is not registered: $id")
}
