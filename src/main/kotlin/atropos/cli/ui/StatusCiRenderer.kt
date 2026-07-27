package atropos.cli.ui

import atropos.core.execution.LocalWorkQueue

class StatusCiRenderer(
    private val queue: LocalWorkQueue = LocalWorkQueue()
) {
    fun render(): String {
        val status = queue.status()
        return buildString {
            appendLine("ci/edge execution:")
            appendLine("  local process queue: ready")
            appendLine("  queue root: ${status.root.path}")
            appendLine("  jobs: total=${status.total} queued=${status.queued} running=${status.running} succeeded=${status.succeeded} failed=${status.failed}")
            appendLine("  github actions: ${if (status.githubActionsConfigured) "configured optional" else "optional/off"}")
            appendLine("  cloudflare workers: ${if (status.cloudflareConfigured) "configured optional" else "optional/off"}")
            appendLine("  supabase edge: ${if (status.supabaseEdgeConfigured) "configured optional" else "optional/off"}")
            appendLine("  google cloud: ${if (status.googleCloudConfigured) "configured optional" else "optional/off"}")
            appendLine("  policy: local compile first; remote CI is reproducibility only")
        }
    }
}
