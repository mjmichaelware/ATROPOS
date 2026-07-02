package atropos.cli.commands

import atropos.cli.ui.AnsiTerminalEngine
import atropos.core.AtroposConfig
import atropos.core.agent.AgentPatchExtractor
import atropos.core.agent.AgentService
import java.nio.file.Files

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
    private val patchExtractor = AgentPatchExtractor()

    /** Last patch id ATROPOS has knowledge of, surfaced to the status line. Never implies a patch was applied. */
    var lastKnownPatchId: String? = null
        private set

    override fun execute(tokens: List<String>): AgentCommandOutcome {
        if (tokens.size < 2) {
            return invalid("usage: /agent [status|ask <task>|patch [--provider <name>] <task>|apply [--check|--verify] <patch-id|latest>|verify [<patch-id|latest>]|repair [<patch-id|latest>]]")
        }

        return when (tokens[1].lowercase()) {
            "status" -> {
                val snapshot = service.status(activeProviderName())
                lastKnownPatchId = snapshot.lastPatchId ?: lastKnownPatchId
                val rendered = formatBlock("AGENT STATUS", snapshot.render())
                ui.renderNotice(rendered)
                AgentCommandOutcome.Completed(rendered)
            }

            "verify" -> {
                val patchReference = parseReference(tokens.drop(2))
                if (patchReference == null) {
                    return invalid("usage: /agent verify [<patch-id|latest>]")
                }

                ui.startSpinner("Running deterministic verification")
                return try {
                    val result = service.verify(patchReference)
                    lastKnownPatchId = result.patchId ?: lastKnownPatchId
                    val rendered = formatBlock("AGENT VERIFY", result.render())
                    ui.renderNotice(rendered)
                    AgentCommandOutcome.Completed(rendered)
                } catch (failure: Exception) {
                    val message = failure.message ?: "agent verify failed"
                    ui.renderError(message)
                    AgentCommandOutcome.Invalid(message)
                } finally {
                    ui.stopSpinner()
                }
            }

            "ask" -> {
                val task = tokens.drop(2).joinToString(" ").trim()
                if (task.isBlank()) {
                    return invalid("usage: /agent ask <task>")
                }

                ui.startSpinner("Collecting repo context")
                return try {
                    val result = service.ask(activeProviderName(), task)
                    val rendered = formatBlock("AGENT ASK", result.render())
                    ui.renderNotice(rendered)
                    AgentCommandOutcome.Completed(rendered)
                } catch (failure: Exception) {
                    val message = failure.message ?: "agent ask failed"
                    ui.renderError(message)
                    AgentCommandOutcome.Invalid(message)
                } finally {
                    ui.stopSpinner()
                }
            }

            "repair" -> {
                val patchReference = parseReference(tokens.drop(2))
                if (patchReference == null) {
                    return invalid("usage: /agent repair [<patch-id|latest>]")
                }

                val preview = service.previewRepair(patchReference)
                if (preview != null) {
                    val rendered = formatBlock("AGENT REPAIR", preview.render())
                    ui.renderNotice(rendered)
                    return AgentCommandOutcome.Completed(rendered)
                }

                ui.startSpinner("Preparing repair patch")
                return try {
                    val result = service.repair(activeProviderName(), patchReference)
                    lastKnownPatchId = result.patchId ?: lastKnownPatchId
                    val rendered = formatBlock("AGENT REPAIR", result.render())
                    ui.renderNotice(rendered)
                    AgentCommandOutcome.Completed(rendered)
                } catch (failure: Exception) {
                    val message = failure.message ?: "agent repair failed"
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
                    lastKnownPatchId = result.patchId ?: lastKnownPatchId
                    val body = buildString {
                        append(result.render())
                        changedPathsPreview(result.patchPath)?.let {
                            appendLine()
                            append("Changed paths: $it")
                        }
                        appendLine()
                        append("Next command: ${nextPatchCommand(result)}")
                    }
                    val rendered = formatBlock("AGENT PATCH", body)
                    ui.renderNotice(rendered)
                    AgentCommandOutcome.Completed(rendered)
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
                    return invalid("usage: /agent apply [--check|--verify] <patch-id|latest>")
                }
                if (applyRequest.checkOnly && applyRequest.verifyAfterApply) {
                    return invalid("/agent apply supports either --check or --verify, not both")
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
                    lastKnownPatchId = result.patchId ?: lastKnownPatchId
                    val body = buildString {
                        append(result.render())
                        if (result.applied) {
                            appendLine()
                            append("No commit created: changes are in the working tree only.")
                        }
                    }
                    val rendered = formatBlock(
                        when {
                            applyRequest.checkOnly -> "AGENT APPLY --CHECK"
                            applyRequest.verifyAfterApply -> "AGENT APPLY --VERIFY"
                            else -> "AGENT APPLY"
                        },
                        body
                    )
                    ui.renderNotice(rendered)
                    AgentCommandOutcome.Completed(rendered)
                } catch (failure: Exception) {
                    val message = failure.message ?: "agent apply failed"
                    ui.renderError(message)
                    AgentCommandOutcome.Invalid(message)
                } finally {
                    ui.stopSpinner()
                }
            }

            else -> invalid("usage: /agent [status|ask <task>|patch [--provider <name>] <task>|apply [--check|--verify] <patch-id|latest>|verify [<patch-id|latest>]|repair [<patch-id|latest>]]")
        }
    }

    private fun changedPathsPreview(patchPath: java.nio.file.Path?, limit: Int = 6): String? {
        if (patchPath == null || !Files.isRegularFile(patchPath)) return null
        val diffText = runCatching { Files.readString(patchPath) }.getOrNull() ?: return null
        val paths = patchExtractor.extract(diffText)?.touchedPaths ?: return null
        if (paths.isEmpty()) return null
        val shown = paths.take(limit).joinToString(", ")
        val remaining = paths.size - limit
        return if (remaining > 0) "$shown (+$remaining more)" else shown
    }

    private fun nextPatchCommand(result: atropos.core.agent.AgentPatchRunResult): String = when {
        result.patchId == null -> "/agent patch <task>"
        result.checkResult == null -> "/agent apply --check ${result.patchId}"
        result.checkResult.passed -> "/agent apply --check ${result.patchId}  (check already OK)"
        else -> "/agent patch <task>  (git apply --check failed, regenerate)"
    }

    private fun formatBlock(title: String, body: String): String = buildString {
        appendLine("── $title ──")
        body.lineSequence().forEach { line -> append(wrapLine(line)).append('\n') }
    }.trimEnd()

    // Only very long unbroken lines are pre-wrapped; the reactive renderer already
    // wraps every transcript line at the live terminal width, so wrapping shorter
    // lines here too would double-wrap and mangle the output.
    private fun wrapLine(line: String, width: Int = 320): String {
        if (line.length <= width) return line
        val leading = line.takeWhile { it == ' ' }
        val words = line.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return line

        val available = (width - leading.length).coerceAtLeast(10)
        val segments = mutableListOf<String>()
        val current = StringBuilder()
        for (word in words) {
            if (current.isNotEmpty() && current.length + 1 + word.length > available) {
                segments += current.toString()
                current.clear()
            }
            if (current.isNotEmpty()) current.append(' ')
            current.append(word)
        }
        if (current.isNotEmpty()) segments += current.toString()

        return leading + segments.joinToString("\n$leading  ")
    }

    private data class PatchRequest(
        val providerOverride: String? = null,
        val task: String = ""
    )

    private data class ApplyRequest(
        val patchReference: String = "",
        val checkOnly: Boolean = false,
        val verifyAfterApply: Boolean = false
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

    private fun parseReference(args: List<String>): String? {
        if (args.isEmpty()) return "latest"
        if (args.size == 1 && !args[0].startsWith("--")) return args[0].trim().takeIf { it.isNotBlank() }
        return null
    }

    private fun parseApplyRequest(args: List<String>): ApplyRequest {
        if (args.isEmpty()) return ApplyRequest(patchReference = "latest")

        var checkOnly = false
        var verifyAfterApply = false
        var patchReference: String? = null

        for (token in args) {
            when {
                token == "--check" -> checkOnly = true
                token == "--verify" -> verifyAfterApply = true
                token.startsWith("--check=") -> checkOnly = token.substringAfter("=", "true").trim().toBooleanStrictOrNull() ?: true
                token.startsWith("--verify=") -> verifyAfterApply = token.substringAfter("=", "true").trim().toBooleanStrictOrNull() ?: true
                token.startsWith("--") -> return ApplyRequest()
                patchReference == null -> patchReference = token.trim()
                else -> return ApplyRequest()
            }
        }

        return ApplyRequest(
            patchReference = patchReference?.takeIf { it.isNotBlank() } ?: "latest",
            checkOnly = checkOnly,
            verifyAfterApply = verifyAfterApply
        )
    }

    private fun invalid(message: String): AgentCommandOutcome.Invalid {
        ui.renderError(message)
        return AgentCommandOutcome.Invalid(message)
    }
}
