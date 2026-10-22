import { FormatIcon } from "./format-icon";
import { IntegrityBadge } from "./integrity-badge";
import type { SourceDocument } from "@/lib/sources/schemas";

export function DocumentHeader({ document }: { document: SourceDocument }) {
  return (
    <header className="sg-document-header">
      <FormatIcon mediaType={document.media_type} />
      <div>
        <p className="sg-micro-label">Document authority</p>
        <h1>{String(document.title ?? document.name ?? "Untitled source")}</h1>
        <p>{document.media_type ?? "unknown media"} · {Number(document.byte_length ?? document.byte_count ?? 0).toLocaleString()} bytes</p>
      </div>
      <IntegrityBadge state={String(document.extraction_state ?? "authority recorded")} />
    </header>
  );
}
