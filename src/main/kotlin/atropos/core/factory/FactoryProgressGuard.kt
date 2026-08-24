package atropos.core.factory

import atropos.core.dag.DagNodeState
import atropos.core.dag.DagStore
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Base64

data class FactoryProgressDecision(
    val allowed: Boolean,
    val reason: String,
    val evidenceSha256: String
)

/** Detects repeated failure signatures without creating a second retry system. */
class FactoryProgressGuard(
    private val dagStore: DagStore,
    private val identicalFailureLimit: Int = 3
) {
    private val failures = mutableMapOf<Pair<String, String>, Int>()
    private val fileHistory = mutableMapOf<String, ArrayDeque<String>>()
    private val statePath: Path = dagStore.rootPath().resolve(".atropos/factory/progress-guard.tsv")

    init {
        require(identicalFailureLimit > 0) { "failure limit must be positive" }
        loadState()
    }

    @Synchronized
    fun observeFailure(dagId: String, atomId: String, rawFailure: String): FactoryProgressDecision {
        val signature = FactoryLineage.sha256(normalize(rawFailure))
        val key = atomId to signature
        val count = (failures[key] ?: 0) + 1
        failures[key] = count
        persistState()
        if (count < identicalFailureLimit) {
            return FactoryProgressDecision(true, "failure_signature_count=$count", signature)
        }

        val node = dagStore.readDag(dagId)?.findNode(atomId)
        if (node != null && !node.state.terminal) {
            dagStore.writeNode(
                node.copy(
                    state = DagNodeState.BLOCKED,
                    failureReason = "thrash: identical failure signature $signature repeated $count times",
                    lastMessage = "factory progress guard blocked repeated failure"
                )
            )
        }
        return FactoryProgressDecision(
            allowed = false,
            reason = "thrash detected for atom=$atomId repeated_failure_signature=$signature count=$count",
            evidenceSha256 = signature
        )
    }

    @Synchronized
    fun observeWrite(atomId: String, fileHashes: List<String>): FactoryProgressDecision {
        val fingerprint = FactoryLineage.sha256(fileHashes.sorted().joinToString("\n"))
        val history = fileHistory.getOrPut(atomId) { ArrayDeque() }
        history.addLast(fingerprint)
        while (history.size > 3) history.removeFirst()
        persistState()
        val oscillating = history.size == 3 && history.elementAt(0) == history.elementAt(2) && history.elementAt(0) != history.elementAt(1)
        return FactoryProgressDecision(
            allowed = !oscillating,
            reason = if (oscillating) "file hash oscillation detected for atom=$atomId" else "file fingerprint recorded",
            evidenceSha256 = fingerprint
        )
    }

    fun observeWriteOrThrow(atomId: String, fileHashes: List<String>): FactoryProgressDecision {
        val decision = observeWrite(atomId, fileHashes)
        check(decision.allowed) {
            "factory write blocked: ${decision.reason}; evidence_sha256=${decision.evidenceSha256}"
        }
        return decision
    }

    private fun normalize(rawFailure: String): String = rawFailure
        .replace(Regex("\\s+"), " ")
        .replace(Regex("\\d+"), "#")
        .trim()

    private fun loadState() {
        if (!Files.isRegularFile(statePath)) return
        runCatching {
            Files.readAllLines(statePath, StandardCharsets.UTF_8).forEach { line ->
                val fields = line.split('\t')
                when (fields.firstOrNull()) {
                    "F" -> if (fields.size == 4) {
                        failures[decode(fields[1]) to decode(fields[2])] = fields[3].toInt()
                    }
                    "W" -> if (fields.size == 3) {
                        fileHistory[decode(fields[1])] = ArrayDeque(
                            fields[2].split(',').filter(String::isNotBlank).map(::decode)
                        )
                    }
                }
            }
        }
    }

    private fun persistState() {
        try {
            Files.createDirectories(statePath.parent)
            val content = buildString {
                failures.toSortedMap(compareBy<Pair<String, String>> { it.first }.thenBy { it.second })
                    .forEach { (key, count) ->
                        append("F\t${encode(key.first)}\t${encode(key.second)}\t$count\n")
                    }
                fileHistory.toSortedMap().forEach { (atom, history) ->
                    append("W\t${encode(atom)}\t${history.joinToString(",", transform = ::encode)}\n")
                }
            }
            val temp = Files.createTempFile(statePath.parent, "progress-guard-", ".tmp")
            Files.writeString(temp, content, StandardCharsets.UTF_8)
            try {
                Files.move(temp, statePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Exception) {
                Files.move(temp, statePath, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (failure: Exception) {
            throw IllegalStateException("factory progress guard state could not be persisted", failure)
        }
    }

    private fun encode(value: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(value.toByteArray(StandardCharsets.UTF_8))

    private fun decode(value: String): String = String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8)
}
