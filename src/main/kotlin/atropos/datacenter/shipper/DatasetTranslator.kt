package atropos.datacenter.shipper

/**
 * Pure function that converts internal records into Parquet, JSONL, or Arrow format.
 * Zero side effects.
 */
object DatasetTranslator {
    fun toParquetRow(record: Map<String, Any?>): Map<String, Any?> {
        return record.toMap()
    }

    fun toJsonl(record: Map<String, Any?>): String {
        return record.entries.joinToString(prefix = "{", postfix = "}") { (k, v) ->
            "\"$k\":\"$v\""
        }
    }
}
