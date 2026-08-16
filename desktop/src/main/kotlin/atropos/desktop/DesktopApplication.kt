/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

/** Desktop entrypoint. It owns only the window lifecycle; engine policy stays in core. */
fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "ATROPOS",
    ) {
        DesktopSurface()
    }
}
