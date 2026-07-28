package atropos.core.dag

import java.time.Instant

internal object DagAuthorityLineCodec {
    fun nodeToLine(node: DAGNode): String {
        val parts = listOf(
            node.id,
            node.requirementId,
            node.parentIds.joinToString(","),
            node.children.joinToString(","),
            node.dependencies.joinToString(","),
            node.state.name,
            node.implementationFiles.joinToString("|"),
            node.testFiles.joinToString("|"),
            node.hash
        )
        return parts.joinToString("\t") { it.replace('\t', ' ').replace('\n', ' ') }
    }

    fun lineToNode(line: String): DAGNode? {
        val parts = line.split("\t")
        if (parts.size < 9) return null
        return try {
            DAGNode(
                id = parts[0],
                requirementId = parts[1],
                parentIds = parts[2].split(",").filter { it.isNotBlank() },
                children = parts[3].split(",").filter { it.isNotBlank() },
                dependencies = parts[4].split(",").filter { it.isNotBlank() },
                state = DAGNodeState.valueOf(parts[5]),
                implementationFiles = parts[6].split("|").filter { it.isNotBlank() },
                testFiles = parts[7].split("|").filter { it.isNotBlank() },
                hash = parts[8]
            )
        } catch (_: Exception) {
            null
        }
    }

    fun docToLine(document: SourceDocument): String {
        val sections = document.sections.joinToString(";") {
            "${it.sectionId}:${it.startLine}:${it.endLine}"
        }
        return listOf(
            document.id,
            document.sha256,
            document.size.toString(),
            document.format,
            document.originalPath,
            document.ingestionTime.toString(),
            document.version.toString(),
            sections
        ).joinToString("\t")
    }

    fun lineToDoc(line: String): SourceDocument? {
        val parts = line.split("\t")
        if (parts.size < 8) return null
        return try {
            SourceDocument(
                id = parts[0],
                sha256 = parts[1],
                size = parts[2].toLong(),
                format = parts[3],
                originalPath = parts[4],
                ingestionTime = Instant.parse(parts[5]),
                version = parts[6].toInt(),
                sections = parts[7].split(";").filter { it.isNotBlank() }.map { raw ->
                    val sectionParts = raw.split(":")
                    SourceSection(
                        sectionId = sectionParts[0],
                        heading = sectionParts[0],
                        startLine = sectionParts.getOrNull(1)?.toInt() ?: 0,
                        endLine = sectionParts.getOrNull(2)?.toInt() ?: 0,
                        content = "",
                        coordinates = raw
                    )
                }
            )
        } catch (_: Exception) {
            null
        }
    }
}
