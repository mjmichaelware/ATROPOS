import type { LayoutAlgorithm } from "./schemas";
import { DEFAULT_NODE_HEIGHT, DEFAULT_NODE_WIDTH, type LayoutOptions, type LayoutWorkerRequest } from "./layout-types";
import type { RendererGraphContent } from "./transform";

export function normalizeLayoutOptions(algorithm: LayoutAlgorithm): LayoutOptions {
  if (algorithm === "compact") {
    return { algorithm, direction: "DOWN", nodeSpacing: 24, layerSpacing: 56 };
  }
  if (algorithm === "focus") {
    return { algorithm, direction: "RIGHT", nodeSpacing: 40, layerSpacing: 80 };
  }
  return { algorithm, direction: "DOWN", nodeSpacing: 48, layerSpacing: 96 };
}

/**
 * Builds a deterministic, normalized layout request from renderer content.
 * Nodes and edges are stably sorted by ID so identical content always
 * produces an identical request payload regardless of arrival order.
 */
export function buildLayoutRequest(content: RendererGraphContent, algorithm: LayoutAlgorithm, generation: number): LayoutWorkerRequest {
  const nodes = [...content.nodes]
    .sort((a, b) => a.id.localeCompare(b.id))
    .map((node) => ({ id: node.id, width: DEFAULT_NODE_WIDTH, height: DEFAULT_NODE_HEIGHT }));
  const edges = [...content.edges]
    .sort((a, b) => a.id.localeCompare(b.id))
    .map((edge) => ({ id: edge.id, source: edge.source, target: edge.target }));
  return { generation, algorithm, options: normalizeLayoutOptions(algorithm), nodes, edges };
}
