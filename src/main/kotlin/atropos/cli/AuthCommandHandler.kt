/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli

import atropos.cli.ui.AnsiTerminalEngine
import atropos.core.auth.AuthBootResult
import atropos.core.auth.AuthBootstrap
import atropos.core.auth.AuthCascadeResolver
import atropos.core.auth.CascadeResolution

/**
 * `/auth` — the operator's view of the governing documents.
 *
 * `SUP.AUTH.HASH-ATTEST` requires an "`atropos auth verify` CLI command that
 * reports all authority document statuses", and `SUP.AUTH.CASCADE-PRECEDENCE`
 * requires the cascade snapshot to be exposed. Both are reads.
 *
 * `accept` is the one write, and it takes a path rather than accepting
 * everything. Accepting in bulk would let a legitimate edit to one document
 * carry an illegitimate edit to another through the gate alongside it, which is
 * the whole failure the attestation exists to catch.
 */
class AuthCommandHandler(
    private val uiEngine: AnsiTerminalEngine,
    private val bootstrap: AuthBootstrap = AuthBootstrap(),
    private val resolver: AuthCascadeResolver = AuthCascadeResolver()
) {
    fun execute(tokens: List<String>): RouterOutcome {
        when (tokens.getOrNull(1)?.lowercase()) {
            null, "verify", "status" -> renderVerify()
            "cascade" -> renderCascade()
            "accept" -> renderAccept(tokens.getOrNull(2))
            else -> uiEngine.renderError("usage: /auth [verify|cascade|accept <path>]")
        }
        return RouterOutcome.CONTINUE
    }

    private fun renderVerify() {
        val statuses = bootstrap.verify().filter { it.state != "absent" }
        if (statuses.isEmpty()) {
            uiEngine.renderNotice(
                "No authority documents found. Add AGENTS.md to declare rules that survive provider drift."
            )
            return
        }
        uiEngine.renderNotice(
            buildString {
                appendLine("Authority documents")
                statuses.forEach { status ->
                    appendLine("  ${status.state.padEnd(9)} ${status.path}  ${status.sha256.take(16)}")
                }
            }.trimEnd()
        )
    }

    private fun renderCascade() {
        when (val boot = bootstrap.boot()) {
            is AuthBootResult.Refused -> uiEngine.renderError(
                "${boot.cause.reason}\n${boot.cause.remedy}"
            )

            is AuthBootResult.Booted -> {
                if (boot.layers.isEmpty()) {
                    uiEngine.renderNotice("No authority layers are declared, so nothing is cascaded.")
                    return
                }
                uiEngine.renderNotice(
                    buildString {
                        appendLine("Authority cascade")
                        resolver.snapshot(boot.layers).forEach { line ->
                            appendLine("  " + describe(line))
                        }
                    }.trimEnd()
                )
            }
        }
    }

    private fun describe(resolution: CascadeResolution): String = when (resolution) {
        is CascadeResolution.Resolved ->
            "${resolution.key} = ${resolution.value}  (${resolution.source}" +
                if (resolution.final) ", final)" else ")"

        is CascadeResolution.Violation ->
            "${resolution.key} REFUSED — ${resolution.reason}"

        is CascadeResolution.Undefined -> "${resolution.key} undefined"
    }

    private fun renderAccept(path: String?) {
        if (path.isNullOrBlank()) {
            uiEngine.renderError("usage: /auth accept <path>")
            return
        }
        if (bootstrap.accept(path)) {
            uiEngine.renderNotice("Recorded the current contents of $path as authoritative.")
        } else {
            uiEngine.renderError("Could not record $path. Check that it exists inside the repository.")
        }
    }
}
