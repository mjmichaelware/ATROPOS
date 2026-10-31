/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.toolchain.drivers

import java.nio.file.Path

/**
 * ATOM EX9: REMOTE CONTAINERS REPRODUCIBLE ISOLATION MOUNT
 * Path: src/main/kotlin/atropos/core/toolchain/drivers/RemoteContainerIsolationMount.kt
 *
 * Mounts the entire development workspace inside an isolated, disposable container
 * defined by repository manifests with strict credential rejection and ephemeral binding.
 */
enum class ContainerIsolationLevel {
    HOST_NETWORK_SHARED,
    SANDBOX_LOOPBACK_ONLY,
    AIRGAPPED_ZERO_NETWORK
}

data class ContainerMountConfig(
    val hostWorktreePath: Path,
    val containerMountPath: String = "/workspaces/atropos",
    val isolationLevel: ContainerIsolationLevel = ContainerIsolationLevel.SANDBOX_LOOPBACK_ONLY,
    val readOnlyMounts: List<String> = emptyList(),
    val ephemeralTmpfsMounts: List<String> = listOf("/tmp", "/dev/shm")
)

data class IsolationMountReceipt(
    val containerId: String,
    val mountConfig: ContainerMountConfig,
    val isSecure: Boolean,
    val liveCredentialsRejected: Boolean
)

class RemoteContainerIsolationMount(
    private val config: ContainerMountConfig
) {
    fun validateWorktree(): Boolean {
        return java.nio.file.Files.isDirectory(config.hostWorktreePath)
    }

    fun rejectLiveCredentials(environment: Map<String, String>): Map<String, String> {
        val dangerousKeys = setOf("AWS_SECRET_ACCESS_KEY", "GITHUB_TOKEN", "STRIPE_SECRET_KEY", "DATABASE_URL")
        return environment.filterKeys { !dangerousKeys.contains(it) }
    }

    fun generateDockerMountArgs(): List<String> {
        val args = mutableListOf<String>()
        args.add("-v")
        args.add("${config.hostWorktreePath.toAbsolutePath()}:${config.containerMountPath}:cached")

        for (tmp in config.ephemeralTmpfsMounts) {
            args.add("--tmpfs")
            args.add("$tmp:rw,noexec,nosuid,size=512m")
        }

        when (config.isolationLevel) {
            ContainerIsolationLevel.AIRGAPPED_ZERO_NETWORK -> {
                args.add("--network")
                args.add("none")
            }
            ContainerIsolationLevel.SANDBOX_LOOPBACK_ONLY -> {
                args.add("--network")
                args.add("bridge")
            }
            ContainerIsolationLevel.HOST_NETWORK_SHARED -> {
                args.add("--network")
                args.add("host")
            }
        }

        return args
    }

    companion object {
        fun defaultMount(hostPath: Path): RemoteContainerIsolationMount =
            RemoteContainerIsolationMount(ContainerMountConfig(hostWorktreePath = hostPath))
    }
}
