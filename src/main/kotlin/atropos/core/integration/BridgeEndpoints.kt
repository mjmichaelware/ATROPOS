/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.integration

import java.util.concurrent.ConcurrentHashMap

data class Project(val id: String, val name: String, val owner: String)

class BridgeEndpoints {
    private val projects = ConcurrentHashMap<String, Project>()
    private val approvalLog = mutableListOf<String>()
    private val queueFaults = mutableListOf<String>()

    fun createProject(id: String, name: String, owner: String): Project {
        val p = Project(id, name, owner)
        projects[id] = p
        return p
    }

    fun getProject(id: String): Project? = projects[id]

    fun executeCli(args: List<String>): String {
        // RCE fix: validate arguments before executing CLI command over bridge
        val blocked = listOf(";", "&&", "||", "|", "`", "$")
        for (arg in args) {
            if (blocked.any { arg.contains(it) }) {
                throw IllegalArgumentException("Security violation: blocked character in CLI arguments")
            }
        }
        return "EXECUTED: ${args.joinToString(" ")}"
    }

    fun recordApproval(verdict: String) {
        // Approvals never erase history
        approvalLog.add(verdict)
    }

    fun getApprovalHistory(): List<String> = approvalLog.toList()

    fun reportQueueFault(fault: String) {
        queueFaults.add(fault)
    }

    fun getQueueFaults(): List<String> = queueFaults.toList()
}

data class PipelineField(
    val stage: String,
    val description: String,
    val howDescription: String // "How?" pipeline field
)
