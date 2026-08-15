/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.dloi

object TermuxPathResolver {
    private const val TERMUX_PREFIX = "/data/data/com.termux/files/home/"

    fun isTermuxPath(path: String): Boolean {
        return path.startsWith(TERMUX_PREFIX)
    }

    fun toStandardPath(path: String): String {
        if (isTermuxPath(path)) {
            return "/home/" + path.removePrefix(TERMUX_PREFIX)
        }
        return path
    }

    fun toTermuxPath(path: String): String {
        if (path.startsWith("/home/")) {
            return TERMUX_PREFIX + path.removePrefix("/home/")
        }
        return path
    }

    fun resolve(path: String): String {
        return if (isTermuxPath(path)) {
            toStandardPath(path)
        } else if (path.startsWith("/home/")) {
            toTermuxPath(path)
        } else if (path.startsWith("/root/")) {
            TERMUX_PREFIX + path.removePrefix("/root/")
        } else {
            path
        }
    }
}
