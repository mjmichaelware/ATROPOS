/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.toolchain

import java.nio.file.Files
import java.nio.file.Path

class ChrootUnshareJail {
    fun wrapCommand(worktreePath: Path, command: List<String>): List<String> {
        val sanitizedCommand = command.map { stripSecrets(it) }
        if (!isJailSupported()) {
            return sanitizedCommand
        }

        val chrootPath = worktreePath.toAbsolutePath().normalize()
        return listOf(
            "unshare",
            "--mount",
            "--uts",
            "--ipc",
            "--net",
            "--pid",
            "--fork",
            "--mount-proc",
            "chroot",
            chrootPath.toString()
        ) + sanitizedCommand
    }

    fun stripSecrets(text: String): String {
        return text.replace(Regex("""(?i)(bearer\s+|token\s+|key\s*=\s*)[a-zA-Z0-9_\-\.]{16,}"""), "$1[REDACTED]")
    }

    fun stripSecretsFromEnvironment(env: Map<String, String>): Map<String, String> {
        val secretKeys = setOf("API_KEY", "SECRET", "TOKEN", "PASSWORD", "AUTH")
        return env.filterNot { (k, _) -> secretKeys.any { k.uppercase().contains(it) } }
    }

    fun isJailSupported(): Boolean {
        val isLinux = System.getProperty("os.name", "unknown").lowercase().contains("linux")
        return isLinux && isRoot() && Files.isExecutable(Path.of("/usr/bin/unshare"))
    }

    private fun isRoot(): Boolean {
        try {
            val process = ProcessBuilder("id", "-u").start()
            process.waitFor()
            return process.exitValue() == 0
        } catch (_: Exception) {
            return false
        }
    }
}