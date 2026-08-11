import { ByteRange } from "./byte-range";
import { LineRange } from "./line-range";
import type { ProvenanceNode } from "@/lib/sources/provenance";

export function ProvenanceLocator({ node, selected, onSelect }: { node: ProvenanceNode; selected?: boolean; onSelect: () => void }) {
  return (
    <button type="button" className="sg-provenance-node" data-selected={selected || undefined} onClick={onSelect}>
      <strong>{node.label}</strong>
      <ByteRange range={{ byte_start: node.byteStart, byte_end: node.byteEnd }} />
      <LineRange range={{ line_start: node.lineStart, line_end: node.lineEnd }} />
    </button>
  );
}
