/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

/**
 * Serialises and deserialises [ContextEnvelope] instances to/from the
 * machine-readable blocks embedded in every provider prompt and expected
 * back in the provider's response.
 */
object ContextEnvelopeSerializer {

    private const val HEADER = "--- ATROPOS CONTEXT ENVELOPE ---"
    private const val FOOTER = "--- END ATROPOS CONTEXT ENVELOPE ---"
    private const val ATTESTATION_HEADER = "--- ATROPOS CONTEXT ATTESTATION ---"
    private const val ATTESTATION_FOOTER = "--- END ATROPOS CONTEXT ATTESTATION ---"

    /**
     * Serialise the envelope into a machine-readable block suitable for
     * inclusion in a provider prompt.
     */
    fun serialize(envelope: ContextEnvelope): String = buildString {
        appendLine(HEADER)
        appendLine("contextVersion=${envelope.contextVersion}")
        appendLine("systemIdentity=${envelope.systemIdentity}")
        appendLine("systemType=${envelope.systemType}")
        appendLine("repository=${envelope.repository}")
        appendLine("repositoryRoot=${envelope.repositoryRoot}")
        appendLine("branch=${envelope.branch}")
        appendLine("baselineCommit=${envelope.baselineCommit}")
        appendLine("goalId=${envelope.goalId}")
        appendLine("runId=${envelope.runId}")
        appendLine("dagId=${envelope.dagId}")
        appendLine("nodeId=${envelope.nodeId}")
        appendLine("task=${escape(envelope.task)}")
        appendLine("phaseOrPass=${envelope.phaseOrPass}")
        appendLine("hierarchyRole=${envelope.hierarchyRole}")
        appendLine("authority=${envelope.authority}")
        appendLine("permissions=${envelope.permissions.joinToString(",")}")
        appendLine("assignedTerritory=${envelope.assignedTerritory.joinToString(",")}")
        appendLine("prohibitedActions=${envelope.prohibitedActions.joinToString(",")}")
        appendLine("activePolicy=${envelope.activePolicy}")
        appendLine("providerId=${envelope.providerId}")
        appendLine("modelId=${envelope.modelId}")
        appendLine("canonicalContextHash=${envelope.canonicalContextHash}")
        appendLine(FOOTER)
    }

    /**
     * Parse an envelope from a serialized block.
     * Returns null if the block cannot be parsed.
     */
    fun parse(text: String): ContextEnvelope? {
        val start = text.indexOf(HEADER)
        val end = text.indexOf(FOOTER)
        if (start < 0 || end < 0 || end <= start) return null

        val block = text.substring(start + HEADER.length, end).trim()
        val map = mutableMapOf<String, String>()
        for (line in block.lines()) {
            val eq = line.indexOf('=')
            if (eq > 0) {
                map[line.substring(0, eq).trim()] = line.substring(eq + 1).trim()
            }
        }

        return try {
            ContextEnvelope(
                systemIdentity = map["systemIdentity"] ?: "ATROPOS",
                systemType = map["systemType"] ?: "autonomous software engine",
                repository = map["repository"] ?: "",
                repositoryRoot = map["repositoryRoot"] ?: "",
                branch = map["branch"] ?: "",
                baselineCommit = map["baselineCommit"] ?: "",
                goalId = map["goalId"] ?: "",
                runId = map["runId"] ?: "",
                dagId = map["dagId"] ?: "",
                nodeId = map["nodeId"] ?: "",
                task = unescape(map["task"] ?: ""),
                phaseOrPass = map["phaseOrPass"] ?: "",
                hierarchyRole = map["hierarchyRole"] ?: "worker",
                authority = map["authority"] ?: "bounded",
                permissions = map["permissions"]?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
                assignedTerritory = map["assignedTerritory"]?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
                prohibitedActions = map["prohibitedActions"]?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
                activePolicy = map["activePolicy"] ?: "",
                providerId = map["providerId"] ?: "",
                modelId = map["modelId"] ?: "",
                contextVersion = map["contextVersion"] ?: "1.0",
                canonicalContextHash = map["canonicalContextHash"] ?: ""
            )
        } catch (_: Exception) { null }
    }

    /**
     * Build the attestation block that a provider must include in its response.
     */
    fun attestationBlock(
        systemIdentity: String,
        repository: String,
        taskOrNodeId: String,
        role: String,
        contextVersion: String,
        contextHash: String
    ): String = buildString {
        appendLine(ATTESTATION_HEADER)
        appendLine("systemIdentity=$systemIdentity")
        appendLine("repository=$repository")
        appendLine("taskOrNodeId=$taskOrNodeId")
        appendLine("role=$role")
        appendLine("contextVersion=$contextVersion")
        appendLine("contextHash=$contextHash")
        appendLine(ATTESTATION_FOOTER)
    }

    /**
     * Parse a provider's response attestation block.
     * Returns null if no valid attestation is found.
     */
    fun parseAttestation(text: String): ContextAttestation? {
        val start = text.indexOf(ATTESTATION_HEADER)
        val end = text.indexOf(ATTESTATION_FOOTER)
        if (start < 0 || end < 0 || end <= start) return null

        val block = text.substring(start + ATTESTATION_HEADER.length, end).trim()
        val map = mutableMapOf<String, String>()
        for (line in block.lines()) {
            val eq = line.indexOf('=')
            if (eq > 0) {
                map[line.substring(0, eq).trim()] = line.substring(eq + 1).trim()
            }
        }

        return try {
            ContextAttestation(
                systemIdentity = map["systemIdentity"] ?: return null,
                repository = map["repository"] ?: return null,
                taskOrNodeId = map["taskOrNodeId"] ?: "",
                role = map["role"] ?: "",
                contextVersion = map["contextVersion"] ?: return null,
                contextHash = map["contextHash"] ?: return null
            )
        } catch (_: Exception) { null }
    }

    private fun escape(s: String): String =
        s.replace("\\", "\\\\")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("=", "\\=")

    private fun unescape(s: String): String =
        s.replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\=", "=")
            .replace("\\\\", "\\")
}
