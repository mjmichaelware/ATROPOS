package atropos.cli.commands

import atropos.cli.ui.AnsiTerminalEngine
import atropos.core.AtroposConfig
import atropos.core.agent.AgentService

sealed class AgentCommandOutcome {
    data class Completed(val text: String) : AgentCommandOutcome()
    data class Invalid(val message: String) : AgentCommandOutcome()
}

fun interface AgentCommandHandler {
    fun execute(tokens: List<String>): AgentCommandOutcome
}

class AgentCommand(
    private val ui: AnsiTerminalEngine,
    private val config: AtroposConfig = AtroposConfig.load(),
    private val activeProviderName: () -> String,
    private val service: AgentService = AgentService(config)
) : AgentCommandHandler {
    override fun execute(tokens: List<String>): AgentCommandOutcome {
        if (tokens.size < 2) {
            return invalid("usage: /agent [status|ask <task>|patch [--provider <name>] <task>|apply [--check] <patch-id|latest>]")
        }

        return when (tokens[1].lowercase()) {
            "status" -> {
                val snapshot = service.status(activeProviderName())
                ui.renderNotice(snapshot.render())
                AgentCommandOutcome.Completed(snapshot.render())
            }

            "ask" -> {
                val task = tokens.drop(2).joinToString(" ").trim()
                if (task.isBlank()) {
                    return invalid("usage: /agent ask <task>")
                }

                ui.startSpinner("Collecting repo context")
                return try {
                    val result = service.ask(activeProviderName(), task)
                    ui.renderNotice(result.render())
                    AgentCommandOutcome.Completed(result.render())
                } catch (failure: Exception) {
                    val message = failure.message ?: "agent ask failed"
                    ui.renderError(message)
                    AgentCommandOutcome.Invalid(message)
                } finally {
                    ui.stopSpinner()
                }
            }

            "patch" -> {
                val patchRequest = parsePatchRequest(tokens.drop(2))
                val task = patchRequest.task
                if (task.isBlank()) {
                    return invalid("usage: /agent patch [--provider <name>] <task>")
                }
                if (patchRequest.providerOverride != null && patchRequest.providerOverride !in patchProviderAllowList) {
                    return invalid("/agent patch provider override must be one of: ${patchProviderAllowList.joinToString(", ")}")
                }

                ui.startSpinner("Collecting repo context")
                return try {
                    val result = service.patch(activeProviderName(), task, patchRequest.providerOverride)
                    ui.renderNotice(result.render())
                    AgentCommandOutcome.Completed(result.render())
                } catch (failure: Exception) {
                    val message = failure.message ?: "agent patch failed"
                    ui.renderError(message)
                    AgentCommandOutcome.Invalid(message)
                } finally {
                    ui.stopSpinner()
                }
            }

            "apply" -> {
                val applyRequest = parseApplyRequest(tokens.drop(2))
                if (applyRequest.patchReference.isBlank()) {
                    return invalid("usage: /agent apply [--check] <patch-id|latest>")
                }

                ui.startSpinner("Validating stored patch")
                return try {
                    val result = service.applyPatch(applyRequest.patchReference, applyRequest.checkOnly)
                    ui.renderNotice(result.render())
                    AgentCommandOutcome.Completed(result.render())
                } catch (failure: Exception) {
                    val message = failure.message ?: "agent apply failed"
                    ui.renderError(message)
                    AgentCommandOutcome.Invalid(message)
                } finally {
                    ui.stopSpinner()
                }
            }

            else -> invalid("usage: /agent [status|ask <task>|patch [--provider <name>] <task>|apply [--check] <patch-id|latest>]")
        }
    }

    private data class PatchRequest(
        val providerOverride: String? = null,
        val task: String = ""
    )

    private val patchProviderAllowList = setOf("github_models", "sambanova", "cloudflare_ai", "groq")

    private fun parsePatchRequest(args: List<String>): PatchRequest {
        if (args.isEmpty()) return PatchRequest(task = "")

        var index = 0
        var providerOverride: String? = null

        while (index < args.size) {
            val token = args[index]
            when {
                token == "--provider" -> {
                    if (index + 1 >= args.size) {
                        return PatchRequest(task = "")
                    }
                    providerOverride = args[index + 1].trim().lowercase()
                    index += 2
                }
                token.startsWith("--provider=") -> {
                    providerOverride = token.substringAfter("=").trim().lowercase()
                    index++
                }
                token.startsWith("--") -> {
                    break
                }
                else -> break
            }
        }

        val task = args.drop(index).joinToString(" ").trim()
        return PatchRequest(providerOverride = providerOverride?.takeIf { it.isNotBlank() }, task = task)
    }

    private data class ApplyRequest(
        val checkOnly: Boolean = false,
        val patchReference: String = ""
    )

    private fun parseApplyRequest(args: List<String>): ApplyRequest {
        if (args.isEmpty()) return ApplyRequest()

        var checkOnly = false
        var patchReference: String? = null

        for (token in args) {
            when {
                token == "--check" -> checkOnly = true
                token.startsWith("--") -> return ApplyRequest()
                patchReference == null -> patchReference = token
                else -> return ApplyRequest()
            }
        }

        return ApplyRequest(
            checkOnly = checkOnly,
            patchReference = patchReference?.trim().orEmpty()
        )
    }

    private fun invalid(message: String): AgentCommandOutcome.Invalid {
        ui.renderError(message)
        return AgentCommandOutcome.Invalid(message)
    }
}
