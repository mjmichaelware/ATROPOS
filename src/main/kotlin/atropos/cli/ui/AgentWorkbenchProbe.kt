/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import java.io.File

data class AgentPatchWorkbenchTruth(
    val latestPatchId: String?,
    val checkStatus: String,
    val applyState: String,
    val changedPathsCount: Int?,
    val nextCommand: String
)

data class AgentWorkbenchTruth(
    val askAvailable: Boolean,
    val patchAvailable: Boolean,
    val applyAvailable: Boolean,
    val patchProviderOrder: List<String>,
    val localFallbackEnabled: Boolean,
    val paidLocked: Boolean,
    val patch: AgentPatchWorkbenchTruth
)

/**
 * Disk-only, non-blocking read of agent/patch state for dashboard display.
 * Deliberately avoids network probes (e.g. Ollama health checks) so the
 * landing dashboard never stalls while idle or typing.
 */
class AgentWorkbenchProbe {
    private val patchDocOrder = listOf("github_models", "sambanova", "cloudflare_ai", "groq")

    fun probe(workspace: String, groqConfigured: Boolean): AgentWorkbenchTruth {
        val configured = linkedSetOf<String>()
        if (!System.getenv("GITHUB_MODELS_TOKEN").isNullOrBlank()) configured += "github_models"
        if (!System.getenv("SAMBANOVA_API_KEY").isNullOrBlank()) configured += "sambanova"
        if (!System.getenv("CLOUDFLARE_API_TOKEN").isNullOrBlank() &&
            !System.getenv("CLOUDFLARE_ACCOUNT_ID").isNullOrBlank()
        ) {
            configured += "cloudflare_ai"
        }
        if (groqConfigured) configured += "groq"

        val order = patchDocOrder.filter { it in configured }
        val paidLocked = runCatching {
            atropos.core.paid.EmergencyPaidGate().status().locked
        }.getOrDefault(true)

        val patchDir = File(workspace, ".atropos/agent/patches")

        return AgentWorkbenchTruth(
            askAvailable = true,
            patchAvailable = order.isNotEmpty(),
            applyAvailable = true,
            patchProviderOrder = order,
            localFallbackEnabled = true,
            paidLocked = paidLocked,
            patch = probePatch(patchDir)
        )
    }

    private fun probePatch(patchDir: File): AgentPatchWorkbenchTruth {
        val none = AgentPatchWorkbenchTruth(
            latestPatchId = null,
            checkStatus = "NOT RUN",
            applyState = "not attempted",
            changedPathsCount = null,
            nextCommand = "/agent patch <task>"
        )

        if (!patchDir.isDirectory) return none

        val latestId = patchDir.listFiles { file -> file.isFile && file.name.endsWith(".diff") }
            ?.map { it.name.removeSuffix(".diff") }
            ?.sorted()
            ?.lastOrNull()
            ?: return none

        val meta = parseKeyValueFile(File(patchDir, "$latestId.meta"))
        val checkStatus = meta["gitApplyCheckStatus"] ?: "NOT RUN"

        val lastApply = patchDir.listFiles { file ->
            file.isFile && file.name.startsWith("apply-") && file.name.endsWith(".meta")
        }
            ?.sortedBy { it.name }
            ?.map(::parseKeyValueFile)
            ?.lastOrNull { it["patchId"] == latestId }

        val applyState = when {
            lastApply == null -> "not attempted"
            lastApply["applied"] == "true" -> "applied"
            lastApply["checkOnly"] == "true" -> "checked only"
            else -> "refused"
        }

        val changedCount = lastApply?.get("changedPaths")
            ?.split(",")
            ?.map(String::trim)
            ?.filter(String::isNotBlank)
            ?.size

        val nextCommand = when {
            applyState == "applied" -> "git status --short"
            checkStatus == "OK" -> "/agent apply latest"
            checkStatus == "FAILED" -> "/agent patch <task>"
            else -> "/agent apply --check latest"
        }

        return AgentPatchWorkbenchTruth(
            latestPatchId = latestId,
            checkStatus = checkStatus,
            applyState = applyState,
            changedPathsCount = changedCount,
            nextCommand = nextCommand
        )
    }

    private fun parseKeyValueFile(file: File): Map<String, String> {
        if (!file.isFile) return emptyMap()
        return runCatching {
            file.readLines().mapNotNull { line ->
                val index = line.indexOf('=')
                if (index <= 0) null else line.substring(0, index) to line.substring(index + 1)
            }.toMap()
        }.getOrDefault(emptyMap())
    }
}
