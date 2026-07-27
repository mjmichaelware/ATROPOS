import type { RendererEdge, RendererGraphContent, RendererNodeCategory, RendererNodeContent } from "./transform";

export type GraphFilterState = {
  query: string;
  category: RendererNodeCategory | "all";
  status: string | "all";
};

export function emptyGraphFilterState(): GraphFilterState {
  return { query: "", category: "all", status: "all" };
}

function matchesQuery(node: RendererNodeContent, query: string) {
  if (!query.trim()) return true;
  const needle = query.trim().toLowerCase();
  return node.data.label.toLowerCase().includes(needle) || node.id.toLowerCase().includes(needle);
}

/**
 * Filters over the currently loaded, bounded subset only. This never
 * queries the server; callers must present it as loaded-subset search, not
 * server-wide search.
 */
export function filterGraphContent(content: RendererGraphContent, filters: GraphFilterState): RendererGraphContent {
  const nodes = content.nodes.filter((node) => {
    if (filters.category !== "all" && node.data.category !== filters.category) return false;
    if (filters.status !== "all" && (node.data.status ?? "UNKNOWN") !== filters.status) return false;
    return matchesQuery(node, filters.query);
  });
  const nodeIds = new Set(nodes.map((node) => node.id));
  const edges = content.edges.filter((edge) => nodeIds.has(edge.source) && nodeIds.has(edge.target));
  return { nodes, edges };
}

export function availableStatuses(content: RendererGraphContent): string[] {
  const statuses = new Set<string>();
  for (const node of content.nodes) {
    if (node.data.status) statuses.add(node.data.status);
  }
  return [...statuses].sort();
}

export function availableCategories(content: RendererGraphContent): RendererNodeCategory[] {
  const categories = new Set<RendererNodeCategory>();
  for (const node of content.nodes) {
    categories.add(node.data.category);
  }
  return [...categories].sort();
}

export function connectedRelationshipCount(nodeId: string, edges: RendererEdge[]): number {
  return edges.filter((edge) => edge.source === nodeId || edge.target === nodeId).length;
}

export function neighborhood(nodeId: string, content: RendererGraphContent): { nodes: RendererNodeContent[]; edges: RendererEdge[] } {
  const edges = content.edges.filter((edge) => edge.source === nodeId || edge.target === nodeId);
  const neighborIds = new Set<string>([nodeId]);
  for (const edge of edges) {
    neighborIds.add(edge.source);
    neighborIds.add(edge.target);
  }
  const nodes = content.nodes.filter((node) => neighborIds.has(node.id));
  return { nodes, edges };
}
