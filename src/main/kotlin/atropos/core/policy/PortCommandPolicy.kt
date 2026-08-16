/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.policy

import atropos.cli.input.CommandRegistry

/**
 * Which commands a client reached over a port may run, and which it may never.
 *
 * The operator's complaint is fair: switching between agents because one
 * surface can do a thing and another cannot is the problem ATROPOS exists to
 * remove. The CLI accepts anything; the phone and the browser accepted almost
 * nothing, and the browser's allow-list was hand-written in TypeScript where
 * the engine could neither read it nor enforce it.
 *
 * This is that rule, owned once, in the engine. Both the bridge and the web
 * surface consult it, so a command is permitted or refused identically no
 * matter which client asked — which is what surface parity actually means.
 *
 * ## What is forbidden, and why it is a family rather than a list
 *
 * Four families reach the operating system: `/shell`, `!command`, `/cd`, and
 * anything spelled with `exec`. Over a loopback port those are remote code
 * execution against the operator's own machine — any page in their browser,
 * any app on their phone, can reach 127.0.0.1. Denial is by *prefix family*
 * rather than by exact string so a later `/shell-run` cannot be added and
 * quietly pass a list nobody remembered to update.
 *
 * Everything else the registry knows is allowed, including writes. A write is
 * not made safe by being absent from a phone — it is made safe by attribution
 * and by the gates it already passes through. The bridge requires the first;
 * the policy engine, territory grant and bounded-agency gate provide the
 * second, unchanged and unbypassed.
 *
 * ## Unregistered input is not a command
 *
 * A string the registry does not know is refused rather than passed along.
 * That is the difference between this and the `POST /cli` argv passthrough the
 * handoff proposed: passthrough treats unknown text as a shell fragment, which
 * is exactly the surface that must not exist.
 */
object PortCommandPolicy {

    /**
     * Command families no client over a port may invoke, at any depth.
     *
     * Matched as a prefix on the family word so `/shell`, `/shellexec` and a
     * future `/shell-run` are all covered by the same entry.
     */
    val FORBIDDEN_FAMILIES: Set<String> = setOf("/shell", "/cd", "/exec", "!")

    /** Substrings that mark a command as reaching the operating system. */
    private val FORBIDDEN_MARKERS: List<String> = listOf("shell", "exec", "bang")

    sealed class Verdict {
        /** The command may run. [normalized] is the form to dispatch. */
        data class Allowed(val normalized: String) : Verdict()

        data class Refused(val reason: String, val remedy: String) : Verdict()
    }

    /**
     * Decides whether [raw] may run on behalf of a client reached over a port.
     *
     * @param raw the whole command line as typed, e.g. `/factory a notes app`.
     */
    fun evaluate(raw: String): Verdict {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) {
            return Verdict.Refused(
                "An empty command is not a command.",
                "Send the command as it would be typed in the CLI, e.g. /status."
            )
        }

        // Bare natural language is not routed here. It has its own surface at
        // /v1/message, which turns it into queued work with attempt limits and
        // an evidence trail; letting it in here would give the same text two
        // different admission paths.
        if (!trimmed.startsWith("/")) {
            return Verdict.Refused(
                "Only slash commands run on this surface.",
                "Send plain language to /v1/message instead; it becomes queued work."
            )
        }

        val family = trimmed.substringBefore(' ').lowercase()

        if (FORBIDDEN_FAMILIES.any { family.startsWith(it) } ||
            FORBIDDEN_MARKERS.any { family.contains(it) }
        ) {
            return Verdict.Refused(
                "'$family' reaches the operating system and is not available over a port.",
                "Run it in the CLI, where the operator is already at the machine."
            )
        }

        if (family !in CommandRegistry.families()) {
            return Verdict.Refused(
                "'$family' is not a registered command.",
                "GET /v1/commands lists what this build accepts."
            )
        }

        if (family in CommandRegistry.unboundFamilies) {
            return Verdict.Refused(
                "'$family' is registered but not bound in this build.",
                "GET /v1/commands shows which families are wired."
            )
        }

        return Verdict.Allowed(trimmed)
    }

    /**
     * Every family a port client may invoke, for a client that wants to render
     * its own affordances rather than discover refusals by trying.
     */
    fun allowedFamilies(): List<String> =
        CommandRegistry.families()
            .filter { evaluate(it) is Verdict.Allowed }
            .sorted()
}
