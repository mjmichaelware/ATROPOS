import { Alert } from "@/components/ui/alert";
import type { GraphSizeTier } from "@/lib/graph/zoom";

export function GraphLargeModeNotice({ size, nodeCount }: { size: GraphSizeTier; nodeCount: number }) {
  if (size === "small") {
    return null;
  }
  return (
    <Alert tone="info" title={size === "large" ? "Large-graph safe mode active" : "Simplified rendering active"}>
      <p>
        {nodeCount} nodes loaded. {size === "large"
          ? "Detailed rendering is capped, motion is minimized, and filters or the accessible list are the fastest way to work at this scale."
          : "Detail is reduced at low zoom to keep interaction responsive. Zoom in or use filters to see full detail on fewer nodes."}
      </p>
    </Alert>
  );
}
