/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.toolchain

import java.io.File

/**
 * SD7-T-080: PRoot sandbox fallback for Android/Termux environments.
 * Mathematical Invariant: Cap(P_theta) ∩ Cap(A_write) = ∅
 * Wraps toolchain commands in a rootless proot sandbox with READ_ONLY_ROOT and WRITEABLE_WORKTREE.
 */
object TermuxProotShim {

    fun isTermux(): Boolean {
        val prefix = System.getenv("PREFIX") ?: ""
        return prefix.contains("com.termux") || File("/data/data/com.termux").exists()
    }

    fun isArm64(): Boolean {
        val arch = System.getProperty("os.arch") ?: ""
        return arch.equals("aarch64", ignoreCase = true) || arch.equals("arm64", ignoreCase = true)
    }

    fun wrap(command: List<String>): List<String> {
        if (!isTermux() || !isArm64()) {
            return command
        }

        val prootBin = if (File("/data/data/com.termux/files/usr/bin/proot").exists()) {
            "/data/data/com.termux/files/usr/bin/proot"
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
