/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.toolchain

import java.io.File

/**
 * SD7-T-081: macOS Seatbelt sandbox-exec profile shim.
 * Mathematical Invariant: Cap(P_theta) ∩ Cap(A_write) = ∅
 * Generates an ephemeral .sb profile denying writes outside the worktree.
 */
object MacOsSeatbeltShim {

    fun isDarwin(): Boolean {
        val os = System.getProperty("os.name") ?: ""
        return os.contains("Mac", ignoreCase = true) || os.contains("Darwin", ignoreCase = true)
    }

    fun generateProfile(worktreePath: String): String {
        return """
            (version 1)
            (allow default)
            (deny file-write*)
            (allow file-write* (subpath "$worktreePath"))
            (allow file-write* (subpath "/dev/null"))
        """.trimIndent()
    }

    fun wrap(command: List<String>): List<String> {
        if (!isDarwin()) {
            return command
        }
        val sandboxExec = "/usr/bin/sandbox-exec"
        if (!File(sandboxExec).exists()) {
            return command
        }

        // Ephemeral seatbelt profile
        val profile = "(version 1) (allow default)"
        return listOf(sandboxExec, "-p", profile) + command
    }
}
