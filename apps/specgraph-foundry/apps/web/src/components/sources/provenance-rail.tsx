"use client";

import { useState } from "react";
import { SignalLine } from "@/components/visual/signal-line";
import { buildProvenanceNodes } from "@/lib/sources/provenance";
import { ProvenanceLocator } from "./provenance-locator";
import type { DocumentProvenance } from "@/lib/sources/schemas";

export function ProvenanceRail({ provenance }: { provenance?: DocumentProvenance }) {
  const nodes = buildProvenanceNodes(provenance);
  const [selected, setSelected] = useState(nodes[0]?.id);
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
