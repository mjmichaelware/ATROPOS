package atropos.core.mcp

import atropos.core.security.TokenIsolationVault
import atropos.core.security.VaultReadResult
import atropos.core.security.SecretSinkKind
import java.nio.charset.StandardCharsets
import java.util.Base64

class McpVaultCryptoBinding(
    private val vault: TokenIsolationVault = TokenIsolationVault()
) {
    private val secretNamePrefix = "mcp_"

    fun storeIntegrationSecret(systemId: String, key: String, secret: String): String {
        val secretName = "$secretNamePrefix${systemId}_$key"
        vault.writeSecret(secretName, secret)
        return secretName
    }

    fun resolveIntegrationSecret(systemId: String, key: String): String {
        val secretName = "$secretNamePrefix${systemId}_$key"
        val result = vault.readSecretForSink(secretName, SecretSinkKind.TOOL_INVOCATION)

        return when (result) {
            is VaultReadResult.Available -> result.value
            is VaultReadResult.Refused -> throw SecurityException("MCP secret unavailable for $systemId/$key: ${result.reason}")
        }
    }
}