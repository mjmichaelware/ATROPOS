package atropos.core.toolchain

import atropos.core.platform.Platform
import atropos.core.platform.RuntimePlatform
import java.nio.file.Files
import java.nio.file.Path

class ChrootUnshareJail {
    fun wrapCommand(worktreePath: Path, command: List<String>): List<String> {
        if (!isJailSupported()) {
            return command
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
        ) + command
    }

    fun isJailSupported(): Boolean {
        return Platform.currentPlatform() == RuntimePlatform.LINUX &&
               isRoot() &&
               Files.isExecutable(Path.of("/usr/bin/unshare"))
    }

    private fun isRoot(): Boolean {
        try {
            val process = ProcessBuilder("id", "-u").start()
            process.waitFor()
            return process.exitValue() == 0
        } catch (e: Exception) {
            return false
        }
    }
}