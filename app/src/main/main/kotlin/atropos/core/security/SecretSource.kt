package atropos.core.security

import atropos.core.AtroposRepoRootLocator
import java.io.File

data class SecretLookup(
    val name: String,
    val value: String?,
    val source: String,
    val configured: Boolean
) {
    fun redacted(): String = if (configured) "$name=<configured:$source>" else "$name=<missing>"

    override fun toString(): String = redacted()
}

interface SecretSource {
    fun lookup(name: String): SecretLookup
}

class MapSecretSource(private val values: Map<String, String>, private val label: String = "explicit") : SecretSource {
    override fun lookup(name: String): SecretLookup {
        val value = values[name]?.takeIf { it.isNotBlank() }
        return SecretLookup(name, value, label, value != null)
    }
}

class EnvSecretSource(private val env: Map<String, String> = System.getenv()) : SecretSource {
    override fun lookup(name: String): SecretLookup {
        val value = env[name]?.takeIf { it.isNotBlank() }
        return SecretLookup(name, value, "environment", value != null)
    }
}

class LocalFileSecretSource(
    private val root: File = AtroposRepoRootLocator.resolve().resolve(".atropos/secrets").toFile()
) : SecretSource {
    private val vault = TokenIsolationVault(root.toPath())

    override fun lookup(name: String): SecretLookup {
        val result = runCatching { vault.readSecretResult(name) }
            .getOrElse { return SecretLookup(name, null, "local_file_refused_io", false) }
        return when (result) {
            is VaultReadResult.Available -> SecretLookup(name, result.value, "local_file", true)
            is VaultReadResult.Refused -> SecretLookup(
                name,
                null,
                "local_file_refused_${result.reason.name.lowercase()}",
                false
            )
        }
    }
}

class CompositeSecretSource(private val sources: List<SecretSource>) : SecretSource {
    override fun lookup(name: String): SecretLookup {
        for (source in sources) {
            val hit = source.lookup(name)
            if (hit.configured) return hit
        }
        return SecretLookup(name, null, "missing", false)
    }

    fun status(names: List<String>): SecretSourceStatus {
        val lookups = names.distinct().map(::lookup)
        return SecretSourceStatus(
            requested = lookups.size,
            configured = lookups.count { it.configured },
            missing = lookups.filterNot { it.configured }.map { it.name },
            redactedLines = lookups.map { it.redacted() }
        )
    }
}

data class SecretSourceStatus(
    val requested: Int,
    val configured: Int,
    val missing: List<String>,
    val redactedLines: List<String>
)

object DefaultSecretSource {
    fun create(
        explicit: Map<String, String> = emptyMap(),
        env: Map<String, String> = System.getenv(),
        localRoot: File = AtroposRepoRootLocator.resolve().resolve(".atropos/secrets").toFile()
    ): CompositeSecretSource = CompositeSecretSource(
        listOf(
            MapSecretSource(explicit, "explicit"),
            EnvSecretSource(env),
            LocalFileSecretSource(localRoot)
        )
    )
}


data class KeySetupResult(
    val root: File,
    val template: File,
    val readme: File,
    val names: List<String>
)

class KeySetupHelper(
    private val root: File = AtroposRepoRootLocator.resolve().resolve(".atropos/secrets").toFile()
) {
    private val vault = TokenIsolationVault(root.toPath())

    fun setup(names: List<String> = defaultNames()): KeySetupResult {
        val rootPath = vault.rootPath()
        val rootDir = rootPath.toFile()
        val template = rootPath.resolve("secrets.template").toFile()
        val readme = rootPath.resolve("README.txt").toFile()
        val distinct = names.map { it.trim() }.filter { it.isNotBlank() }.distinct().sorted()

        template.writeText(distinct.joinToString("\n") { "$it=" } + "\n")

        readme.writeText(
            buildString {
                appendLine("ATROPOS local secret setup")
                appendLine("Copy secrets.template to local secrets outside git tracking when needed.")
                appendLine("Never commit real keys.")
                appendLine("Current secret source precedence: explicit > environment > local_file.")
                appendLine("Configured values are displayed only as <configured:source>.")
            }
        )
        template.setReadable(true, true)
        template.setWritable(true, true)
        readme.setReadable(true, true)
        readme.setWritable(true, true)

        return KeySetupResult(rootDir, template, readme, distinct)
    }

    companion object {
        fun defaultNames(): List<String> = listOf(
            "GROQ_API_KEY",
            "OPENROUTER_API_KEY",
            "GEMINI_API_KEY",
            "GITHUB_MODELS_TOKEN",
            "CLOUDFLARE_API_TOKEN",
            "CLOUDFLARE_ACCOUNT_ID",
            "JINA_API_KEY",
            "SERPAPI_API_KEY",
            "SAMBANOVA_API_KEY",
            "CEREBRAS_API_KEY",
            "NVIDIA_API_KEY",
            "DEEPINFRA_API_KEY",
            "HUGGINGFACE_API_KEY",
            "SILICONFLOW_API_KEY",
            "SUPABASE_URL",
            "SUPABASE_ANON_KEY",
            "PINECONE_API_KEY",
            "GITHUB_TOKEN",
            "GOOGLE_APPLICATION_CREDENTIALS"
        )
    }
}
