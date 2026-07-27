import type { ExecutionReceipt, FreeformRecord } from "./schemas";

export type SafeReceipt = {
  id: string;
  runNodeId?: string;
  actorSystem?: string;
  actorId?: string;
  outcome?: string;
  summary?: string;
  evidenceSha256?: string;
  evidenceFieldCount?: number;
  validationStatus?: string;
  createdAt?: string;
};

/**
 * Redacts a receipt record to safe, human-viewable metadata only. The raw
 * `evidence` object the server returns is deliberately never included in
 * the result — only a count of its top-level keys, so the UI can indicate
 * evidence is attached without rendering private receipt payload content.
 */
export function redactReceipt(receipt: ExecutionReceipt & { evidence?: FreeformRecord }): SafeReceipt {
  const evidence = receipt.evidence;
  return {
    id: receipt.id,
    runNodeId: typeof receipt.run_node_id === "string" ? receipt.run_node_id : undefined,
    actorSystem: typeof receipt.actor_system === "string" ? receipt.actor_system : undefined,
    actorId: typeof receipt.actor_id === "string" ? receipt.actor_id : undefined,
    outcome: typeof receipt.outcome === "string" ? receipt.outcome : undefined,
    summary: typeof receipt.summary === "string" ? receipt.summary : undefined,
    evidenceSha256: typeof receipt.evidence_sha256 === "string" ? receipt.evidence_sha256 : undefined,
    evidenceFieldCount: evidence && typeof evidence === "object" ? Object.keys(evidence).length : undefined,
    validationStatus: typeof receipt.validation_status === "string" ? receipt.validation_status : undefined,
    createdAt: typeof receipt.created_at === "string" ? receipt.created_at : undefined,
  };
}

export function redactReceipts(receipts: Array<ExecutionReceipt & { evidence?: FreeformRecord }>): SafeReceipt[] {
  return receipts.map(redactReceipt);
}
