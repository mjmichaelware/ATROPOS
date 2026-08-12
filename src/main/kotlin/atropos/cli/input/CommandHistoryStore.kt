package atropos.cli.input

import atropos.core.security.RedactionFilter
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Base64

/** Durable, bounded command history that preserves the PromptHistoryRing API. */
class CommandHistoryStore(
    private val historyPath: Path,
    limit: Int = 100,
    private val filter: RedactionFilter = RedactionFilter()
) : PromptHistoryRing(limit, filter) {
    private val maximumEntries = limit * PromptHistoryLane.entries.size

    init {
        require(!historyPath.toString().contains('\u0000')) { "history path contains NUL" }
        load()
    }

    override fun record(lane: PromptHistoryLane, value: String) {
        if (value.isBlank()) return
        val redacted = filter.redact(value)
        if (entries(lane).lastOrNull() == redacted) return
        super.record(lane, redacted)
        persist()
    }

    private fun load() {
        if (!Files.isRegularFile(historyPath)) return
        Files.newBufferedReader(historyPath).use { reader ->
            repeat(maximumEntries) {
                val line = reader.readLine() ?: return@use
                decode(line)?.let { (lane, value) -> super.record(lane, value) }
            }
        }
    }

    private fun persist() {
        historyPath.parent?.let(Files::createDirectories)
        val temporary = historyPath.resolveSibling(".${historyPath.fileName}.tmp")
        val lines = PromptHistoryLane.entries.flatMap { lane ->
            entries(lane).map { value ->
                lane.name + "\t" + Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))
            }
        }
        Files.write(temporary, lines)
        runCatching {
            Files.move(temporary, historyPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }.getOrElse {
            Files.move(temporary, historyPath, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun decode(line: String): Pair<PromptHistoryLane, String>? {
        val fields = line.split('\t', limit = 2)
        if (fields.size != 2) return null
        val lane = runCatching { PromptHistoryLane.valueOf(fields[0]) }.getOrNull() ?: return null
        val value = runCatching {
            String(Base64.getDecoder().decode(fields[1]), Charsets.UTF_8)
        }.getOrNull()?.takeIf(String::isNotBlank) ?: return null
        return lane to filter.redact(value)
    }
}
