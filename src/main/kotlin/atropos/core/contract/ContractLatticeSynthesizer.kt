/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.contract

import atropos.core.confidence.HysteresisConfidenceEngine
import atropos.core.planning.PartitionCluster
import atropos.core.research.ResearchDocument
import atropos.core.toolchain.NativeToolchainRegistry
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

data class ContractLattice(
    val latticeId: String,
    val interfaceFiles: Map<String, String>,
    val schemas: Map<String, String>
)

class ContractLatticeSynthesizer(
    private val toolchains: NativeToolchainRegistry = NativeToolchainRegistry
) {
    private val confidenceEngine: HysteresisConfidenceEngine = HysteresisConfidenceEngine()

    fun synthesize(
        doc: ResearchDocument,
        cluster: PartitionCluster,
        targetDir: Path
    ): ContractLattice {
        if (doc.confidence.score < confidenceEngine.thetaPause) {
            throw IllegalStateException(
                "Contract lattice synthesis rejected: confidence score ${doc.confidence.score} < theta_pause ${confidenceEngine.thetaPause}"
            )
        }

        Files.createDirectories(targetDir)
        val interfaces = mutableMapOf<String, String>()
        val schemas = mutableMapOf<String, String>()

        for (node in cluster.nodes) {
            val interfaceName = toInterfaceName(node.id)
            val interfacePath = "interfaces/$interfaceName.kt"
            val interfaceCode = generatePureInterface(interfaceName, node.territory)
            assertZeroExecutableStatements(interfaceCode)
            interfaces[interfacePath] = interfaceCode

            val schemaPath = "schemas/${node.id}.schema.json"
            val schemaContent = generateSchema(node.id, node.territory)
            schemas[schemaPath] = schemaContent

            val absInterfacePath = targetDir.resolve(interfacePath)
            Files.createDirectories(absInterfacePath.parent)
            Files.writeString(absInterfacePath, interfaceCode)

            val absSchemaPath = targetDir.resolve(schemaPath)
            Files.createDirectories(absSchemaPath.parent)
            Files.writeString(absSchemaPath, schemaContent)
        }

        val latticeHash = sha256((interfaces.keys + schemas.keys).sorted().joinToString(";"))
        return ContractLattice(
            latticeId = "lattice-$latticeHash",
            interfaceFiles = interfaces,
            schemas = schemas
        )
    }

    private fun toInterfaceName(nodeId: String): String {
        return nodeId.split(Regex("[^a-zA-Z0-9]"))
            .filter { it.isNotBlank() }
            .joinToString("") { it.replaceFirstChar { c -> c.uppercase() } }
            .let { if (it.isBlank()) "ComponentContract" else "I$it" }
    }

    private fun generatePureInterface(name: String, territory: List<String>): String {
        val territoryComment = territory.joinToString(", ")
        return """/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.generated.contracts

/**
 * Pure Wave-0 contract interface for $name.
 * Territory: $territoryComment
 * Invariant: Zero executable statements allowed.
 */
interface $name {
    fun executeContract(payload: Map<String, Any>): Map<String, Any>
    fun validatePreconditions(): Boolean
    fun validatePostconditions(): Boolean
}
"""
    }

    private fun generateSchema(nodeId: String, territory: List<String>): String {
        val territories = territory.joinToString(",") { "\"$it\"" }
        return """{
  "${'$'}schema": "https://json-schema.org/draft/2020-12/schema",
  "title": "$nodeId",
  "type": "object",
  "properties": {
    "nodeId": { "type": "string", "const": "$nodeId" },
    "territory": { "type": "array", "items": { "type": "string" }, "default": [$territories] }
  },
  "required": ["nodeId"]
}"""
    }

    private fun assertZeroExecutableStatements(code: String) {
        val prohibitedPatterns = listOf(
            Regex("""\{\s*[^}]*\bval\b"""),
            Regex("""\{\s*[^}]*\bvar\b"""),
            Regex("""\{\s*[^}]*\bprintln\b"""),
            Regex("""\{\s*[^}]*\breturn\b"""),
            Regex("""\{\s*[^}]*\bthrow\b"""),
            Regex("""=\s*run\s*\{"""),
            Regex("""\bfor\s*\("""),
            Regex("""\bwhile\s*\(""")
        )
        for (pattern in prohibitedPatterns) {
            if (pattern.containsMatchIn(code)) {
                throw IllegalStateException("Wave-0 contract purity violation: executable statements detected in contract")
            }
        }
    }

    private fun sha256(text: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
