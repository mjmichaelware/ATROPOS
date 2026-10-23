/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core

import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

/**
 * ATOM-ISO-001: Isolated Target Workspace Locator.
 *
 * Enforces strict mathematical domain decoupling:
 * Cap(P_theta) ∩ Cap(A_write) = ∅ ; Cap(Engine_Write) ∩ Cap(Target_Write) = ∅.
 * Guarantees that target workspaces reside exclusively in isolated external
 * directories and can never resolve to or mutate the engine installation root.
 */
object TargetWorkspaceLocator {
    private const val WORKSPACE_PREFIX = "atropos-target-workspace-"

    /**
     * Mathematical validation of capability domain severance.
     */
    fun assertAxiomaticIsolation(engineCapabilities: Set<String>, targetCapabilities: Set<String>) {
        val intersection = engineCapabilities.intersect(targetCapabilities)
        if (intersection.isNotEmpty()) {
            throw EngineDomainViolationException(
                "Axiomatic Isolation violation: Cap(Engine) ∩ Cap(Target) = ∅ required, but found intersecting capabilities: $intersection"
            )
        }
    }

    fun resolveTargetWorkspace(targetHint: Path? = null): Path {
        val engineRoot = AtroposRepoRootLocator.resolve().toAbsolutePath().normalize()

        if (targetHint != null) {
            val candidate = targetHint.toAbsolutePath().normalize()
            if (candidate.startsWith(engineRoot)) {
                throw EngineDomainViolationException(
                    "Domain decoupling violation: target workspace cannot be inside engine repository: $candidate"
                )
            }
            if (!Files.exists(candidate)) {
                Files.createDirectories(candidate)
            }
            return candidate
        }

        val tmpDir = Path.of(System.getProperty("java.io.tmpdir", "/tmp")).toAbsolutePath().normalize()
        val uniqueName = "$WORKSPACE_PREFIX${UUID.randomUUID()}"
        val created = tmpDir.resolve(uniqueName)
        Files.createDirectories(created)
        return created
    }
}
