package atropos.core.dag

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant

internal class DagExecutionCodec(
    private val executionDefinitionsDir: Path
) {
    fun renderNode(node: DagNode): String = buildString {
        appendLine("id=${node.id}")
        appendLine("dagId=${node.dagId ?: ""}")
        appendLine("labelB64=${encode(node.label)}")
        appendLine("dependencies=${node.dependencies.joinToString(",")}")
        appendLine("territory=${node.territory.joinToString("|")}")
        appendLine("action=${node.action}")
        appendLine("actionPayloadB64=${encode(node.actionPayload.orEmpty())}")
        appendLine("expectedOutputs=${node.expectedOutputs.joinToString("|")}")
        appendLine("optionalChecks=${node.optionalChecks.joinToString("|")}")
        appendLine("maxAttempts=${node.maxAttempts}")
        appendLine("retryDelaySeconds=${node.retryDelaySeconds}")
        appendLine("state=${node.state}")
        appendLine("claimToken=${node.claimToken ?: ""}")
        appendLine("claimOwner=${node.claimOwner ?: ""}")
        appendLine("claimExpiresAt=${node.claimExpiresAt ?: ""}")
        appendLine("attempts=${node.attempts}")
        appendLine("resultB64=${encode(node.result.orEmpty())}")
        appendLine("failureReasonB64=${encode(node.failureReason.orEmpty())}")
        appendLine("childJobId=${node.childJobId ?: ""}")
        appendLine("lastMessageB64=${encode(node.lastMessage.orEmpty())}")
        appendLine("createdAt=${node.createdAt}")
        appendLine("updatedAt=${node.updatedAt}")
        appendLine("finishedAt=${node.finishedAt ?: ""}")
    }

    fun writeDefinition(definition: DagDefinition) {
        Files.createDirectories(definition.metaFile.parent)
        val tmp = Files.createTempFile(definition.metaFile.parent, definition.id, ".tmp")
        val content = buildString {
            appendLine("id=${definition.id}")
            appendLine("labelB64=${encode(definition.label)}")
            appendLine("projectId=${definition.projectId ?: ""}")
            appendLine("nodeIds=${definition.nodes.joinToString(",") { it.id }}")
            appendLine("createdAt=${definition.createdAt}")
            appendLine("updatedAt=${definition.updatedAt}")
        }
        Files.writeString(tmp, content, StandardCharsets.UTF_8)
        try {
            Files.move(tmp, definition.metaFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: Exception) {
            Files.move(tmp, definition.metaFile, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    fun readDefinition(file: Path): DagDefinition? {
        val fields = readFields(file) ?: return null
        val id = fields["id"].orEmpty()
        val nodeIds = fields["nodeIds"]?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        val nodes = nodeIds.mapNotNull { readExecutionNode(id, it) }
        return DagDefinition(
            id = id,
            label = decode(fields["labelB64"]),
            projectId = fields["projectId"]?.takeIf { it.isNotBlank() },
            nodes = nodes,
            createdAt = parseInstant(fields["createdAt"]) ?: Instant.EPOCH,
            updatedAt = parseInstant(fields["updatedAt"]) ?: Instant.EPOCH,
            metaFile = file
        )
    }

    fun readExecutionNode(dagId: String, nodeId: String): DagNode? {
        val file = executionDefinitionsDir.resolve(dagId).resolve("$nodeId.meta").normalize()
        if (!file.startsWith(executionDefinitionsDir) || !Files.isRegularFile(file)) return null
        return readNodeFile(file)
    }

    fun readNodeFile(file: Path): DagNode? {
        val fields = readFields(file) ?: return null
        return runCatching {
            DagNode(
                id = fields["id"].orEmpty(),
                dagId = fields["dagId"]?.takeIf { it.isNotBlank() },
                label = decode(fields["labelB64"]),
                dependencies = fields["dependencies"]?.split(",")?.filter { it.isNotBlank() } ?: emptyList(),
                territory = fields["territory"]?.split("|")?.filter { it.isNotBlank() } ?: emptyList(),
                action = DagNodeAction.valueOf(fields["action"].orEmpty()),
                actionPayload = decode(fields["actionPayloadB64"]).takeIf { it.isNotBlank() },
                expectedOutputs = fields["expectedOutputs"]?.split("|")?.filter { it.isNotBlank() } ?: emptyList(),
                optionalChecks = fields["optionalChecks"]?.split("|")?.filter { it.isNotBlank() }?.toSet() ?: emptySet(),
                maxAttempts = fields["maxAttempts"]?.toIntOrNull() ?: 2,
                retryDelaySeconds = fields["retryDelaySeconds"]?.toLongOrNull() ?: 15L,
                state = DagNodeState.valueOf(fields["state"].orEmpty()),
                claimToken = fields["claimToken"]?.takeIf { it.isNotBlank() },
                claimOwner = fields["claimOwner"]?.takeIf { it.isNotBlank() },
                claimExpiresAt = parseInstant(fields["claimExpiresAt"]),
                attempts = fields["attempts"]?.toIntOrNull() ?: 0,
                result = decode(fields["resultB64"]).takeIf { it.isNotBlank() },
                failureReason = decode(fields["failureReasonB64"]).takeIf { it.isNotBlank() },
                childJobId = fields["childJobId"]?.takeIf { it.isNotBlank() },
                lastMessage = decode(fields["lastMessageB64"]).takeIf { it.isNotBlank() },
                createdAt = parseInstant(fields["createdAt"]) ?: Instant.EPOCH,
                updatedAt = parseInstant(fields["updatedAt"]) ?: Instant.EPOCH,
                finishedAt = parseInstant(fields["finishedAt"]),
                metaFile = file
            )
        }.getOrNull()
    }

    private fun readFields(file: Path): Map<String, String>? =
        runCatching {
            Files.readAllLines(file, StandardCharsets.UTF_8).mapNotNull { line ->
                val index = line.indexOf('=')
                if (index <= 0) null else line.substring(0, index) to line.substring(index + 1)
            }.toMap()
        }.getOrNull()

    private fun parseInstant(value: String?): Instant? =
        value?.takeIf { it.isNotBlank() }?.let { runCatching { Instant.parse(it) }.getOrNull() }

    private fun encode(value: String): String =
        java.util.Base64.getEncoder().encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decode(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return runCatching {
            String(java.util.Base64.getDecoder().decode(value), StandardCharsets.UTF_8)
        }.getOrDefault("")
    }
}
