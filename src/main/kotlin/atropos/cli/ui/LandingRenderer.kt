/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

class LandingRenderer(
    private val theme: TerminalTheme
) {
    private val truthProbe = WorkbenchTruthProbe()

    fun render(state: SessionPresentationState, terminalWidth: Int): List<String> =
        render(state, terminalWidth, 32)

    fun render(state: SessionPresentationState, terminalWidth: Int, terminalHeight: Int): List<String> {
        val width = terminalWidth.coerceAtLeast(36)
        val targetHeight = terminalHeight.coerceAtLeast(12)
        val truth = truthProbe.probe(state.workspace)
        val out = mutableListOf<String>()

        out += logo(width)
        out += theme.subdued(TerminalText.ellipsize("local-first · free-first · quota-aware · truthful app-factory workbench", width))
        out += ""

        val panels = listOf(
            workspacePanel(state, truth),
            providersPanel(truth),
            routingPanel(),
            quotaPanel(),
            researchPanel(truth),
            factoryPanel(truth),
            commandsPanel(),
            indexPanel(truth),
            sourceDocsPanel(truth),
            verificationPanel(truth),
            sessionPanel(state),
            legendPanel()
        )

        out += when {
            width >= 140 -> columns(panels, 4, width)
            width >= 100 -> columns(panels, 3, width)
            width >= 60 -> columns(panels, 2, width)
            else -> panels.flatten()
        }

        out += actionRail(width)

        out += lowerPanels(state, truth, width, targetHeight - out.size)
        return out.take(targetHeight)
    }

    private fun logo(width: Int): List<String> {
        val full = listOf(
            " █████╗ ████████╗██████╗  ██████╗ ██████╗  ██████╗ ███████╗",
            "██╔══██╗╚══██╔══╝██╔══██╗██╔═══██╗██╔══██╗██╔═══██╗██╔════╝",
            "███████║   ██║   ██████╔╝██║   ██║██████╔╝██║   ██║███████╗",
            "██╔══██║   ██║   ██╔══██╗██║   ██║██╔═══╝ ██║   ██║╚════██║",
            "██║  ██║   ██║   ██║  ██║╚██████╔╝██║     ╚██████╔╝███████║",
            "╚═╝  ╚═╝   ╚═╝   ╚═╝  ╚═╝ ╚═════╝ ╚═╝      ╚═════╝ ╚══════╝"
        )
        val medium = listOf(
            " █████╗ ████████╗██████╗  ██████╗",
            "██╔══██╗╚══██╔══╝██╔══██╗██╔═══██╗",
            "███████║   ██║   ██████╔╝██║   ██║",
            "██╔══██║   ██║   ██╔══██╗██║   ██║",
            "██║  ██║   ██║   ██║  ██║╚██████╔╝",
            "╚═╝  ╚═╝   ╚═╝   ╚═╝  ╚═╝ ╚═════╝",
            "ATROPOS"
        )
        val compact = listOf("ATROPOS", "deterministic app-factory CLI")
        return (if (width >= 72) full else if (width >= 44) medium else compact).map(theme::brand)
    }

    private fun workspacePanel(state: SessionPresentationState, truth: WorkbenchTruth): List<String> =
        panel("WORKSPACE", listOf(
            row("path", TerminalText.compactPath(state.workspace)),
            row("git", state.repository.branch ?: "tracked"),
            row("kotlin", "${truth.sourceFiles} files"),
            row("mode", state.mode.lowercase()),
            row("provider", state.provider.lowercase())
        ))

    private fun providersPanel(truth: WorkbenchTruth): List<String> =
        panel("PROVIDERS", truth.providers.map { provider ->
            val status = when {
                !provider.implemented -> bad("not installed")
                provider.configured -> good("configured")
                else -> warn("missing key")
            }
            row(provider.name, "$status ${theme.subdued(provider.role)}")
        })

    private fun routingPanel(): List<String> =
        panel("ROUTING", listOf(
            row("current", "legacy route wired"),
            row("macro-a", good("policy compiled")),
            row("fast", "groq first"),
            row("large", "gemini first"),
            row("paid", bad("locked")),
            row("preview", "/route <prompt>")
        ))

    private fun quotaPanel(): List<String> =
        panel("QUOTA CORE", listOf(
            row("mode", good("free-first")),
            row("ledger", good("compiled")),
            row("cooldown", good("tracked")),
            row("fallback", good("nonblocking")),
            row("status", warn("UI next batch"))
        ))

    private fun researchPanel(truth: WorkbenchTruth): List<String> =
        panel("RESEARCH", listOf(
            row("status", if (truth.masterMap && truth.astDb) good("ready") else warn("pending index")),
            row("master", if (truth.masterMap) good("present") else bad("missing")),
            row("corpus", if (truth.lakehouseMounted) good("mounted") else warn("not mounted")),
            row("docs", "${truth.corpusFiles} detected"),
            row("ast db", if (truth.astDb) good("built") else bad("not built"))
        ))

    private fun factoryPanel(truth: WorkbenchTruth): List<String> =
        panel("APP FACTORY", listOf(
            row("planner", "descriptor route ready"),
            row("compiler", source(truth.selfImprovingLoop)),
            row("solver", source(truth.constraintSolver)),
            row("immunity", source(truth.immunityEngine)),
            row("factory", warn("orchestration next"))
        ))

    private fun commandsPanel(): List<String> =
        panel("COMMANDS", listOf(
            row("/providers", "inventory"),
            row("descriptors", "provider grid"),
            row("validate", "contract check"),
            row("/status", "endpoints"),
            row("/verify", "toolchain"),
            row("/exit", "close")
        ))

    private fun indexPanel(truth: WorkbenchTruth): List<String> =
        panel("INDEX", listOf(
            row("router", source(truth.ontologicalRouter)),
            row("indexer", source(truth.latentIndexer)),
            row("delta", source(truth.deltaTracker)),
            row("ast", if (truth.astDb) good("built") else bad("not built")),
            row("sqlite", warn("not verified"))
        ))

    private fun sourceDocsPanel(truth: WorkbenchTruth): List<String> =
        panel("SOURCE DOCS", listOf(
            row("ATROPOS", "${truth.corpusFiles} files"),
            row("map", if (truth.masterMap) good("present") else bad("missing")),
            row(".300", "provider grid"),
            row(".305", "phase plan"),
            row(".315", "workflow rules")
        ))

    private fun verificationPanel(truth: WorkbenchTruth): List<String> =
        panel("VERIFY", listOf(
            row("tests", source(truth.testsPresent)),
            row("compile", "checked by gate"),
            row("json", "raw leaks blocked"),
            row("secrets", "not rendered"),
            row("rollback", "enabled")
        ))

    private fun sessionPanel(state: SessionPresentationState): List<String> =
        panel("SESSION", listOf(
            row("operation", state.activeOperation ?: "ready"),
            row("mode", state.mode.lowercase()),
            row("provider", state.provider.lowercase()),
            row("tokens", state.tokens.text()),
            row("cost", state.cost.text())
        ))

    private fun legendPanel(): List<String> =
        panel("LEGEND", listOf(
            row(good("green"), "verified/present"),
            row(warn("amber"), "defined/pending"),
            row(bad("red"), "missing/locked"),
            row("truth", "disk-backed claims"),
            row("next", "status quota UI")
        ))

    private fun actionRail(width: Int): List<String> =
        listOf(
            "",
            theme.subdued("─".repeat(width)),
            theme.metadata("next ") + theme.code("/providers descriptors") +
                theme.metadata(" · ") + theme.code("/providers validate") +
                theme.metadata(" · ") + theme.code("/status endpoints") +
                theme.metadata(" · ") + theme.code("/verify narrow")
        )

    private fun panel(title: String, rows: List<String>): List<String> =
        listOf(theme.brand("╭─ $title")) +
            rows.map { theme.metadata("│ ") + it } +
            listOf(theme.subdued("╰" + "─".repeat(28)))

    private fun row(label: String, value: String): String =
        theme.metadata(TerminalText.padEnd(label, 11)) + " " + value

    private fun good(value: String): String = theme.success(value)
    private fun warn(value: String): String = theme.warning(value)
    private fun bad(value: String): String = theme.error(value)
    private fun source(value: Boolean): String = if (value) good("source present") else bad("missing")

    private fun columns(panels: List<List<String>>, count: Int, width: Int): List<String> {
        val columnWidth = (width / count).coerceAtLeast(28)
        val output = mutableListOf<String>()
        panels.chunked(count).forEach { group ->
            val height = group.maxOf { it.size }
            for (rowIndex in 0 until height) {
                output += group.joinToString("") { panel ->
                    TerminalText.padEnd(panel.getOrElse(rowIndex) { "" }, columnWidth)
                }.trimEnd()
            }
            output += ""
        }
        return output
    }

    private fun lowerPanels(
        state: SessionPresentationState,
        truth: WorkbenchTruth,
        width: Int,
        availableRows: Int
    ): List<String> {
        if (availableRows <= 0) return emptyList()

        val panels = listOf(
            usefulPanel(
                "RECENT COMMANDS",
                state.commands.ifEmpty {
                    listOf("/dashboard", "/status", "/providers", "/route")
                }.take(4)
            ),
            usefulPanel(
                "ROUTE TRACE",
                listOf(
                    "provider ${state.provider.lowercase()}",
                    "screen ${state.activeScreen.lowercase()}",
                    state.activeOperation ?: "operation ready"
                )
            ),
            usefulPanel(
                "PROVIDER HEALTH",
                truth.providers.take(4).map { provider ->
                    val status = when {
                        provider.configured -> "configured"
                        provider.implemented -> "missing key"
                        else -> "not installed"
                    }
                    "${provider.name} $status"
                }
            ),
            usefulPanel(
                "TAB ACTIVITY",
                listOf(
                    state.activeTab,
                    state.activeScreen,
                    "prompt preserved across redraw"
                )
            )
        )

        val rows = when {
            width < 60 -> panels.flatten()
            width < 100 -> compactColumns(panels, 2, width)
            else -> compactColumns(panels, 4, width)
        }

        return rows.take(availableRows)
    }

    private fun usefulPanel(title: String, rows: List<String>): List<String> =
        listOf(theme.brand("[" + title + "]")) +
            rows.map {
                theme.metadata("  ") + TerminalText.sanitize(it)
            }

    private fun compactColumns(
        panels: List<List<String>>,
        count: Int,
        width: Int
    ): List<String> {
        val columnWidth = (width / count).coerceAtLeast(20)
        val output = mutableListOf<String>()

        panels.chunked(count).forEach { group ->
            val height = group.maxOf { it.size }
            for (rowIndex in 0 until height) {
                output += group.joinToString("") { panel ->
                    TerminalText.padEnd(
                        TerminalText.ellipsize(
                            panel.getOrElse(rowIndex) { "" },
                            columnWidth - 1
                        ),
                        columnWidth
                    )
                }.trimEnd()
            }
        }

        return output
    }
}
