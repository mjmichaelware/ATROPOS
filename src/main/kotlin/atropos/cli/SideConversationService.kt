/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import atropos.cli.ui.AnsiTerminalEngine
import atropos.core.ProviderCascadeRouter
import atropos.core.thinking.Thinking
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/**
 * `/ps` — a question answered beside the work, not behind it.
 *
 * The engine runs one command at a time on purpose: commands mutate a shared
 * repository, DAG and goal store, and interleaving them would corrupt the
 * sequencing every verifier depends on. But that sequencing is about *writes*.
 * Asking a question is a read, and making an operator wait ten minutes behind
 * a self-host run to ask "what did that error mean" is a queue discipline
 * applied where it buys nothing.
 *
 * So a side conversation runs on its own thread and never enters the command
 * queue. It cannot touch the repository: it dispatches to a provider and
 * renders the answer, and that is the whole of its authority.
 *
 * ## Which provider answers
 *
 * Not the one the run is using. A provider already mid-request for a long
 * generation is the worst candidate for a quick question -- it is rate-limited
 * against itself, and a side question that slows the main work is a side
 * question that should not have been asked. The cascade is asked to skip it
 * and take the next healthy provider; if every other provider refuses, the
 * question falls back to the busy one rather than being dropped, because a
 * slow answer beats none.
 */
class SideConversationService(
    private val uiEngine: AnsiTerminalEngine,
    private val cascade: ProviderCascadeRouter,
    private val activeProvider: () -> String,
    /** What the main run is using right now, when anything is. */
    private val busyProvider: () -> String? = { null }
) : AutoCloseable {

    private val executor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "atropos-side-conversation").apply { isDaemon = true }
    }
    private val inFlight = AtomicReference<String?>(null)

    fun ask(question: String): RouterOutcome {
        val trimmed = question.trim()
        if (trimmed.isBlank()) {
            uiEngine.renderError("usage: /ps <question> — asks a second provider without joining the queue")
            return RouterOutcome.CONTINUE
        }

        // One at a time here too, but for a different reason: a side channel
        // that can be spammed into a dozen concurrent provider calls will burn
        // the operator's quota on questions they have stopped reading.
        if (!inFlight.compareAndSet(null, trimmed)) {
            uiEngine.renderError("a side question is already in flight — one at a time")
            return RouterOutcome.CONTINUE
        }

        val busy = busyProvider()
        uiEngine.renderNotice(
            "side conversation" + (if (busy == null) "" else " — $busy is busy, asking someone else")
        )

        executor.execute {
            try {
                Thinking.step("side", "asking beside the run: ${trimmed.take(80)}")
                val result = cascade.completeWithCascade(
                    requestedProvider = preferredProvider(busy),
                    prompt = trimmed,
                    context = "A side question asked while other work is running. " +
                        "Answer it directly and briefly. Do not modify any files.",
                    beforeAttempt = { candidate: String -> Thinking.detail("side", "trying $candidate") }
                )
                uiEngine.renderAssistant("${result.providerName} (side)", result.response)
            } catch (failure: Throwable) {
                uiEngine.renderError(
                    "side conversation failed (${failure.javaClass.simpleName}): " +
                        (failure.message ?: "unknown failure")
                )
            } finally {
                inFlight.set(null)
            }
        }
        return RouterOutcome.CONTINUE
    }

    /**
     * The provider to ask first.
     *
     * The active one unless the run is already using it. The cascade handles
     * the rest -- including falling back to the busy provider when nothing
     * else answers, which is why this only needs to express a preference and
     * not a policy.
     */
    private fun preferredProvider(busy: String?): String {
        val active = activeProvider()
        if (busy == null || !busy.equals(active, ignoreCase = true)) return active
        return cascade.providerOrderPreview(active).firstOrNull { !it.equals(busy, ignoreCase = true) } ?: active
    }

    override fun close() {
        executor.shutdownNow()
    }
}
