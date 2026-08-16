/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.phase20

import java.nio.file.Path
import java.nio.file.Paths

/** AnsiScheme (Item 262) & compliance checks. */
object AnsiScheme {
    const val RESET = "\u001B[0m"
    const val BOLD = "\u001B[1m"
    const val GREEN = "\u001B[32m"
    const val RED = "\u001B[31m"

    /**
     * Refuses any escape at all, for text that has not been painted yet.
     *
     * [assertNoRawEscapes] asks a different question — whether a *composed*
     * string uses only this scheme's sequences — and answering it for raw
     * content would admit `\u001B[31m` into a filename or an operator message
     * purely because red happens to be a colour this scheme also emits. Content
     * arriving at the painter carries no escapes; the painter is what adds
     * them.
     */
    fun assertNoEscapes(text: String) {
        require(!text.contains('\u001B')) {
            "Compliance error: raw ANSI escape code found in text to be painted"
        }
    }

    fun assertNoRawEscapes(text: String) {
        val escapeIndex = text.indexOf('\u001B')
        if (escapeIndex >= 0) {
            val substring = text.substring(escapeIndex)
            val isKnown = listOf(RESET, BOLD, GREEN, RED).any { substring.startsWith(it) }
            require(isKnown) { "Compliance error: raw ANSI escape code found outside AnsiScheme" }
        }
    }
}

/** GlobalByteCeiling (Item 263). */
object GlobalByteCeiling {
    const val MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024L // 10 MB limit
    const val MAX_WORKSPACE_CEILING = 100 * 1024 * 1024L // 100 MB limit

    fun verifyWithinCeiling(bytes: Long) {
        require(bytes <= MAX_WORKSPACE_CEILING) { "Byte ceiling exceeded: workspace grows past limit" }
    }
}

/** PathResolver (Item 264). */
object PathResolver {
    fun resolveSafe(root: Path, relativePath: String): Path {
        val resolved = root.resolve(relativePath).normalize().toAbsolutePath()
        require(resolved.startsWith(root.toAbsolutePath().normalize())) {
            "Compliance error: path traversal detected escaping root boundary"
        }
        return resolved
    }
}

/** ComputerUseBridge (Item 265). */
class ComputerUseBridge(
    private val mcpBridge: atropos.core.integration.ComputerUseTerritoryBridge = 
        atropos.core.integration.ComputerUseTerritoryBridge(setOf("inspect", "verify"))
) {
    fun convertRequest(
        callerId: String,
        operation: String,
        paths: List<String>,
        targetSurface: String,
        territoryGrantId: String
    ): atropos.core.integration.InboundToolRequest {
        return atropos.core.integration.InboundToolRequest(
            source = atropos.core.integration.InboundSource.COMPUTER_USE,
            callerId = callerId,
            operation = operation,
            paths = paths,
            targetSurface = targetSurface,
            territoryGrantId = territoryGrantId
        )
    }

    /** Submit the converted intent to the same territory/policy gate. */
    fun judge(
        callerId: String,
        operation: String,
        paths: List<String>,
        targetSurface: String,
        territoryGrantId: String
    ): atropos.core.integration.InboundGateResult =
        mcpBridge.judge(convertRequest(callerId, operation, paths, targetSurface, territoryGrantId))
}

/** SessionManager (Item 266). */
class SessionManager(
    private val maxTabs: Int = 10
) {
    private val activeSessions = mutableListOf<String>()

    fun openSession(id: String) {
        require(activeSessions.size < maxTabs) { "Tab limit exceeded: maximum of $maxTabs tabs allowed" }
        if (id !in activeSessions) {
            activeSessions.add(id)
        }
    }

    fun closeSession(id: String) {
        activeSessions.remove(id)
    }

    fun getActiveCount(): Int = activeSessions.size
}

/** RecoveryRibbon (Item 267). */
class RecoveryRibbon {
    fun renderRibbon(tectonicState: String): String {
        return "Tectonic Ribbon: [$tectonicState]"
    }
}

/** @mention file ingestion (Item 268). */
object MentionFileParser {
    fun parseMentions(prompt: String): List<String> {
        val regex = Regex("@([a-zA-Z0-9_/.-]+)")
        return regex.findAll(prompt).map { it.groupValues[1] }.toList()
    }
}

/** Territory monitor complexity cost counters (Item 269). */
class TerritoryMonitor {
    fun measureComplexity(files: List<String>): String {
        val n = files.size
        val linearCost = n
        val quadraticCost = n * n
        return "Territory monitor complexity: linearCost=$linearCost quadraticCost=$quadraticCost"
    }
}
