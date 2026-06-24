package atropos

import atropos.core.AtroposConfig
import atropos.cli.CommandRouter
import atropos.cli.ui.AnsiTerminalEngine
import java.util.Scanner

fun main() {
    val uiEngine = AnsiTerminalEngine()
    uiEngine.clearScreen()

    try {
        val config = AtroposConfig.load()
        val router = CommandRouter(config)
        
        uiEngine.renderConsoleFrame(config, config.runtime.defaultProvider)

        val scanner = Scanner(System.`in`)
        while (true) {
            print("\u001B[35m› \u001B[0m")
            if (!scanner.hasNextLine()) break
            val line = scanner.nextLine()
            router.handleInput(line)
        }
        
    } catch (e: Exception) {
        println("Initialization Error: ${e.message}")
    }
}
