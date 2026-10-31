/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.github

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * ATOM EX1: GITHUB PULL REQUESTS AND ISSUES NATIVE WORKSPACE PANEL
 * Path: src/main/kotlin/atropos/core/github/GitHubWorkspaceCollaborationPanel.kt
 *
 * Dedicated sidebar model to inspect, browse, review, and comment on open repository
 * pull requests and issues without switching to external web browsers.
 */
data class CollaborationPullRequest(
    val number: Int,
    val title: String,
    val author: String,
    val state: String,
    val headBranch: String,
    val baseBranch: String,
    val htmlUrl: String,
    val createdAt: Instant,
    val commentsCount: Int = 0,
    val isDraft: Boolean = false
)

data class IssueCommentRecord(
    val id: Long,
    val author: String,
    val body: String,
    val createdAt: Instant
)

class GitHubWorkspaceCollaborationPanel(
    private val tokenProvider: () -> String? = { System.getenv("GITHUB_TOKEN") }
) {
    fun parsePullRequests(jsonArrayString: String): List<CollaborationPullRequest> {
        val list = mutableListOf<CollaborationPullRequest>()
        val array = try { JSONArray(jsonArrayString) } catch (_: Exception) { return emptyList() }
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val num = obj.optInt("number")
            val title = obj.optString("title")
            val user = obj.optJSONObject("user")?.optString("login") ?: "unknown"
            val state = obj.optString("state", "open")
            val head = obj.optJSONObject("head")?.optString("ref") ?: "head"
            val base = obj.optJSONObject("base")?.optString("ref") ?: "main"
            val url = obj.optString("html_url")
            val created = try { Instant.parse(obj.optString("created_at")) } catch (_: Exception) { Instant.now() }
            val comments = obj.optInt("comments", 0)
            val draft = obj.optBoolean("draft", false)

            list.add(
                CollaborationPullRequest(
                    number = num,
                    title = title,
                    author = user,
                    state = state,
                    headBranch = head,
                    baseBranch = base,
                    htmlUrl = url,
                    createdAt = created,
                    commentsCount = comments,
                    isDraft = draft
                )
            )
        }
        return list
    }

    fun buildCommentPayload(body: String): String {
        val obj = JSONObject()
        obj.put("body", body)
        return obj.toString()
    }

    companion object {
        val defaultPanel = GitHubWorkspaceCollaborationPanel()
    }
}
