/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.toolchain

import atropos.core.adapter.HardwareProfileAdapter
import atropos.core.platform.PlatformAdapterRegistry
import java.io.File
import java.nio.file.Files

/**
 * SD7-T-080: PRoot sandbox fallback for Android/Termux environments.
 * Mathematical Invariant: Cap(P_theta) ∩ Cap(A_write) = ∅
 * Wraps toolchain commands in a rootless proot sandbox with READ_ONLY_ROOT and WRITEABLE_WORKTREE.
 */
object TermuxProotShim {

    fun isTermux(): Boolean {
        val prefix = System.getenv("PREFIX") ?: ""
        val termuxHome = System.getenv("TERMUX_VERSION") ?: ""
        return prefix.contains("com.termux") || termuxHome.isNotBlank() ||
            runCatching {
                val home = PlatformAdapterRegistry.current().getHomeDirectory()
                home.toString().contains("com.termux")
            }.getOrDefault(false)
    }

    fun isArm64(): Boolean {
        val arch = HardwareProfileAdapter().getArch()
        return arch.equals("aarch64", ignoreCase = true) || arch.equals("arm64", ignoreCase = true)
    }

    fun wrap(command: List<String>): List<String> {
        if (!isTermux() || !isArm64()) {
            return command
        }

        val resolved = PlatformAdapterRegistry.current().resolveToolPath("proot")
        val prootBin = if (Files.isExecutable(resolved)) {
            resolved.toString()
        } else {
            "proot"
        }

        val wrapped = mutableListOf(
            prootBin,
            "-0",
            "-b", "/data",
            "-b", "/dev",
            "-b", "/proc"
        )
        wrapped.addAll(command)
        return wrapped
    }
}
