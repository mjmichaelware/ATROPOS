package atropos.cli

import atropos.core.AtroposConfig
import atropos.core.ProviderFactory
import atropos.core.AIProvider
import atropos.cli.ui.AnsiTerminalEngine
import atropos.data.cache.CodebaseDeltaTreeTracker
import kotlin.system.exitProcess

class CommandRouter(private val config: AtroposConfig) {
    private val providerFactory = ProviderFactory(config)
    private val uiEngine = AnsiTerminalEngine()
    private var currentProviderName = config.runtime.defaultProvider
    private var activeProvider: AIProvider = providerFactory.getProvider(currentProviderName)

    fun handleInput(input: String) {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return
        if (trimmed.startsWith("/")) handleCommand(trimmed) else handlePrompt(trimmed)
    }

    private fun handleCommand(cmdStr: String) {
        val parts = cmdStr.split("\\s+".toRegex())
        when (parts[0].lowercase()) {
            "/exit" -> { 
                println("Session closed.")
                exitProcess(0) 
            }
            "/help" -> printHelp()
            "/status" -> printStatus()
            "/use" -> if (parts.size >= 2) switchProvider(parts[1]) else println("Format: /use <provider>")
            else -> println("Unknown command. Type /help")
        }
    }

    private fun switchProvider(name: String) {
        try {
            activeProvider = providerFactory.getProvider(name)
            currentProviderName = name.lowercase()
            println("Switched interface link tracking to: ${activeProvider.name}")
        } catch (e: Exception) { 
            println("Switch Error: ${e.message}") 
        }
    }

    private fun handlePrompt(prompt: String) {
        println("Routing pipeline call through [${activeProvider.name}]...")
        val response = activeProvider.complete(prompt, "")
        println("\n--- Response (${activeProvider.name}) ---\n$response\n--------------------------------\n")
    }

    private fun printStatus() {
        // Clear screen and draw core HUD headers
        uiEngine.clearScreen()
        uiEngine.renderHeader()
        uiEngine.renderStatusMatrix(config, currentProviderName)
        
        // Execute the background porcelain process tracker mapping pass
        println("\u001B[34m╭── Repository Workspace Deltas ──────────────────────────────────╮\u001B[0m")
        try {
            val tracker = CodebaseDeltaTreeTracker()
            val deltas = tracker.getActiveWorkspaceDeltas()
            if (deltas.isEmpty()) {
                println("  \u001B[90mWorkspace completely clean. Zero uncommitted tokens detected.\u001B[0m")
            } else {
                deltas.forEach { delta ->
                    val color = when(delta.modificationType) {
                        "ADDED" -> "\u001B[32m"
                        "MODIFIED" -> "\u001B[33m"
                        "DELETED" -> "\u001B[31m"
                        else -> "\u001B[90m"
                    }
                    println("  $color[${delta.modificationType}]\u001B[0m ${delta.relativePath} -> \u001B[36m${delta.impactedLinesCount} lines\u001B[0m")
                }
            }
        } catch (e: Exception) {
            println("  \u001B[31m[Tracker Exception] Failed to query native Git streaming loops.\u001B[0m")
        }
        println("\u001B[34m╰─────────────────────────────────────────────────────────────────╯\u001B[0m\n")
    }

    private fun printHelp() {
        println("/help        Exposes command indices.")
        println("/status      Forces grid refresh frames.")
        println("/use <eng>   Dynamic route switching (groq, openai, anthropic, xai)")
        println("/exit        Safely terminates process.")
    }
}
