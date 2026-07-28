package atropos.core.specgraph

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Reads and validates authoritative SpecGraph JSON outputs.
 * Enforces source hash pinning, exact coordinate resolution, and typed NoMatch on absent authority.
 * Every claim cites exact source coordinates and fingerprints.
 */
class SpecGraphReader {
    private val mapper = ObjectMapper()

    /**
     * Read a SpecGraph JSON file and validate its integrity against the declared source hash.
     * @param path Path to the SpecGraph JSON file
     * @return The parsed SpecGraph document
     * @throws SpecGraphReadException if the file is missing or cannot be parsed
     * @throws SpecGraphHashMismatchException if the source hash does not match the file content
     */
    fun readSpecGraph(path: Path): SpecGraph {
        if (!Files.isRegularFile(path)) {
            throw SpecGraphReadException("SpecGraph file not found: $path")
        }
        val content = Files.readString(path)
        val graph = mapper.readValue(content, SpecGraph::class.java)
        validateSourceHash(graph, path, content)
        return graph
    }

    private fun validateSourceHash(graph: SpecGraph, path: Path, content: String) {
        val computedHash = sha256Hex(content)
        graph.sourceHash?.let { declaredHash ->
            if (declaredHash != computedHash) {
                throw SpecGraphHashMismatchException(
                    "Source hash mismatch for $path: declared=$declaredHash, computed=$computedHash"
                )
            }
        }
    }

    /**
     * Resolve a specific atom/node by exact ID.
     * Returns the atom or throws [SpecGraphResolutionException] (typed NoMatch) if not found.
     */
    fun resolveAtom(graph: SpecGraph, atomId: String): SpecGraphAtom {
        return graph.nodes.find { it.id == atomId }
            ?: throw SpecGraphResolutionException("Atom not found in SpecGraph: id=$atomId")
    }

    /**
     * Resolve a specific edge by exact ID.
     * Returns the edge or throws [SpecGraphResolutionException] (typed NoMatch) if not found.
     */
    fun resolveEdge(graph: SpecGraph, edgeId: String): SpecGraphEdge {
        return graph.edges.find { it.id == edgeId }
            ?: throw SpecGraphResolutionException("Edge not found in SpecGraph: id=$edgeId")
    }

    /**
     * Extract all atoms that match a given requirement address prefix.
     * Returns an empty list if no atoms match (never null).
     */
    fun resolveByRequirement(graph: SpecGraph, requirementAddress: String): List<SpecGraphAtom> {
        return graph.nodes.filter { it.requirementAddress == requirementAddress || it.id.startsWith(requirementAddress) }
    }

    /**
     * Return the SHA-256 hex digest of the given content string.
     */
    fun sha256Hex(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(content.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}

/**
 * A parsed SpecGraph document with source hash validation.
 */
data class SpecGraph(
    val id: String,
    val name: String? = null,
    val kind: String? = null,
    @JsonProperty("enforce_acyclic")
    val enforceAcyclic: Boolean = false,
    val nodes: List<SpecGraphAtom> = emptyList(),
    val edges: List<SpecGraphEdge> = emptyList(),
    @JsonProperty("source_hash")
    val sourceHash: String? = null
)

/**
 * A node/atom within a SpecGraph document.
 */
data class SpecGraphAtom(
    val id: String,
    val name: String? = null,
    val kind: String? = null,
    val authority: String? = null,
    @JsonProperty("requirement_address")
    val requirementAddress: String? = null,
    val metadata: Map<String, Any> = emptyMap()
)

/**
 * An edge within a SpecGraph document.
 */
data class SpecGraphEdge(
    val id: String,
    val source: String,
    val target: String,
    val relation: String? = null,
    val metadata: Map<String, Any> = emptyMap()
)

class SpecGraphReadException(message: String) : Exception(message)
class SpecGraphHashMismatchException(message: String) : Exception(message)
class SpecGraphResolutionException(message: String) : Exception(message)
