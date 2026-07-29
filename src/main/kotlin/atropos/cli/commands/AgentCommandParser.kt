package atropos.cli.commands

data class AgentPatchRequest(
    val providerOverride: String? = null,
    val task: String = ""
)

data class AgentApplyRequest(
    val patchReference: String = "",
    val checkOnly: Boolean = false,
    val verifyAfterApply: Boolean = false
)

data class AgentJobRequest(
    val reference: String? = null,
    val raw: Boolean = false
)

data class AgentRunRequest(
    val smokeCommand: String? = null,
    val task: String = ""
)

object AgentCommandParser {
    fun parsePatchRequest(args: List<String>): AgentPatchRequest {
        if (args.isEmpty()) return AgentPatchRequest(task = "")

        var index = 0
        var providerOverride: String? = null
        while (index < args.size) {
            val token = args[index]
            when {
                token == "--provider" -> {
                    if (index + 1 >= args.size) return AgentPatchRequest(task = "")
                    providerOverride = args[index + 1].trim().lowercase()
                    index += 2
                }
                token.startsWith("--provider=") -> {
                    providerOverride = token.substringAfter("=").trim().lowercase()
                    index++
                }
                token.startsWith("--") -> break
                else -> break
            }
        }

        val task = args.drop(index).joinToString(" ").trim()
        return AgentPatchRequest(providerOverride = providerOverride?.takeIf { it.isNotBlank() }, task = task)
    }

    fun parseReference(args: List<String>): String? {
        if (args.isEmpty()) return "latest"
        if (args.size == 1 && !args[0].startsWith("--")) return args[0].trim().takeIf { it.isNotBlank() }
        return null
    }

    fun parseJobRequest(args: List<String>): AgentJobRequest {
        if (args.isEmpty()) return AgentJobRequest(reference = "latest")

        var raw = false
        val referenceParts = mutableListOf<String>()
        for (token in args) {
            when {
                token == "--raw" || token.equals("raw", ignoreCase = true) -> raw = true
                token.startsWith("--raw=") -> raw = token.substringAfter("=", "true").trim().toBooleanStrictOrNull() ?: true
                token.startsWith("--") -> return AgentJobRequest()
                else -> referenceParts += token.trim()
            }
        }

        val reference = referenceParts.joinToString(" ").trim().ifBlank { "latest" }
        return AgentJobRequest(reference = reference, raw = raw)
    }

    fun parseQueueShowRequest(args: List<String>): AgentJobRequest =
        parseJobRequest(args)

    fun parseApplyRequest(args: List<String>): AgentApplyRequest {
        if (args.isEmpty()) return AgentApplyRequest(patchReference = "latest")

        var checkOnly = false
        var verifyAfterApply = false
        var patchReference: String? = null
        for (token in args) {
            when {
                token == "--check" -> checkOnly = true
                token == "--verify" -> verifyAfterApply = true
                token.startsWith("--check=") -> checkOnly = token.substringAfter("=", "true").trim().toBooleanStrictOrNull() ?: true
                token.startsWith("--verify=") -> verifyAfterApply = token.substringAfter("=", "true").trim().toBooleanStrictOrNull() ?: true
                token.startsWith("--") -> return AgentApplyRequest()
                patchReference == null -> patchReference = token.trim()
                else -> return AgentApplyRequest()
            }
        }

        return AgentApplyRequest(
            patchReference = patchReference?.takeIf { it.isNotBlank() } ?: "latest",
            checkOnly = checkOnly,
            verifyAfterApply = verifyAfterApply
        )
    }

    fun parseRunRequest(args: List<String>): AgentRunRequest {
        if (args.isEmpty()) return AgentRunRequest(task = "")

        var smokeCommand: String? = null
        val taskParts = mutableListOf<String>()
        var index = 0
        while (index < args.size) {
            val token = args[index]
            when {
                token == "--smoke" -> {
                    val smoke = args.getOrNull(index + 1)?.trim()
                    if (smoke.isNullOrBlank() || smoke.startsWith("--")) return AgentRunRequest()
                    smokeCommand = smoke
                    index += 2
                }
                token.startsWith("--smoke=") -> {
                    val smoke = token.substringAfter("=").trim()
                    if (smoke.isBlank()) return AgentRunRequest()
                    smokeCommand = smoke
                    index++
                }
                token.startsWith("--") -> return AgentRunRequest()
                else -> {
                    taskParts += token
                    index++
                }
            }
        }

        return AgentRunRequest(
            smokeCommand = smokeCommand?.takeIf { it.isNotBlank() },
            task = taskParts.joinToString(" ").trim()
        )
    }
}
