package atropos.core.security

import java.io.File

data class SecretLookup(
    val name: String,
    val value: String?,
    val source: String,
    val configured: Boolean
) {
    fun redacted(): String = if (configured) "$name=<configured:$source>" else "$name=<missing>"
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

class LocalFileSecretSource(private val root: File = File(".atropos/secrets")) : SecretSource {
    override fun lookup(name: String): SecretLookup {
        val safeName = name.replace(Regex("[^A-Za-z0-9_.-]"), "_")
        val file = File(root, "$safeName.secret")
        val value = if (file.isFile) file.readText().trim().takeIf { it.isNotBlank() } else null
        return SecretLookup(name, value, "local_file", value != null)
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
        localRoot: File = File(".atropos/secrets")
    ): CompositeSecretSource = CompositeSecretSource(
        listOf(
            MapSecretSource(explicit, "explicit"),
            EnvSecretSource(env),
            LocalFileSecretSource(localRoot)
        )
    )
}
