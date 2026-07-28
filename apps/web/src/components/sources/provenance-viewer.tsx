import { HudPanel } from "@/components/visual/hud-panel";
import { buildProvenanceNodes } from "@/lib/sources/provenance";
import { ProvenanceRail } from "./provenance-rail";
import type { DocumentProvenance } from "@/lib/sources/schemas";

export function ProvenanceViewer({ provenance }: { provenance?: DocumentProvenance }) {
  const nodes = buildProvenanceNodes(provenance);
  return (
    <HudPanel title="Provenance rail" status={`${nodes.length} nodes`}>
      <ProvenanceRail provenance={provenance} />
      <ol className="sg-provenance-list" aria-label="Provenance list alternative">
        {nodes.map((node) => <li key={node.id}>{node.label}</li>)}
      </ol>
    </HudPanel>
  );
}
