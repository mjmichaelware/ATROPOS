/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge

/**
 * What running one command printed.
 *
 * Public where [BridgeCommandHandler] is internal, because this crosses the
 * boundary: `BridgeRoutes` takes a runner as a constructor parameter, so the
 * shape it returns is part of the public contract even though the handler that
 * consumes it is not.
 *
 * @param exited true when the command asked the session to end. Reported rather
 *   than acted on — a client over a port must not be able to stop the engine
 *   other clients are using.
 */
data class BridgeCommandOutput(val text: String, val exited: Boolean)
