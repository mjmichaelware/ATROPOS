import Link from "next/link";
import { projectDocumentRoute } from "@/components/navigation/routes";
import { cellsForAtom } from "@/lib/research/gaps";
import type { GapAtom } from "@/lib/research/schemas";
import { DimensionStatus } from "./dimension-status";

export function AtomCard({ projectId, atom }: { projectId: string; atom: GapAtom }) {
  const cells = cellsForAtom(atom);
  const documentId = atom.source_document_id ?? atom.document_id;
  return (
    <article className="sg-atom-card">
      <h3>{String(atom.label ?? atom.text ?? "Atom")}</h3>
      <p className="sg-muted">Open gaps: {cells.filter((cell) => String(cell.status).toUpperCase() === "OPEN").length}</p>
      <div className="sg-dimension-strip">
        {cells.slice(0, 6).map((cell, index) => <DimensionStatus key={`${atom.id}-${cell.dimension ?? index}`} status={cell.status} />)}
      </div>
      {documentId ? <Link href={projectDocumentRoute(projectId, documentId)}>Open source provenance</Link> : null}
    </article>
  );
}
