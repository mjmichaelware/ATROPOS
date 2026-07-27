"use client";

import { useMemo, useState } from "react";
import { SignalLine } from "@/components/visual/signal-line";
import { buildProvenanceNodes } from "@/lib/sources/provenance";
import { ProvenanceLocator } from "./provenance-locator";
import type { DocumentProvenance } from "@/lib/sources/schemas";

// Its only caller (ProvenanceViewer, inside DocumentInspector) remounts
// fully when the user navigates to a different document - see
// DocumentInspector's key={documentId} - so seeding `selected` once on
// mount is safe: it never needs to react to `provenance` changing under
// an already-mounted instance.
export function ProvenanceRail({ provenance }: { provenance?: DocumentProvenance }) {
  const nodes = useMemo(() => buildProvenanceNodes(provenance), [provenance]);
  const [selected, setSelected] = useState(() => nodes[0]?.id);
  return (
    <div className="sg-provenance-rail" aria-label="Authority provenance rail">
      {nodes.map((node, index) => (
        <div key={node.id} className="sg-provenance-step">
          {index > 0 ? <SignalLine active={selected === node.id} /> : null}
          <ProvenanceLocator node={node} selected={selected === node.id} onSelect={() => setSelected(node.id)} />
        </div>
      ))}
    </div>
  );
}
