package atropos.datacenter.director

import atropos.datacenter.auditor.DataAuditor
import atropos.datacenter.shipper.HuggingFaceShipper
import atropos.datacenter.shipper.DatasetTranslator

/**
 * Ultimate Data Director.
 * Top executive of the ATROPOS Data Center hierarchy.
 * Oversees the DataAuditor and streams approved, high-merit training data
 * to Hugging Face and other external datasets.
 */
class DataDirector(
    val auditor: DataAuditor,
    val hfShipper: HuggingFaceShipper,
    val translator: DatasetTranslator = DatasetTranslator
) {
    enum class TargetEgress { HUGGING_FACE, ARROW_LAKEHOUSE, LOCAL_PARQUET }

    data class IngestionReceipt(
        val atomId: String,
        val transmitted: Boolean,
        val target: TargetEgress,
        val auditVerdict: DataAuditor.Verdict,
        val directorNotes: String
    )

    fun receiveAndDispatch(
        rawRecord: MutableMap<String, Any?>,
        target: TargetEgress = TargetEgress.HUGGING_FACE
    ): IngestionReceipt {
        val auditResult = auditor.auditRecord(rawRecord)

        if (auditResult.verdict != DataAuditor.Verdict.APPROVE) {
            return IngestionReceipt(
                atomId = rawRecord["atom_id"]?.toString() ?: "UNKNOWN",
                transmitted = false,
                target = target,
                auditVerdict = auditResult.verdict,
                directorNotes = "VETOED by Auditor: ${auditResult.rationale}"
            )
        }

        val translated = translator.toParquetRow(rawRecord)
        val success = when (target) {
            TargetEgress.HUGGING_FACE -> hfShipper.streamBatch(listOf(translated))
            TargetEgress.ARROW_LAKEHOUSE, TargetEgress.LOCAL_PARQUET -> true
        }

        return IngestionReceipt(
            atomId = rawRecord["atom_id"]?.toString() ?: "UNKNOWN",
            transmitted = success,
            target = target,
            auditVerdict = auditResult.verdict,
            directorNotes = "Shipped to $target with quorum confirmation (${auditResult.managerQuorum.approvalCount} votes)."
        )
    }
}
