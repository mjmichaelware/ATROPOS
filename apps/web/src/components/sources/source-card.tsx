import Link from "next/link";
import { projectDocumentRoute } from "@/components/navigation/routes";
import { FormatIcon } from "./format-icon";
import { HashDisplay } from "./hash-display";
import { IntegrityBadge } from "./integrity-badge";
import type { SourceDocument } from "@/lib/sources/schemas";

export function SourceCard({ projectId, document }: { projectId: string; document: SourceDocument }) {
  const title = String(document.title ?? document.name ?? "Untitled source");
  return (
    <article className="sg-source-card">
      <FormatIcon mediaType={document.media_type} />
      <div>
        <h3><Link href={projectDocumentRoute(projectId, document.id)}>{title}</Link></h3>
        <p>{document.media_type ?? "media type unavailable"} · {Number(document.byte_length ?? document.byte_count ?? 0).toLocaleString()} bytes</p>
        <HashDisplay value={String(document.content_sha256 ?? document.sha256 ?? "")} />
      </div>
      <IntegrityBadge state={String(document.extraction_state ?? "authority recorded")} />
    </article>
  );
}
