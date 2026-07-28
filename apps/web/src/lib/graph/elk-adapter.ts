import ELK from "elkjs/lib/elk.bundled.js";
import type { LayoutPosition } from "./schemas";
import type { LayoutWorkerRequest } from "./layout-types";

const elk = new ELK();

type ElkNode = { id: string; width: number; height: number; x?: number; y?: number };
type ElkEdge = { id: string; sources: string[]; targets: string[] };
type ElkGraphResult = { children?: ElkNode[] };

/**
 * Pure async transformation from a normalized layout request to a map of
 * node ID to position. Bounded by elkjs's own layered algorithm; no
 * physics/force simulation runs here. Safe to call directly in tests or
 * from inside the layout worker.
 */
export async function computeElkLayout(request: LayoutWorkerRequest): Promise<Record<string, LayoutPosition>> {
  if (request.nodes.length === 0) {
    return {};
  }
  const elkGraph = {
    id: "root",
    layoutOptions: {
      "elk.algorithm": "layered",
      "elk.direction": request.options.direction,
      "elk.layered.spacing.nodeNodeBetweenLayers": String(request.options.layerSpacing),
      "elk.spacing.nodeNode": String(request.options.nodeSpacing),
      "elk.layered.considerModelOrder.strategy": "NODES_AND_EDGES",
    },
    children: request.nodes.map((node): ElkNode => ({ id: node.id, width: node.width, height: node.height })),
    edges: request.edges.map((edge): ElkEdge => ({ id: edge.id, sources: [edge.source], targets: [edge.target] })),
  };
  const result = (await elk.layout(elkGraph)) as ElkGraphResult;
  const positions: Record<string, LayoutPosition> = {};
  for (const child of result.children ?? []) {
    positions[child.id] = { x: child.x ?? 0, y: child.y ?? 0 };
  }
  return positions;
}
