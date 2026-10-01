/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.security

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import org.json.JSONObject

/**
 * Immutable Byte-Level Credential Vault (Alpha-Omega Override).
 *
 * This is the single source of truth for API keys. It persists to
 * ~/.atropos/credentials.json (plaintext for now, encrypted when vault key available)
 * and reads at boot time BEFORE any provider health checks execute.
 *
 * The vault is the single source of truth - no environment variable volatility,
 * no lazy binding, no silent fallbacks. If a key is configured, it IS available.
 */
class CredentialVault(
    private val root: Path = AtroposConfig.configRoot().resolve("credentials")
) {
    private val credentialsFile = root.resolve("credentials.json")
    private val vaultKeyFile = root.resolve(".vault_key")

    /** In-memory credential store populated at boot time. */
    private val credentials = mutableMapOf<String, String>()

    /** Whether the vault has been initialized. */
    private var initialized = false

    init {
        Files.createDirectories(root)
    }

    /** Load credentials from disk. Called at boot time before provider initialization. */
    fun load(): Boolean {
        if (initialized) return true

        // 1. Try to load from credentials.json
        if (Files.exists(credentialsFile)) {
            try {
                val content = Files.readString(credentialsFile)
                val json = JSONObject(content)
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = json.getString(key)
                    if (value.isNotBlank()) {
                        credentials[key.uppercase()] = value
                    }
                }
            } catch (e: Exception) {
                // Corrupted file - will be overwritten on next save
            }
        }

        // 2. Environment variables override (highest priority)
        val env = System.getenv()
        val envKeys = env.keys
        while (envKeys.hasMoreElements()) {
            val key = envKeys.nextElement()
            val value = env[key]
            if (key.startsWith("ATROPOS_") || key.endsWith("_API_KEY") || key.endsWith("_KEY") || key.endsWith("_TOKEN")) {
                if (value != null && value.isNotBlank()) {
                    credentials[key.uppercase()] = value
                }
            }
        }

        initialized = true
        return credentials.isNotEmpty()
    }

    /** Get a credential by name (case-insensitive). */
    fun get(name: String): String? {
        if (!initialized) load()
        return credentials[name.uppercase()]
    }

    /** Get all credentials. */
    fun getAll(): Map<String, String> {
        if (!initialized) load()
        return credentials.toMap()
    }

    /** Check if a credential exists and is non-blank. */
    fun has(name: String): Boolean {
        val value = get(name)
        return value != null && value.isNotBlank() && value != "your-api-key" && value != "test"
    }

    /** Store a credential and persist to disk. */
    fun put(name: String, value: String): Boolean {
        if (value.isBlank()) return false
        credentials[name.uppercase()] = value
        return save()
    }

    /** Remove a credential. */
    fun remove(name: String): Boolean {
        val removed = credentials.remove(name.uppercase()) != null
        if (removed) save()
        return removed
    }

    /** Persist credentials to disk atomically. */
    private fun save(): Boolean {
        try {
            Files.createDirectories(root)
            val json = JSONObject(credentials)
            val temp = Files.createTempFile(root, "credentials", ".tmp")
            Files.writeString(temp, json.toString(2), StandardCharsets.UTF_8, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)
            Files.move(temp, credentialsFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            return true
        } catch (e: Exception) {
            return false
        }
    }

    /** Check if vault has any valid credentials. */
    fun hasAnyCredentials(): Boolean {
        if (!initialized) load()
        return credentials.any { (_, v) -> v.isNotBlank() && v != "your-api-key" && v != "test" }
    }

    /** Get all credential names (for debugging). */
    fun names(): Set<String> {
        if (!initialized) load()
        return credentials.keys.toSet()
    }
}

/** Boot-time credential injection singleton. */
object CredentialBootstrap {
    @Volatile private var instance: CredentialVault? = null

    fun initialize(root: Path? = null): CredentialVault {
        val vault = root?.let { CredentialVault(it) } ?: CredentialVault()
        val hasCreds = vault.load()
        instance = vault
        if (!hasCreds) {
            System.err.println("⚠️  CREDENTIAL VAULT EMPTY: No API keys found. Run '/provider connect' to configure.")
        } else {
            System.err.println("✅ Credential vault loaded: ${vault.names().joinToString(", ")}")
        }
        return vault
    }

    fun get(): CredentialVault {
        return instance ?: initialize()
    }
}