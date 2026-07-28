import { redactReceipts } from "@/lib/execution/receipts";
import type { ExecutionReceipt } from "@/lib/execution/schemas";

export function ExecutionReceiptList({ receipts }: { receipts: ExecutionReceipt[] }) {
  const safe = redactReceipts(receipts);
  if (safe.length === 0) {
    return <p className="sg-muted">No receipts have been submitted for this run yet.</p>;
  }
  return (
    <>
      <p className="sg-muted">
        A receipt is the connected runtime system reporting back what actually happened, with an evidence hash proving what it validated against — never the evidence content itself, only enough
        metadata to confirm the check really occurred.
      </p>
      <ul className="sg-plan-history" aria-label="Execution receipts (safe metadata only)">
        {safe.map((receipt) => (
          <li key={receipt.id}>
            <div>
              <strong>{receipt.outcome ?? "Unknown outcome"}</strong>
              <p className="sg-micro-label">
                {receipt.actorSystem ?? "unknown actor system"} · {receipt.validationStatus ?? "unknown validation status"}
              </p>
              {receipt.summary ? <p>{receipt.summary}</p> : null}
              <dl>
                <div>
                  <dt>Evidence hash</dt>
                  <dd className="sg-mono">{receipt.evidenceSha256 ?? "unknown"}</dd>
                </div>
                {typeof receipt.evidenceFieldCount === "number" ? (
                  <div>
                    <dt>Evidence</dt>
                    <dd>{receipt.evidenceFieldCount} field(s) attached (content not shown)</dd>
                  </div>
                ) : null}
              </dl>
            </div>
          </li>
        ))}
      </ul>
    </>
  );
}
