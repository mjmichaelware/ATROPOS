import { HudPanel } from "@/components/visual/hud-panel";
import { HashDisplay } from "./hash-display";
import type { DocumentProvenance, SourceDocument } from "@/lib/sources/schemas";

export function DocumentAuthority({ document, provenance }: { document: SourceDocument; provenance?: DocumentProvenance }) {
  const raw = provenance?.provenance?.raw_authority;
  return (
    <HudPanel title="Raw authority" status="immutable bytes">
      <dl className="sg-authority-grid">
        <div><dt>Original media</dt><dd>{raw?.original_media_type ?? document.media_type ?? "unknown"}</dd></div>
        <div><dt>Byte length</dt><dd>{Number(raw?.byte_count ?? document.byte_length ?? 0).toLocaleString()}</dd></div>
        <div><dt>Storage</dt><dd>{raw?.bucket ? "private bucket object" : "not exposed"}</dd></div>
      </dl>
      <HashDisplay value={raw?.sha256 ?? String(document.content_sha256 ?? document.sha256 ?? "")} label="Original SHA-256" />
    </HudPanel>
  );
}
