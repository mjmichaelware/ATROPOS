package atropos.cli.ui

import atropos.core.AtroposConfig

class AnsiTerminalEngine {
    
    fun clearScreen() {
        print("\u001B[H\u001B[2J")
        System.out.flush()
    }

    fun renderHeader() {
        println("\u001B[35m╭───────────────────────────────────────────────╮\u001B[0m")
        println("\u001B[35m│   A T R O P O S   ·   factory console           │\u001B[0m")
        println("\u001B[35m╰───────────────────────────────────────────────╯\u001B[0m")
        println("Automated Topological Repair & Ontological Optimization Swarm")
        println("v2.0.0-rc.1   ·   type /help for commands\n")
    }

    fun renderStatusMatrix(config: AtroposConfig, activeProvider: String) {
        println("\u001B[36msession\u001B[0m")
        
        val modelDisplay = when(activeProvider.lowercase()) {
            "groq" -> "llama-3.3-70b"
            "openai" -> "gpt-4o"
            "anthropic" -> "claude-3-7-sonnet"
            "xai" -> "grok-2"
            else -> "llama3.2"
        }
        
        println("├─ \u001B[90mmodel\u001B[0m    $modelDisplay")
        println("├─ \u001B[90mollama\u001B[0m   \u001B[31m● offline http://localhost:11434\u001B[0m")
        println("├─ \u001B[90mcwd\u001B[0m      ${System.getProperty("user.dir")}")
        println("└─ \u001B[90musage\u001B[0m    0 prompts · ~0 tok · est \$0.0000\n")
    }

    /**
     * Unified macro wrapper mapping to preserve interface alignment 
     * across different initialization sequences.
     */
    fun renderConsoleFrame(config: AtroposConfig, activeProvider: String) {
        renderHeader()
        renderStatusMatrix(config, activeProvider)
    }
}
