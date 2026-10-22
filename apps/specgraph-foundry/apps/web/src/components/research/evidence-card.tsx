import type { EvidenceRecord } from "@/lib/research/schemas";

export function EvidenceCard({ evidence }: { evidence: EvidenceRecord }) {
  const uri = evidence.source_uri ? new URL(String(evidence.source_uri)) : undefined;
  return (
    <article className="sg-evidence-card">
      <h4>{String(evidence.source_title ?? "Evidence")}</h4>
      <p>{String(evidence.excerpt ?? "")}</p>
      {uri ? <a href={uri.href} target="_blank" rel="noreferrer noopener">Open {uri.hostname}</a> : null}
      <small>{String(evidence.evidence_type ?? "evidence")} · reliability {String(evidence.reliability ?? "unrated")}</small>
    </article>
  );
}
