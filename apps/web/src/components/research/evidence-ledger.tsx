import type { EvidenceRecord } from "@/lib/research/schemas";
import { EvidenceCard } from "./evidence-card";

export function EvidenceLedger({ evidence }: { evidence?: EvidenceRecord[] }) {
  const items = evidence ?? [];
  return (
    <section aria-label="Evidence ledger" className="sg-evidence-ledger">
      <h3>Evidence ledger</h3>
      {items.length ? items.map((item, index) => <EvidenceCard key={item.id ?? index} evidence={item} />) : <p className="sg-muted">No evidence has been recorded for this task.</p>}
    </section>
  );
}
