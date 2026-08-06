package atropos.cli.commands

import atropos.core.agent.WorkerCodeProposal
import atropos.core.agent.WorkerCodeProposalService

/** Routes worker proposals to the existing patch proposal owner without mutation. */
class AgentWorkerCommandHandler(
    private val proposalService: WorkerCodeProposalService,
    private val activeProviderName: () -> String,
    private val invalid: (String) -> AgentCommandOutcome.Invalid
) {
    fun propose(args: List<String>): AgentCommandOutcome {
        val request = AgentCommandParser.parseWorkerProposalRequest(args)
        if (request.workerId.isBlank() || request.territory.isEmpty() || request.task.isBlank()) {
            return invalid("usage: /agent worker propose --worker <id> --territory <path[,path...]> [--provider <name>] <task>")
        }
        val proposal = proposalService.propose(
            workerId = request.workerId,
            activeProvider = request.provider ?: activeProviderName(),
            task = request.task,
            territory = request.territory
        )
        return AgentCommandOutcome.Completed(render(proposal))
    }

    private fun render(proposal: WorkerCodeProposal): String = buildString {
        appendLine("worker proposal:")
        appendLine("  worker: ${proposal.workerId}")
        appendLine("  accepted: ${proposal.accepted}")
        appendLine("  provider: ${proposal.provider}")
        appendLine("  territory: ${proposal.territory.joinToString(",")}")
        proposal.patchId?.let { appendLine("  patch: $it") }
        proposal.proposalSha256?.let { appendLine("  proposal_sha256: $it") }
        appendLine("  mutation: not performed")
        appendLine("  reason: ${proposal.reason}")
    }.trimEnd()
}
