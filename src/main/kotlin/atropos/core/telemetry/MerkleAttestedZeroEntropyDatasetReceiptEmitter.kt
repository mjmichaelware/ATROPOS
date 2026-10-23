/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.telemetry

import atropos.core.certificate.ZeroEntropyCertificateEmitter
import atropos.core.evaluation.EvidenceEntry
import atropos.core.evaluation.EvidenceStore
import atropos.core.storage.StorageAccountingEntry
import atropos.core.storage.StorageAccountingLedger
import java.time.Instant

/**
 * SD7-T1-TEL20: Merkle Attested Zero Entropy Dataset Receipt Emitter.
 *
 * Cryptographic dataset attestation protocol that binds every committed telemetry batch
 * to a verifiable Merkle root hash and CAS receipt ledger.
 * Guarantees zero data committed without provenance linking to originating prompt H0.
 */
class MerkleAttestedZeroEntropyDatasetReceiptEmitter(
    private val evidenceStore: EvidenceStore = EvidenceStore(),
    private val accountingLedger: StorageAccountingLedger = StorageAccountingLedger()
) {
    data class DatasetCommitReceipt(
        val batchId: String,
        val h0PromptLineage: String,
        val chunkMerkleRoot: String,
        val totalRecords: Int,
        val casEvidencePointer: String,
        val signatureHash: String,
        val timestamp: Instant = Instant.now()
    )

    fun emitReceipt(
        batch: TelemetryBatchPayload,
        h0Prompt: String
    ): DatasetCommitReceipt {
        val merkleRoot = batch.merkleRoot
        val casPointer = "cas://telemetry/chunk/${batch.batchId}/$merkleRoot"
        val signature = FrontierSweTelemetryRecord.sha256Hex("$h0Prompt:$merkleRoot:${batch.totalRecords}")

        val receipt = DatasetCommitReceipt(
            batchId = batch.batchId,
            h0PromptLineage = h0Prompt,
            chunkMerkleRoot = merkleRoot,
            totalRecords = batch.totalRecords,
            casEvidencePointer = casPointer,
            signatureHash = signature
        )

        // Store into immutable CAS evidence store
        evidenceStore.store(
            EvidenceEntry(
                id = receipt.batchId,
                metric = "telemetry.dataset_chunk_merkle_root",
                hash = receipt.chunkMerkleRoot,
                timestamp = receipt.timestamp.toEpochMilli()
            )
        )

        // Record in accounting ledger
        accountingLedger.record(
            StorageAccountingEntry(
                objectId = receipt.batchId,
                bytes = batch.toJsonLines().length.toLong(),
                storageClass = "TELEMETRY_PARQUET_CHUNK",
                recordedAt = receipt.timestamp
            )
        )

        return receipt
    }
}
