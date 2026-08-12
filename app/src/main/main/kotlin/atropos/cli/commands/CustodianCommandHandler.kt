/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.commands

import atropos.core.custodian.CustodianService

/**
 * `/custodian` — Phase 15 housekeeping.
 *
 * Both subcommands delete things, so neither is the bare-command default. An
 * operator who types `/custodian` and gets a usage line has lost nothing; one
 * who types it and gets a prune has lost snapshots.
 */
class CustodianCommandHandler(
    private val custodian: CustodianService = CustodianService()
) {
    fun handle(args: List<String>): String = when (args.firstOrNull()) {
        "clean" -> custodian.cleanTempFiles().summary
        "prune" -> custodian.pruneDeadSnapshots().summary
        else -> "usage: /custodian clean|prune"
    }
}
