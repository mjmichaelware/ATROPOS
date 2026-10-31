/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.toolchain

import java.nio.file.Path

/**
 * ATOM EX7: REMOTE SSH PHYSICAL SERVER FILE AND TERMINAL ACCESS
 * Path: src/main/kotlin/atropos/core/toolchain/RemoteSshExecutionTunnel.kt
 *
 * Enables opening remote folders and executing shell commands on external
 * physical servers or virtual machines over secure SSH tunnels with environment secret scrubbing.
 */
data class SshConnectionConfig(
    val host: String,
    val port: Int = 22,
    val user: String = "root",
    val identityKeyPath: Path? = null,
    val remoteWorkspacePath: String = "/workspace"
)

class RemoteSshExecutionTunnel(
    private val config: SshConnectionConfig
) {
    fun buildSshCommand(remoteCommand: String): List<String> {
        val args = mutableListOf("ssh", "-p", config.port.toString())

        if (config.identityKeyPath != null) {
            args.add("-i")
            args.add(config.identityKeyPath.toAbsolutePath().toString())
        }

        // Multiplexing and control master settings for low-latency tunnels
        args.add("-o")
        args.add("ControlMaster=auto")
        args.add("-o")
        args.add("ControlPath=/tmp/atropos-ssh-%r@%h:%p")
        args.add("-o")
        args.add("ControlPersist=10m")
        args.add("-o")
        args.add("StrictHostKeyChecking=accept-new")

        val target = "${config.user}@${config.host}"
        args.add(target)

        val cdPrefix = "cd ${escapeShell(config.remoteWorkspacePath)} && "
        args.add(cdPrefix + remoteCommand)

        return args
    }

    fun stripSecretsFromEnvironment(env: Map<String, String>): Map<String, String> {
        val sensitivePatterns = listOf("TOKEN", "SECRET", "KEY", "PASSWORD", "AUTH", "CREDENTIAL")
        return env.filterKeys { key ->
            val upper = key.uppercase()
            sensitivePatterns.none { upper.contains(it) }
        }
    }

    private fun escapeShell(arg: String): String = "'" + arg.replace("'", "'\\''") + "'"

    companion object {
        fun defaultTunnel(host: String): RemoteSshExecutionTunnel =
            RemoteSshExecutionTunnel(SshConnectionConfig(host = host))
    }
}
