/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.github

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * ATOM CC10: GITHUB PULL REQUEST CREATION ROUTER
 * Path: src/main/kotlin/atropos/core/github/GitHubPullRequestCreationRouter.kt
 *
 * Routes operators between creating Pull Requests natively inside the IDE or
 * redirecting to the official GitHub web interface with pre-filled branch, title,
 * and body parameters.
 */
data class PullRequestCreationRequest(
    val repoOwner: String,
    val repoName: String,
    val headBranch: String,
    val baseBranch: String = "main",
    val title: String,
    val body: String,
    val isDraft: Boolean = false
)

sealed class PullRequestRoutingDestination {
    data class WebRedirect(val webUrl: String) : PullRequestRoutingDestination()
    data class NativeApiDispatch(val endpointUrl: String, val payloadJson: String) : PullRequestRoutingDestination()
}

class GitHubPullRequestCreationRouter(
    private val createOnGithubWeb: Boolean = false
) {
    fun routePullRequestCreation(request: PullRequestCreationRequest): PullRequestRoutingDestination {
        return if (createOnGithubWeb) {
            val encodedTitle = URLEncoder.encode(request.title, StandardCharsets.UTF_8)
            val encodedBody = URLEncoder.encode(request.body, StandardCharsets.UTF_8)
            val url = "https://github.com/${request.repoOwner}/${request.repoName}/compare/" +
                "${request.baseBranch}...${request.headBranch}?expand=1&title=$encodedTitle&body=$encodedBody"
            PullRequestRoutingDestination.WebRedirect(url)
        } else {
            val endpoint = "https://api.github.com/repos/${request.repoOwner}/${request.repoName}/pulls"
            val json = """
                {
                  "title": "${escapeJson(request.title)}",
                  "head": "${escapeJson(request.headBranch)}",
                  "base": "${escapeJson(request.baseBranch)}",
                  "body": "${escapeJson(request.body)}",
                  "draft": ${request.isDraft}
                }
            """.trimIndent()
            PullRequestRoutingDestination.NativeApiDispatch(endpoint, json)
        }
    }

    private fun escapeJson(str: String): String =
        str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")

    companion object {
        val defaultRouter = GitHubPullRequestCreationRouter()
    }
}
