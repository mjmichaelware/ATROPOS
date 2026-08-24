package atropos.cli.commands

import atropos.cli.ui.AnsiTerminalEngine
import atropos.core.agent.AgentPatchExtractor
import atropos.core.agent.AgentService
import atropos.core.provider.ApiCapability
import atropos.core.provider.StaticProviderDescriptorRegistry

class AgentPatchCommandHandler(
    private val ui: AnsiTerminalEngine,
    private val service: AgentService,
    private val identityResponder: AgentIdentityResponder,
    private val activeProviderName: () -> String,
    private val currentPatchId: () -> String?,
    private val invalid: (String) -> AgentCommandOutcome.Invalid
) {
    private val patchDisplay = AgentPatchDisplayHelper(AgentPatchExtractor())

    fun ask(args: List<String>): AgentCommandExecutionResult {
        val task = args.joinToString(" ").trim()
        if (task.isBlank()) {
            return invalidResult("usage: /agent ask <task>")
        }

        identityResponder.respond(task)?.let { answer ->
            val rendered = AgentCommandText.formatBlock("AGENT ASK", answer)
            ui.renderNotice(rendered)
            return AgentCommandExecutionResult(AgentCommandOutcome.Completed(rendered))
        }

        ui.startSpinner("Collecting repo context")
        return try {
            val result = service.ask(activeProviderName(), task)
            val rendered = AgentCommandText.formatBlock("AGENT ASK", result.render())
            completed(rendered)
        } catch (failure: Exception) {
            invalidResult(failure.message ?: "agent ask failed")
        } finally {
            ui.stopSpinner()
        }
    }

    fun repair(args: List<String>): AgentCommandExecutionResult {
        val patchReference = AgentCommandParser.parseReference(args)
            ?: return invalidResult("usage: /agent repair [<patch-id|latest>]")

        service.previewRepair(patchReference)?.let { preview ->
            val rendered = AgentCommandText.formatBlock("AGENT REPAIR", preview.render())
            ui.renderNotice(rendered)
            return AgentCommandExecutionResult(AgentCommandOutcome.Completed(rendered))
        }

        ui.startSpinner("Preparing repair patch")
        return try {
            val result = service.repair(activeProviderName(), patchReference)
            val rendered = AgentCommandText.formatBlock("AGENT REPAIR", result.render())
            completed(rendered, result.patchId ?: currentPatchId())
        } catch (failure: Exception) {
            invalidResult(failure.message ?: "agent repair failed")
        } finally {
            ui.stopSpinner()
        }
    }

    fun patch(args: List<String>): AgentCommandExecutionResult {
        val patchRequest = AgentCommandParser.parsePatchRequest(args)
        val task = patchRequest.task
        if (task.isBlank()) {
            return invalidResult("usage: /agent patch [--provider <name>] <task>")
        }
        if (patchRequest.providerOverride != null && patchRequest.providerOverride !in patchProviderAllowList) {
            return invalidResult("/agent patch provider override must be one of: ${patchProviderAllowList.joinToString(", ")}")
        }

        ui.startSpinner("Collecting repo context")
        return try {
            val result = service.patch(activeProviderName(), task, patchRequest.providerOverride)
            val body = buildString {
                append(result.render())
                patchDisplay.richDiffSummary(result.patchPath)?.let {
                    appendLine()
                    appendLine()
                    append("Changes:")
                    appendLine()
                    append(it)
                } ?: patchDisplay.changedPathsPreview(result.patchPath)?.let {
                    appendLine()
                    append("Changed paths: $it")
                }
                appendLine()
                append("Next command: ${patchDisplay.nextPatchCommand(result)}")
            }
            completed(AgentCommandText.formatBlock("AGENT PATCH", body), result.patchId ?: currentPatchId())
        } catch (failure: Exception) {
            invalidResult(failure.message ?: "agent patch failed")
        } finally {
            ui.stopSpinner()
        }
    }

    fun apply(args: List<String>): AgentCommandExecutionResult {
        val applyRequest = AgentCommandParser.parseApplyRequest(args)
        if (applyRequest.patchReference.isBlank()) {
            return invalidResult("usage: /agent apply [--check|--verify] <patch-id|latest>")
        }
        if (applyRequest.checkOnly && applyRequest.verifyAfterApply) {
            return invalidResult("/agent apply supports either --check or --verify, not both")
        }

        ui.startSpinner(
            when {
                applyRequest.checkOnly -> "Validating stored patch"
                applyRequest.verifyAfterApply -> "Applying and verifying stored patch"
                else -> "Applying stored patch"
            }
        )
        return try {
            val result = service.applyPatch(
                applyRequest.patchReference,
                applyRequest.checkOnly,
                applyRequest.verifyAfterApply
            )
            val body = buildString {
                append(result.render())
                if (result.applied) {
                    appendLine()
                    append("No commit created: changes are in the working tree only.")
                }
            }
            val rendered = AgentCommandText.formatBlock(
                when {
                    applyRequest.checkOnly -> "AGENT APPLY --CHECK"
                    applyRequest.verifyAfterApply -> "AGENT APPLY --VERIFY"
                    else -> "AGENT APPLY"
                },
                body
            )
            completed(rendered, result.patchId ?: currentPatchId())
        } catch (failure: Exception) {
            invalidResult(failure.message ?: "agent apply failed")
        } finally {
            ui.stopSpinner()
        }
    }

    private fun completed(rendered: String, patchId: String? = null): AgentCommandExecutionResult {
        ui.renderNotice(rendered)
        return AgentCommandExecutionResult(AgentCommandOutcome.Completed(rendered), patchId)
    }

    private fun invalidResult(message: String): AgentCommandExecutionResult =
        AgentCommandExecutionResult(invalid(message))

    private companion object {
        val patchProviderAllowList = StaticProviderDescriptorRegistry()
            .getAll()
            .filter { descriptor ->
                (descriptor.hasCapability(ApiCapability.CODE) ||
                    descriptor.hasCapability(ApiCapability.REPAIR)) &&
                    !descriptor.isPaid()
            }
            .map { it.id }
            .toSet()
    }
}
