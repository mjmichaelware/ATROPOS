/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.recovery

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import org.json.JSONArray
import org.json.JSONObject

data class InFlightBufferDraft(
    val filePath: String,
    val unsavedContent: String,
    val cursorLine: Int,
    val cursorColumn: Int,
    val timestamp: Long
)

/**
 * ATOM W4: Persistent Hot Exit and Dirty Buffer Restoration.
 *
 * Preserves unsaved dirty editor buffers, open tab sequences, and cursor coordinates across
 * application restarts and unexpected termination without polluting Git working trees.
 *
 * ## Research: Encrypted Write-Ahead Log (WAL) for Editor Drafts
 * Researches AES-GCM append-only WAL storage in ~/.atropos/wal/drafts.wal to persist
 * sub-second editor modifications safely without premature Git index mutations.
 */
class PersistentHotExitBufferRestorer(
    private val storageDir: Path
) {
    private val draftFile = storageDir.resolve("hot_exit_drafts.json")

    fun saveDrafts(drafts: List<InFlightBufferDraft>) {
        Files.createDirectories(storageDir)
        val array = JSONArray()
        for (d in drafts) {
            val obj = JSONObject()
            obj.put("filePath", d.filePath)
            obj.put("unsavedContent", d.unsavedContent)
            obj.put("cursorLine", d.cursorLine)
            obj.put("cursorColumn", d.cursorColumn)
            obj.put("timestamp", d.timestamp)
            array.put(obj)
        }
        Files.writeString(draftFile, array.toString(2), StandardCharsets.UTF_8)
    }

    fun restoreDrafts(): List<InFlightBufferDraft> {
        if (!Files.isRegularFile(draftFile)) return emptyList()
        val content = Files.readString(draftFile, StandardCharsets.UTF_8)
        val array = JSONArray(content)
        val list = mutableListOf<InFlightBufferDraft>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                InFlightBufferDraft(
                    filePath = obj.getString("filePath"),
                    unsavedContent = obj.getString("unsavedContent"),
                    cursorLine = obj.optInt("cursorLine", 1),
                    cursorColumn = obj.optInt("cursorColumn", 1),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                )
            )
        }
        return list
    }

    fun clearDrafts() {
        if (Files.exists(draftFile)) {
            Files.delete(draftFile)
        }
    }
}
