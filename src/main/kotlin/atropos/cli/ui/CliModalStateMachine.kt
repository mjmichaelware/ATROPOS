/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.input.KeyEvent
import atropos.cli.ui.QuestionMarkModalRenderer.ActiveView
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
        COST_PEEK
    }

    sealed interface ModalOutcome {
        data object None : ModalOutcome
        data object Redraw : ModalOutcome
        data object Dismiss : ModalOutcome
        data class Execute(val command: String) : ModalOutcome
    }

    var activeModal: ModalType = ModalType.NONE
        private set

    var questionActiveView: ActiveView = ActiveView.SHORTCUTS
    var questionSelectedIndex: Int = 0
    var questionIsExpanded: Boolean = false

    var trieNavState: CategorizedTrieModalRenderer.TrieNavState = CategorizedTrieModalRenderer.TrieNavState()
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

    private val questionRenderer = QuestionMarkModalRenderer(theme)
    private val trieRenderer = CategorizedTrieModalRenderer(theme)
    private val interruptSurface = InterruptModalSurface(theme)
    private val costPeekRenderer = CostPeekModalRenderer(theme)

    fun checkAutoTrigger(buffer: String): Boolean {
        val trimmed = buffer.trim()
        val prev = activeModal
        activeModal = when {
            trimmed == "?" -> ModalType.QUESTION_MARK
            trimmed == "/?" -> ModalType.CATEGORY_INFO
            trimmed == "/" -> ModalType.CATEGORIZED_TRIE
            trimmed == "+" -> ModalType.INTERRUPT
            trimmed == "$" -> ModalType.COST_PEEK
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
                            CategorizedTrieModalRenderer.DrillLevel.LEAF_DOCS ->
                                trieNavState.copy(level = CategorizedTrieModalRenderer.DrillLevel.SUBCATEGORIES_OR_ACTIONS)
                            CategorizedTrieModalRenderer.DrillLevel.SUBCATEGORIES_OR_ACTIONS ->
                                trieNavState.copy(level = CategorizedTrieModalRenderer.DrillLevel.DOMAINS, selectedIndex = 0)
                            CategorizedTrieModalRenderer.DrillLevel.DOMAINS -> trieNavState
                        }
                        return ModalOutcome.Redraw
                    }
                    ModalType.INTERRUPT -> {
                        interruptIsExpanded = false
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
                            CategorizedTrieModalRenderer.DrillLevel.DOMAINS -> {
                                val domains = listOf("agent", "governance", "memory", "repo", "session", "system", "toolchain")
                                val domain = domains.getOrElse(trieNavState.selectedIndex) { "agent" }
                                trieNavState.copy(level = CategorizedTrieModalRenderer.DrillLevel.SUBCATEGORIES_OR_ACTIONS, selectedDomain = domain, selectedIndex = 0)
                            }
                            CategorizedTrieModalRenderer.DrillLevel.SUBCATEGORIES_OR_ACTIONS ->
                                trieNavState.copy(level = CategorizedTrieModalRenderer.DrillLevel.LEAF_DOCS)
                            CategorizedTrieModalRenderer.DrillLevel.LEAF_DOCS -> trieNavState
                        }
                        return ModalOutcome.Redraw
                    }
                    ModalType.INTERRUPT -> {
                        interruptIsExpanded = !interruptIsExpanded
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
                        trieNavState = trieNavState.copy(selectedIndex = trieNavState.selectedIndex + 1)
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
                    else -> Unit
                }
            }

            KeyEvent.Enter -> {
                val outcome = when (activeModal) {
                    ModalType.QUESTION_MARK -> {
                        if (questionActiveView == ActiveView.COMMANDS) {
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
                    }
                    ModalType.CATEGORIZED_TRIE -> {
                        val domains = listOf("agent", "governance", "memory", "repo", "session", "system", "toolchain")
                        val domain = domains.getOrElse(trieNavState.selectedIndex) { "agent" }
                        ModalOutcome.Execute("/$domain")
                    }
                    ModalType.INTERRUPT -> {
                        ModalOutcome.Execute("+ ${interruptSelectedIndex + 1}")
                    }
                    else -> ModalOutcome.Dismiss
                }
                dismiss()
                return outcome
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
            ModalType.NONE -> emptyList()
        }
    }
}
