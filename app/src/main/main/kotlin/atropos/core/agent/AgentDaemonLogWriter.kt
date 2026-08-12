package atropos.core.agent

import atropos.core.security.RedactionFilter
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/** Drains daemon output without persisting raw provider or credential text. */
class AgentDaemonLogWriter(
    private val redactionFilter: RedactionFilter = RedactionFilter(),
    private val maxLineBytes: Int = 8 * 1024,
    private val maxFileBytes: Long = 2L * 1024L * 1024L
) {
    fun attach(process: Process, logFile: Path): Thread {
        val writerThread = Thread({ drain(process.inputStream.bufferedReader(), logFile) }, "atropos-daemon-log-writer")
        writerThread.isDaemon = true
        writerThread.start()
        return writerThread
    }

    private fun drain(reader: BufferedReader, logFile: Path) {
        try {
            Files.createDirectories(logFile.parent)
            if (Files.notExists(logFile)) Files.createFile(logFile)
            Files.newBufferedWriter(
                logFile,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND
            ).use { writer ->
                var written = Files.size(logFile)
                while (true) {
                    val line = reader.readLine() ?: break
                    if (written >= maxFileBytes) continue
                    val safe = redactionFilter.redact(line).take(maxLineBytes)
                    val encoded = (safe + System.lineSeparator()).toByteArray(StandardCharsets.UTF_8)
                    val remaining = (maxFileBytes - written).coerceAtLeast(0L)
                    if (encoded.size.toLong() > remaining) {
                        writer.write("<daemon log limit reached>")
                        writer.newLine()
                        writer.flush()
                        written = maxFileBytes
                    } else {
                        writer.write(safe)
                        writer.newLine()
                        writer.flush()
                        written += encoded.size
                    }
                }
            }
        } catch (_: IOException) {
            // Daemon lifecycle must not be changed by an unavailable log sink.
        }
    }
}
