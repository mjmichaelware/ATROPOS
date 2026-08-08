package atropos.core.provider

import atropos.core.provider.adapter.AdapterRequest
import atropos.core.provider.adapter.AssetProviderCatalog
import atropos.core.provider.adapter.AssetProviderFixtures
import atropos.core.provider.adapter.DataInfraKernelFixtures
import atropos.core.provider.adapter.DataInfraResearchProviderCatalog
import atropos.core.provider.adapter.NonOpenAiFreeProviderCatalog
import atropos.core.provider.adapter.NonOpenAiKernelFixtures
import atropos.core.provider.adapter.OpenAiCompatibleProviderCatalog
import atropos.core.provider.adapter.ProviderAdapterRegistry
import atropos.core.provider.adapter.StaticProviderAdapterRegistry
import java.util.Locale

class ProviderFixtureMatrixService(
    private val registry: ProviderDescriptorRegistry = StaticProviderDescriptorRegistry(),
    private val adapterRegistry: ProviderAdapterRegistry = StaticProviderAdapterRegistry(registry),
    private val normalizer: ProviderErrorNormalizer = ProviderErrorNormalizer()
) {
    fun runProvider(providerId: String): ProviderFixtureMatrixRecord {
        val descriptor = registry.getById(providerId)
            ?: return ProviderFixtureMatrixRecord(providerId, false, 0, 1, listOf("missing descriptor"))
        val adapter = adapterRegistry.getByProviderId(providerId)
        val lines = mutableListOf<Pair<String, Boolean>>()

        familyFixtures(providerId).forEach { result ->
            lines += normalizeName(result.fixture) to result.passed
        }

        val dryRunTask = probeTask(descriptor)
        val dryRun = adapter?.complete(
            AdapterRequest(
                task = dryRunTask,
                prompt = "fixture dry run for $providerId",
                dryRun = true,
                liveNetworkAllowed = false
            )
        ) ?: localFixtureResult(descriptor, dryRunTask, "local dry run")
        lines += "dry_run" to (
            dryRun is ProviderCallResult.Success ||
                dryRun is ProviderCallResult.LocalOnly ||
                dryRun is ProviderCallResult.Queued
            )
        lines += "quota_exhausted" to (
            normalizer.normalize(providerId, "quota exhausted").type == NormalizedProviderFailureType.QUOTA_EXHAUSTED
            )
        lines += "error" to (
            normalizer.normalize(providerId, "provider internal error").type == NormalizedProviderFailureType.INTERNAL
            )
        lines += "auth_failed" to (
            normalizer.normalize(providerId, "401 unauthorized invalid api key").type == NormalizedProviderFailureType.AUTH_FAILED
            )
        lines += "rate_limited" to (
            normalizer.normalize(providerId, "rate limit exceeded").type == NormalizedProviderFailureType.RATE_LIMITED
            )
        lines += "billing_required" to (
            normalizer.normalize(providerId, "billing required").type == NormalizedProviderFailureType.BILLING_REQUIRED
            )
        lines += "unavailable" to (
            normalizer.normalize(providerId, "connection refused").type == NormalizedProviderFailureType.UNAVAILABLE
            )
        lines += "timeout" to (
            normalizer.normalize(providerId, "request timed out").type == NormalizedProviderFailureType.TIMEOUT
            )
        lines += "malformed_response" to (
            normalizer.normalize(providerId, "invalid json malformed response").type == NormalizedProviderFailureType.MALFORMED_RESPONSE
            )
        lines += "empty_response" to (
            normalizer.normalize(providerId, "").type == NormalizedProviderFailureType.EMPTY_RESPONSE
            )
        lines += "cancellation" to (
            normalizer.normalize(providerId, "request cancelled by caller").type == NormalizedProviderFailureType.CANCELLED
            )
        lines += "redaction" to runRedactionFixture(providerId)
        lines += "attestation" to runAttestationFixture(providerId)

        // A provider that belongs to no family catalog — `local` is the current
        // case — got every failure fixture but no success fixture, because
        // `success` is only ever contributed by familyFixtures. The matrix then
        // reported it as covered while silently omitting the one case that proves
        // the adapter can actually answer. Every registered provider gets a success
        // fixture; a provider with no adapter fails it rather than skipping it.
        if (lines.none { it.first == "success" }) {
            val successTask = probeTask(descriptor)
            val offlineSuccess = adapter?.complete(
                AdapterRequest(
                    task = successTask,
                    prompt = "fixture success for $providerId",
                    dryRun = true,
                    liveNetworkAllowed = false
                )
            ) ?: localFixtureResult(descriptor, successTask, "local fixture success")
            lines += "success" to (
                offlineSuccess is ProviderCallResult.Success ||
                    offlineSuccess is ProviderCallResult.LocalOnly ||
                    offlineSuccess is ProviderCallResult.Queued
            )
        }

        val distinct = linkedMapOf<String, Boolean>()
        lines.forEach { (name, passed) -> distinct[name] = distinct[name] ?: passed }
        val detail = distinct.entries.sortedBy { it.key }.map { "${it.key}=${if (it.value) "PASS" else "FAIL"}" }
        val required = setOf("success", "error", "malformed_response", "empty_response", "timeout", "redaction")
        return ProviderFixtureMatrixRecord(
            providerId = providerId,
            passed = required.all { distinct[it] == true } && distinct.values.all { it },
            passedCount = distinct.values.count { it },
            totalCount = distinct.size,
            details = detail
        )
    }

    fun runAll(): List<ProviderFixtureMatrixRecord> =
        registry.getAll().sortedBy { it.id }.map { runProvider(it.id) }

    private fun familyFixtures(providerId: String) =
        when {
            OpenAiCompatibleProviderCatalog.get(providerId) != null -> atropos.core.provider.adapter.AdapterKernelFixtures.runAll(providerId)
            NonOpenAiFreeProviderCatalog.get(providerId) != null -> NonOpenAiKernelFixtures.runAll(providerId)
            DataInfraResearchProviderCatalog.get(providerId) != null -> DataInfraKernelFixtures.runAll(providerId)
            AssetProviderCatalog.get(providerId) != null -> AssetProviderFixtures.runAll(providerId)
            else -> emptyList()
        }

    private fun normalizeName(name: String): String =
        when (name.lowercase(Locale.US)) {
            "provider_error_auth" -> "auth_failed"
            "provider_error_rate_limit" -> "rate_limited"
            "provider_error_billing" -> "billing_required"
            "malformed" -> "malformed_response"
            "empty" -> "empty_response"
            "cancelled" -> "cancellation"
            else -> name
        }

    private fun probeTask(descriptor: ProviderDescriptor): ProviderTask =
        when {
            AssetProviderCatalog.get(descriptor.id) != null ->
                ProviderTask(ProviderTaskKind.ASSET_GENERATION, ApiCapability.ASSET, "fixture asset")
            DataInfraResearchProviderCatalog.get(descriptor.id)?.schema == atropos.core.provider.adapter.DataInfraProviderSchema.JINA_READER ->
                ProviderTask(ProviderTaskKind.WEB_DOCS_LOOKUP, ApiCapability.READER, "https://example.com")
            DataInfraResearchProviderCatalog.get(descriptor.id)?.schema == atropos.core.provider.adapter.DataInfraProviderSchema.SERPAPI_WEB ->
                ProviderTask(ProviderTaskKind.WEB_DOCS_LOOKUP, ApiCapability.WEB, "example lookup")
            DataInfraResearchProviderCatalog.get(descriptor.id) != null ->
                ProviderTask(ProviderTaskKind.DATABASE_STATE, descriptor.capabilities.first(), "data fixture")
            NonOpenAiFreeProviderCatalog.get(descriptor.id)?.schema == atropos.core.provider.adapter.NonOpenAiProviderSchema.CLOUDFLARE_WORKERS ->
                ProviderTask(ProviderTaskKind.EDGE_WORKER, ApiCapability.EDGE, "edge fixture")
            descriptor.hasCapability(ApiCapability.CHAT) -> ProviderTask(ProviderTaskKind.CHAT_PROMPT, ApiCapability.CHAT, "hello")
            descriptor.hasCapability(ApiCapability.CODE) -> ProviderTask(ProviderTaskKind.FAST_CODE_DRAFT, ApiCapability.CODE, "fun hi() = 1")
            descriptor.hasCapability(ApiCapability.READER) -> ProviderTask(ProviderTaskKind.WEB_DOCS_LOOKUP, ApiCapability.READER, "https://example.com")
            descriptor.hasCapability(ApiCapability.WEB) -> ProviderTask(ProviderTaskKind.WEB_DOCS_LOOKUP, ApiCapability.WEB, "example lookup")
            descriptor.hasCapability(ApiCapability.ASSET) -> ProviderTask(ProviderTaskKind.ASSET_GENERATION, ApiCapability.ASSET, "fixture asset")
            descriptor.hasCapability(ApiCapability.VECTOR_DB) -> ProviderTask(ProviderTaskKind.VECTOR_MEMORY, ApiCapability.VECTOR_DB, "vector fixture")
            descriptor.hasCapability(ApiCapability.DATABASE) -> ProviderTask(ProviderTaskKind.DATABASE_STATE, ApiCapability.DATABASE, "db fixture")
            descriptor.hasCapability(ApiCapability.EDGE) -> ProviderTask(ProviderTaskKind.EDGE_WORKER, ApiCapability.EDGE, "edge fixture")
            descriptor.hasCapability(ApiCapability.STORAGE) -> ProviderTask(ProviderTaskKind.DATABASE_STATE, ApiCapability.STORAGE, "storage fixture")
            descriptor.hasCapability(ApiCapability.SECRET) -> ProviderTask(ProviderTaskKind.SECRET_STORAGE, ApiCapability.SECRET, "secret fixture")
            else -> ProviderTask(ProviderTaskKind.LOCAL_ONLY, ApiCapability.LOCAL_TOOL, "local fixture")
        }

    private fun localFixtureResult(
        descriptor: ProviderDescriptor,
        task: ProviderTask,
        content: String
    ): ProviderCallResult? =
        if (descriptor.isLocal) ProviderCallResult.LocalOnly(task, content) else null

    private fun runRedactionFixture(providerId: String): Boolean {
        val raw = "Authorization: Bearer " + "A".repeat(24) + " sk-" + "B".repeat(24) + " api_key=sk-" + "C".repeat(24)
        val failure = normalizer.normalize(providerId, raw)
        return !failure.cleanSummary.contains("A".repeat(24)) &&
            !failure.cleanSummary.contains("B".repeat(24)) &&
            !failure.cleanSummary.contains("C".repeat(24)) &&
            failure.cleanSummary.contains("<redacted")
    }

    private fun runAttestationFixture(providerId: String): Boolean {
        val block = ContextEnvelopeSerializer.attestationBlock(
            systemIdentity = "ATROPOS",
            repository = "ATROPOS",
            taskOrNodeId = "fixture-node",
            role = "worker",
            contextVersion = "1.0",
            contextHash = "fixture-hash-$providerId"
        )
        val parsed = ContextEnvelopeSerializer.parseAttestation("fixture response\n$block")
        return parsed?.systemIdentity == "ATROPOS" &&
            parsed.repository == "ATROPOS" &&
            parsed.taskOrNodeId == "fixture-node" &&
            parsed.contextHash == "fixture-hash-$providerId"
    }

    fun listAdaptersMissingNormalizedFixtures(): List<String> {
        val knownIds = registry.getAll().map { it.id }.toSet()
        val specIds = OpenAiCompatibleProviderCatalog.all().map { it.providerId }.toSet() +
            NonOpenAiFreeProviderCatalog.all().map { it.providerId }.toSet() +
            DataInfraResearchProviderCatalog.all().map { it.providerId }.toSet() +
            AssetProviderCatalog.all().map { it.providerId }.toSet() +
            setOf("local", "ollama")
        return (knownIds - specIds).toList().sorted()
    }
}
