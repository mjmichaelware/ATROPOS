/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.factory

import java.util.concurrent.ConcurrentHashMap

/**
 * ATOM W7: File Extension Language Association Override.
 *
 * Maps custom file extensions, shebangs, and specific filenames to canonical language identifiers
 * (Kotlin, TypeScript, Python, JSON, Markdown), overriding default file extension tables.
 *
 * ## Research: 512-Byte Shebang and Magic Header Sniffing
 * Researches inspecting the initial 512 bytes of extensionless files (e.g. `#!/usr/bin/env python3`, `<?xml`)
 * to reliably detect target syntax when extensions are missing.
 */
object FileExtensionLanguageAssociator {

    private val customAssociations = ConcurrentHashMap<String, String>()

    init {
        customAssociations[".config"] = "json"
        customAssociations[".env"] = "properties"
        customAssociations[".spec"] = "markdown"
        customAssociations["Jenkinsfile"] = "groovy"
        customAssociations["Dockerfile"] = "dockerfile"
    }

    fun registerAssociation(pattern: String, languageId: String) {
        customAssociations[pattern] = languageId
    }

    fun resolveLanguage(fileName: String, headerSample: String = ""): String {
        // Direct filename match
        val direct = customAssociations[fileName]
        if (direct != null) return direct

        // Extension match
        val extIndex = fileName.lastIndexOf('.')
        if (extIndex != -1) {
            val ext = fileName.substring(extIndex)
            val matched = customAssociations[ext]
            if (matched != null) return matched
        }

        // Header / shebang sniffing
        if (headerSample.startsWith("#!/bin/bash") || headerSample.startsWith("#!/bin/sh") || headerSample.startsWith("#!/usr/bin/env bash")) {
            return "shell"
        }
        if (headerSample.startsWith("#!/usr/bin/env python") || headerSample.startsWith("#!/usr/bin/python")) {
            return "python"
        }

        return "plaintext"
    }
}
