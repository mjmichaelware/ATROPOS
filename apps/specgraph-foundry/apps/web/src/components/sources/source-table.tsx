import type { SourceDocument } from "@/lib/sources/schemas";
import { SourceCard } from "./source-card";

export function SourceTable({ projectId, documents }: { projectId: string; documents: SourceDocument[] }) {
  return (
    <div className="sg-source-table" role="list" aria-label="Source documents">
      {documents.map((document) => (
        <div key={document.id} role="listitem">
          <SourceCard projectId={projectId} document={document} />
        </div>
      ))}
    </div>
  );
}
