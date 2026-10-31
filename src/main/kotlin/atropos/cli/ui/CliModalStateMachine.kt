/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.input.KeyEvent
import atropos.cli.ui.QuestionMarkModalRenderer.ActiveView
import atropos.core.config.AtroposUnifiedConfig
import java.nio.file.Path

/**
 * State machine for auto-popup modals, beautiful box rendering,
 * and deterministic keyboard navigation across CLI modals.
 */
class CliModalStateMachine(
    private val theme: TerminalTheme,
    private val repoRoot: Path = Path.of(".")
) {
    enum class ModalType {
        NONE,
        QUESTION_MARK,
        CATEGORIZED_TRIE,
        CATEGORY_INFO,
        INTERRUPT,
        COST_PEEK,
        SETTINGS
    }

    sealed interface ModalOutcome {
        data object None : ModalOutcome
        data object Redraw : ModalOutcome
        data object Dismiss : ModalOutcome
        data class InsertBuffer(val command: String) : ModalOutcome
        data class Execute(val command: String) : ModalOutcome
    }

    var activeModal: ModalType = ModalType.NONE
        private set

    var questionActiveView: ActiveView = ActiveView.SHORTCUTS
    var questionSelectedIndex: Int = 0
    var questionIsExpanded: Boolean = false

    var trieNavState: CategorizedTrieModalRenderer.TrieNavState = CategorizedTrieModalRenderer.TrieNavState()
    private val canonicalCategories = listOf(
        "agent", "providers", "code", "workspace", "git", "mcp", "diagnostics", "specgraph"
    )

    private val rotatingHints = listOf(
        "Hint: Type ? anytime for shortcuts and commands cheatsheet",
        "Hint: Use @<filename> to reference workspace files",
        "Hint: Use + for live execution queue and interrupts",
        "Hint: Use $ for live token cost and quota peek",
        "Hint: Right-arrow on any command expands full progressive leaf documentation"
    )
    private var hintIndex: Int = 0

    var categorySelectedIndex: Int = 0
    var interruptSelectedIndex: Int = 0
    var interruptIsExpanded: Boolean = false
    var settingsSelectedIndex: Int = 0

    private val questionRenderer = QuestionMarkModalRenderer(theme)
    private val trieRenderer = CategorizedTrieModalRenderer(theme)
    private val interruptSurface = InterruptModalSurface(theme)
    private val costPeekRenderer = CostPeekModalRenderer(theme)
    private val settingsRenderer = SettingsModalRenderer(theme)
    private val unifiedConfig = AtroposUnifiedConfig.load()

    fun openCategorizedTrie() {
        activeModal = ModalType.CATEGORIZED_TRIE
        resetModalState()
    }

    fun checkAutoTrigger(buffer: String): Boolean {
        val trimmed = buffer.trim()
        val prev = activeModal
        activeModal = when {
            trimmed == "?" -> ModalType.QUESTION_MARK
            trimmed == "/?" -> ModalType.CATEGORY_INFO
            trimmed == "/" -> ModalType.CATEGORIZED_TRIE
            trimmed == "+" -> ModalType.INTERRUPT
            trimmed == "$" -> ModalType.COST_PEEK
            trimmed in setOf("/settings", "/config", "/preferences", "settings", "config") -> ModalType.SETTINGS
            else -> ModalType.NONE
        }
        if (activeModal != prev && activeModal != ModalType.NONE) {
            resetModalState()
            return true
        }
        return activeModal != ModalType.NONE
    }

    fun isModalActive(): Boolean = activeModal != ModalType.NONE

    fun dismiss() {
        activeModal = ModalType.NONE
        resetModalState()
    }

    private fun resetModalState() {
        questionActiveView = ActiveView.SHORTCUTS
        questionSelectedIndex = 0
        questionIsExpanded = false
        trieNavState = CategorizedTrieModalRenderer.TrieNavState()
        categorySelectedIndex = 0
        interruptSelectedIndex = 0
        interruptIsExpanded = false
        settingsSelectedIndex = 0
        hintIndex = (hintIndex + 1) % rotatingHints.size
    }

    fun handleKey(key: KeyEvent, width: Int): ModalOutcome {
        if (!isModalActive()) return ModalOutcome.None

        when (key) {
            KeyEvent.Escape -> {
                dismiss()
                return ModalOutcome.Dismiss
            }

            KeyEvent.Tab -> {
                when (activeModal) {
                    ModalType.QUESTION_MARK -> {
                        val views = ActiveView.values()
                        val nextIdx = (questionActiveView.ordinal + 1) % views.size
                        questionActiveView = views[nextIdx]
                        questionSelectedIndex = 0
                        questionIsExpanded = false
                        return ModalOutcome.Redraw
                    }
                    else -> Unit
                }
            }

            KeyEvent.ArrowLeft -> {
                when (activeModal) {
                    ModalType.QUESTION_MARK -> {
                        if (questionIsExpanded) {
                            questionIsExpanded = false
                        } else {
                            val views = ActiveView.values()
                            val prevIdx = (questionActiveView.ordinal - 1 + views.size) % views.size
                            questionActiveView = views[prevIdx]
                            questionSelectedIndex = 0
                        }
                        return ModalOutcome.Redraw
                    }
                    ModalType.CATEGORIZED_TRIE -> {
                        trieNavState = when (trieNavState.level) {
                            CategorizedTrieModalRenderer.DrillLevel.LEAF_DOCS -> {
                                trieNavState.copy(
                                    level = CategorizedTrieModalRenderer.DrillLevel.COMMANDS
                                )
                            }
                            CategorizedTrieModalRenderer.DrillLevel.COMMANDS -> {
                                trieNavState.copy(
                                    level = CategorizedTrieModalRenderer.DrillLevel.SUBCATEGORIES,
                                    selectedCommand = null,
                                    selectedIndex = 0
                                )
                            }
                            CategorizedTrieModalRenderer.DrillLevel.SUBCATEGORIES -> {
                                val catIdx = canonicalCategories.indexOf(trieNavState.selectedCategory).coerceAtLeast(0)
                                trieNavState.copy(
                                    level = CategorizedTrieModalRenderer.DrillLevel.CATEGORIES,
                                    selectedSubcategory = null,
                                    selectedCommand = null,
                                    selectedIndex = catIdx
                                )
                            }
                            CategorizedTrieModalRenderer.DrillLevel.CATEGORIES -> {
                                dismiss()
                                return ModalOutcome.Dismiss
                            }
                        }
                        return ModalOutcome.Redraw
                    }
                    ModalType.INTERRUPT -> {
                        interruptIsExpanded = false
                        return ModalOutcome.Redraw
                    }
                    ModalType.SETTINGS -> {
                        val keys = AtroposUnifiedConfig.ALLOWED_OPTIONS.keys.toList()
                        val keyToCycle = keys.getOrNull(settingsSelectedIndex)
                        if (keyToCycle != null) {
                            unifiedConfig.cycleOption(keyToCycle, forward = false)
                        }
                        return ModalOutcome.Redraw
                    }
                    else -> Unit
                }
            }

            KeyEvent.ArrowRight -> {
                when (activeModal) {
                    ModalType.QUESTION_MARK -> {
                        if (questionActiveView == ActiveView.COMMANDS) {
                            questionIsExpanded = !questionIsExpanded
                        } else {
                            val views = ActiveView.values()
                            val nextIdx = (questionActiveView.ordinal + 1) % views.size
                            questionActiveView = views[nextIdx]
                            questionSelectedIndex = 0
                        }
                        return ModalOutcome.Redraw
                    }
                    ModalType.CATEGORIZED_TRIE -> {
                        trieNavState = when (trieNavState.level) {
                            CategorizedTrieModalRenderer.DrillLevel.CATEGORIES -> {
                                val cat = canonicalCategories.getOrElse(trieNavState.selectedIndex) { "agent" }
                                trieNavState.copy(
                                    level = CategorizedTrieModalRenderer.DrillLevel.SUBCATEGORIES,
                                    selectedCategory = cat,
                                    selectedSubcategory = null,
                                    selectedCommand = null,
                                    selectedIndex = 0
                                )
                            }
                            CategorizedTrieModalRenderer.DrillLevel.SUBCATEGORIES -> {
                                val domainNode = trieRenderer.taxonomy.root[trieNavState.selectedCategory]
                                val subcategories = domainNode?.children?.keys?.toList() ?: emptyList()
                                val subcat = subcategories.getOrNull(trieNavState.selectedIndex)
                                trieNavState.copy(
                                    level = CategorizedTrieModalRenderer.DrillLevel.COMMANDS,
                                    selectedSubcategory = subcat,
                                    selectedCommand = null,
                                    selectedIndex = 0
                                )
                            }
                            CategorizedTrieModalRenderer.DrillLevel.COMMANDS -> {
                                val domainNode = trieRenderer.taxonomy.root[trieNavState.selectedCategory]
                                val cmds = if (trieNavState.selectedSubcategory != null) {
                                    domainNode?.children?.get(trieNavState.selectedSubcategory)?.children?.keys?.toList() ?: emptyList()
                                } else {
                                    domainNode?.children?.values?.flatMap { it.children.keys }?.toList() ?: emptyList()
                                }
                                val chosenCmd = cmds.getOrNull(trieNavState.selectedIndex) ?: "/goal"
                                trieNavState.copy(
                                    level = CategorizedTrieModalRenderer.DrillLevel.LEAF_DOCS,
                                    selectedCommand = chosenCmd
                                )
                            }
                            CategorizedTrieModalRenderer.DrillLevel.LEAF_DOCS -> trieNavState
                        }
                        return ModalOutcome.Redraw
                    }
                    ModalType.INTERRUPT -> {
                        interruptIsExpanded = !interruptIsExpanded
                        return ModalOutcome.Redraw
                    }
                    ModalType.SETTINGS -> {
                        val keys = AtroposUnifiedConfig.ALLOWED_OPTIONS.keys.toList()
                        val keyToCycle = keys.getOrNull(settingsSelectedIndex)
                        if (keyToCycle != null) {
                            unifiedConfig.cycleOption(keyToCycle, forward = true)
                        }
                        return ModalOutcome.Redraw
                    }
                    else -> Unit
                }
            }

            KeyEvent.ArrowUp -> {
                when (activeModal) {
                    ModalType.QUESTION_MARK -> {
                        questionSelectedIndex = (questionSelectedIndex - 1).coerceAtLeast(0)
                        return ModalOutcome.Redraw
                    }
                    ModalType.CATEGORIZED_TRIE -> {
                        trieNavState = trieNavState.copy(selectedIndex = (trieNavState.selectedIndex - 1).coerceAtLeast(0))
                        return ModalOutcome.Redraw
                    }
                    ModalType.CATEGORY_INFO -> {
                        categorySelectedIndex = (categorySelectedIndex - 1).coerceAtLeast(0)
                        return ModalOutcome.Redraw
                    }
                    ModalType.INTERRUPT -> {
                        interruptSelectedIndex = (interruptSelectedIndex - 1).coerceAtLeast(0)
                        return ModalOutcome.Redraw
                    }
                    ModalType.SETTINGS -> {
                        settingsSelectedIndex = (settingsSelectedIndex - 1).coerceAtLeast(0)
                        return ModalOutcome.Redraw
                    }
                    else -> Unit
                }
            }

            KeyEvent.ArrowDown -> {
                when (activeModal) {
                    ModalType.QUESTION_MARK -> {
                        questionSelectedIndex = questionSelectedIndex + 1
                        return ModalOutcome.Redraw
                    }
                    ModalType.CATEGORIZED_TRIE -> {
                        val maxIdx = when (trieNavState.level) {
                            CategorizedTrieModalRenderer.DrillLevel.CATEGORIES -> canonicalCategories.size - 1
                            CategorizedTrieModalRenderer.DrillLevel.SUBCATEGORIES -> {
                                val domainNode = trieRenderer.taxonomy.root[trieNavState.selectedCategory]
                                (domainNode?.children?.size ?: 1) - 1
                            }
                            CategorizedTrieModalRenderer.DrillLevel.COMMANDS -> {
                                val domainNode = trieRenderer.taxonomy.root[trieNavState.selectedCategory]
                                val count = if (trieNavState.selectedSubcategory != null) {
                                    domainNode?.children?.get(trieNavState.selectedSubcategory)?.children?.size ?: 1
                                } else {
                                    domainNode?.children?.values?.sumOf { it.children.size } ?: 1
                                }
                                count - 1
                            }
                            CategorizedTrieModalRenderer.DrillLevel.LEAF_DOCS -> 0
                        }.coerceAtLeast(0)

                        trieNavState = trieNavState.copy(selectedIndex = (trieNavState.selectedIndex + 1).coerceAtMost(maxIdx))
                        return ModalOutcome.Redraw
                    }
                    ModalType.CATEGORY_INFO -> {
                        categorySelectedIndex = categorySelectedIndex + 1
                        return ModalOutcome.Redraw
                    }
                    ModalType.INTERRUPT -> {
                        interruptSelectedIndex = (interruptSelectedIndex + 1).coerceAtMost(2)
                        return ModalOutcome.Redraw
                    }
                    ModalType.SETTINGS -> {
                        val keys = AtroposUnifiedConfig.ALLOWED_OPTIONS.keys.toList()
                        settingsSelectedIndex = (settingsSelectedIndex + 1).coerceAtMost((keys.size - 1).coerceAtLeast(0))
                        return ModalOutcome.Redraw
                    }
                    else -> Unit
                }
            }

            KeyEvent.Enter -> {
                when (activeModal) {
                    ModalType.QUESTION_MARK -> {
                        val outcome = if (questionActiveView == ActiveView.COMMANDS) {
                            val cmds = listOf(
                                "/agent", "/repo", "/memory", "/governance", "/toolchain",
                                "/session", "/system", "/verify", "/help", "/plan", "/architect",
                                "/goal", "/compact", "/batch", "/simplify", "/map", "/lint"
                            )
                            val selected = cmds.getOrNull(questionSelectedIndex)
                            if (selected != null) ModalOutcome.Execute(selected) else ModalOutcome.Dismiss
                        } else {
                            ModalOutcome.Dismiss
                        }
                        dismiss()
                        return outcome
                    }
                    ModalType.CATEGORIZED_TRIE -> {
                        when (trieNavState.level) {
                            CategorizedTrieModalRenderer.DrillLevel.CATEGORIES -> {
                                // Drill into subcategories
                                val cat = canonicalCategories.getOrElse(trieNavState.selectedIndex) { "agent" }
                                trieNavState = trieNavState.copy(
                                    level = CategorizedTrieModalRenderer.DrillLevel.SUBCATEGORIES,
                                    selectedCategory = cat,
                                    selectedIndex = 0
                                )
                                return ModalOutcome.Redraw
                            }
                            CategorizedTrieModalRenderer.DrillLevel.SUBCATEGORIES -> {
                                // Drill into commands
                                val domainNode = trieRenderer.taxonomy.root[trieNavState.selectedCategory]
                                val subcategories = domainNode?.children?.keys?.toList() ?: emptyList()
                                val subcat = subcategories.getOrNull(trieNavState.selectedIndex)
                                trieNavState = trieNavState.copy(
                                    level = CategorizedTrieModalRenderer.DrillLevel.COMMANDS,
                                    selectedSubcategory = subcat,
                                    selectedIndex = 0
                                )
                                return ModalOutcome.Redraw
                            }
                            CategorizedTrieModalRenderer.DrillLevel.COMMANDS -> {
                                // Select command -> Buffer Insertion without auto-execution (ATOM 6.5)
                                val domainNode = trieRenderer.taxonomy.root[trieNavState.selectedCategory]
                                val cmds = if (trieNavState.selectedSubcategory != null) {
                                    domainNode?.children?.get(trieNavState.selectedSubcategory)?.children?.keys?.toList() ?: emptyList()
                                } else {
                                    domainNode?.children?.values?.flatMap { it.children.keys }?.toList() ?: emptyList()
                                }
                                val selectedCmd = cmds.getOrNull(trieNavState.selectedIndex) ?: "/goal"
                                dismiss()
                                return ModalOutcome.InsertBuffer(selectedCmd)
                            }
                            CategorizedTrieModalRenderer.DrillLevel.LEAF_DOCS -> {
                                // Select command -> Buffer Insertion without auto-execution (ATOM 6.5)
                                val selectedCmd = trieNavState.selectedCommand ?: "/goal"
                                dismiss()
                                return ModalOutcome.InsertBuffer(selectedCmd)
                            }
                        }
                    }
                    ModalType.INTERRUPT -> {
                        val outcome = ModalOutcome.Execute("+ ${interruptSelectedIndex + 1}")
                        dismiss()
                        return outcome
                    }
                    ModalType.SETTINGS -> {
                        unifiedConfig.save()
                        val outcome = ModalOutcome.Execute("/config")
                        dismiss()
                        return outcome
                    }
                    else -> {
                        dismiss()
                        return ModalOutcome.Dismiss
                    }
                }
            }

            else -> Unit
        }
        return ModalOutcome.None
    }

    fun render(width: Int): List<String> {
        return when (activeModal) {
            ModalType.QUESTION_MARK -> {
                questionRenderer.render(
                    activeView = questionActiveView,
                    selectedIndex = questionSelectedIndex,
                    width = width,
                    isLeafExpanded = questionIsExpanded
                )
            }
            ModalType.CATEGORIZED_TRIE -> {
                val lines = trieRenderer.render(trieNavState, width).toMutableList()
                val currentHint = rotatingHints[hintIndex]
                lines.add(1, "│ " + theme.subdued(TerminalText.ellipsize(currentHint, width - 4).padEnd(width - 4)) + " │")
                lines
            }
            ModalType.CATEGORY_INFO -> {
                questionRenderer.renderCategoryDefinitions(
                    selectedIndex = categorySelectedIndex,
                    width = width
                )
            }
            ModalType.INTERRUPT -> {
                interruptSurface.render(
                    selectedIndex = interruptSelectedIndex,
                    isExpanded = interruptIsExpanded,
                    width = width
                )
            }
            ModalType.COST_PEEK -> {
                val metrics = CostPeekModalRenderer.CostMetrics(
                    totalTokensUsed = 0L,
                    promptTokens = 0L,
                    completionTokens = 0L,
                    providerName = "default"
                )
                costPeekRenderer.render(metrics, width)
            }
            ModalType.SETTINGS -> {
                settingsRenderer.render(
                    unifiedConfig = unifiedConfig,
                    selectedIndex = settingsSelectedIndex,
                    width = width
                )
            }
            ModalType.NONE -> emptyList()
        }
    }
}
