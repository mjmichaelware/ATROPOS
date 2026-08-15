/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.intent

data class IntentEnvelope(
    val intentId: String,
    val command: String,
    val parameters: Map<String, String>,
    val parsedOk: Boolean
)
