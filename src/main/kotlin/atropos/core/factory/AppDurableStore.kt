/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.factory

import java.io.File
import java.nio.charset.StandardCharsets

class DurableProjectStore(private val storageDir: File) {
    fun saveProject(projectId: String, content: String) {
        val file = File(storageDir, "$projectId.json")
        file.parentFile.mkdirs()
        file.writeText(content, StandardCharsets.UTF_8)
    }

    fun loadProject(projectId: String): String? {
        val file = File(storageDir, "$projectId.json")
        return if (file.exists()) file.readText(StandardCharsets.UTF_8) else null
    }
}

object TerminalEvidenceLinker {
    fun linkTerminalSession(terminalId: String, evidenceHash: String): String {
        return "TerminalSessionLink: terminal=$terminalId evidence=$evidenceHash"
    }
}

object AndroidHoeChrome {
    fun renderChrome(title: String): String {
        return "AndroidChromeHeader: $title"
    }
}

object ColorIndependenceTest {
    fun verifyColorIndependence(plainText: String, colorText: String): Boolean {
        // Confirm no non-colour channel verification is broken by stripping colors
        val stripped = colorText.replace(Regex("\\u001B\\[[;\\d]*m"), "")
        return stripped.trim() == plainText.trim()
    }
}

object InternalsDisclosure {
    fun getDisclosureLevel(role: String): String {
        return if (role == "DEVELOPER" || role == "L4_INTERNAL") {
            "FULL_DISCLOSURE"
        } else {
            "RESTRICTED"
        }
    }
}
