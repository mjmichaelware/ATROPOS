export type ZoomTier = "constellation" | "label" | "inspection";

export type GraphSizeTier = "small" | "medium" | "large";

const SMALL_MAX_NODES = 150;
const MEDIUM_MAX_NODES = 2000;

export function graphSizeTier(nodeCount: number): GraphSizeTier {
  if (nodeCount <= SMALL_MAX_NODES) return "small";
  if (nodeCount <= MEDIUM_MAX_NODES) return "medium";
  return "large";
}

/**
 * Zoom-driven detail tier, adapted by graph size so a 10,000-node graph
 * reaches a coarser tier at a higher zoom level than a 100-node graph.
 * Pure and deterministic: identical (zoom, nodeCount) always yields the
 * same tier.
 */
export function computeZoomTier(zoom: number, nodeCount: number): ZoomTier {
  const size = graphSizeTier(nodeCount);
  const thresholds: Record<GraphSizeTier, { label: number; inspection: number }> = {
    small: { label: 0.5, inspection: 1.1 },
    medium: { label: 0.7, inspection: 1.3 },
    large: { label: 0.9, inspection: 1.6 },
  };
  const { label, inspection } = thresholds[size];
  if (zoom >= inspection) return "inspection";
  if (zoom >= label) return "label";
  return "constellation";
}

/**
 * Bounds on simultaneous detailed rendering, used to force safe modes for
 * medium/large graphs rather than attempting to render every node at full
 * detail simultaneously.
 */
export function detailedNodeBudget(size: GraphSizeTier): number {
  if (size === "small") return Number.POSITIVE_INFINITY;
  if (size === "medium") return 300;
  return 150;
}

export function requiresLargeGraphSafeMode(nodeCount: number): boolean {
  return graphSizeTier(nodeCount) === "large";
}
