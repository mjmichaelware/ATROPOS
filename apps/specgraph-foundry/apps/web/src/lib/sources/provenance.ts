import type { DerivationLocator, DocumentProvenance } from "./schemas";

export type ProvenanceNode = {
  id: string;
  label: string;
  kind: "raw" | "derivation" | "locator";
  byteStart?: number;
  byteEnd?: number;
  lineStart?: number;
  lineEnd?: number;
};

export function buildProvenanceNodes(provenance?: DocumentProvenance): ProvenanceNode[] {
  const raw = provenance?.provenance?.raw_authority;
  const derivation = provenance?.provenance?.derivation;
  const nodes: ProvenanceNode[] = [];
  if (raw) {
    nodes.push({ id: "raw", label: "Raw authority", kind: "raw", byteStart: 0, byteEnd: raw.byte_count });
  }
  if (derivation) {
    nodes.push({
      id: "derivation",
      label: `${derivation.adapter_name ?? "Adapter"} derivation`,
      kind: "derivation",
      byteStart: 0,
      byteEnd: derivation.derived_byte_count,
    });
    for (const locator of derivation.locators_preview ?? []) {
      nodes.push(locatorNode(locator));
    }
  }
  return nodes;
}

function locatorNode(locator: DerivationLocator): ProvenanceNode {
  const ordinal = locator.ordinal ?? 0;
  return {
    id: `locator-${ordinal}`,
    label: locator.label ?? `${locator.kind ?? "locator"} ${ordinal}`,
    kind: "locator",
    byteStart: locator.derived_byte_start,
    byteEnd: locator.derived_byte_end,
    lineStart: locator.derived_line_start,
    lineEnd: locator.derived_line_end,
  };
}
