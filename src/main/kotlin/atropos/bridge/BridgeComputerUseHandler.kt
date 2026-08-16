/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.bridge

import atropos.bridge.http.HttpRequest
import atropos.bridge.http.HttpResponse
import atropos.core.integration.InboundSource
import atropos.core.phase20.ComputerUseBridge

internal class BridgeComputerUseHandler(
    private val computerUseBridge: ComputerUseBridge = ComputerUseBridge()
) {
    private val delegate = BridgeInboundToolHandler(
        source = InboundSource.COMPUTER_USE,
        judge = { request ->
            computerUseBridge.judge(
                callerId = request.callerId,
                operation = request.operation,
                paths = request.paths,
                targetSurface = request.targetSurface.orEmpty(),
                territoryGrantId = request.territoryGrantId.orEmpty()
            )
        },
        surfaceName = "computer-use"
    )

    fun judge(request: HttpRequest): HttpResponse = delegate.judge(request)
}
