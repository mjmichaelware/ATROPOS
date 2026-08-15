/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.integration

import java.io.File

object AndroidHoeShell {
    fun runShellCommand(command: String): String {
        return "AndroidShell: $command"
    }
}

object AtroposWebResolver {
    fun webDirExists(): Boolean {
        // apps/atropos-web/ does not exist - fallback check
        return File("apps/atropos-web").exists()
    }
}

object DesktopSurfaceStub {
    fun launchSurface() {
        println("Desktop Surface Launched")
    }
}

object HoeCliClipboardBridge {
    private var clipboardContent = ""

    fun copyToClipboard(text: String) {
        clipboardContent = text
    }

    fun getClipboardContent(): String = clipboardContent
}

object TerritoryMaterializer {
    fun renderTerritoryMaterial(territory: List<String>): String {
        return "TerritoryMaterial: [${territory.joinToString(",")}]"
    }
}

object AttestationFocusState {
    fun getAttestationFocus(hash: String): String {
        return "OpticalFocus: sha256($hash)"
    }
}

object RecoveryTectonicRibbon {
    fun renderRibbon(restartCount: Int): String {
        return "TectonicRibbon: restarts=$restartCount"
    }
}

object RethemeFromStatus {
    fun selectTheme(status: String): String {
        return when (status) {
            "FAILED" -> "DARK_RED"
            "SUCCESS" -> "DARK_GREEN"
            else -> "DEFAULT"
        }
    }
}
