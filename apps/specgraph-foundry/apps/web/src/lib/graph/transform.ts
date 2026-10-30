import type { AuthorityRelation, LayoutPosition, SemanticGraph, SemanticGraphEdge, SemanticGraphNode, VisualLayoutState } from "./schemas";

export const RELATION_EDGE_TYPES = new Set(["REQUIRES", "REFINES", "CONFLICTS_WITH", "DUPLICATES", "IMPLEMENTS", "VERIFIES", "RELATES_TO"]);
export const EXECUTION_EDGE_TYPES = new Set(["MUST_PRECEDE"]);

export type RendererNodeCategory =
  | "project"
  | "source-document"
  | "section"
  | "atom"
  | "dimension"
  | "research-task"
  | "evidence"
  | "conclusion"
  | "semantic-group"
  | "plan-stage"
  | "execution-stage"
  | "verification-stage"
  | "unknown";

export type RendererEdgeCategory = "authority-relation" | "execution-dependency" | "unknown";

export type RendererNodeData = {
  sourceId: string;
  category: RendererNodeCategory;
  label: string;
  typeLabel: string;
  status?: string;
  /** Real backend atom identifier, when the node payload or fallback source actually carries one. */
  atomId?: string;
};

export type RendererNodeContent = {
  id: string;
  data: RendererNodeData;
};

export type RendererNode = RendererNodeContent & {
  position: LayoutPosition;
};

export type RendererEdgeData = {
  sourceId: string;
  category: RendererEdgeCategory;
  relationLabel?: string;
  rationale?: string;
  confidence?: number;
  inferred?: boolean;
};

export type RendererEdge = {
  id: string;
  source: string;
  target: string;
  data: RendererEdgeData;
};

export type RendererGraphContent = {
  nodes: RendererNodeContent[];
  edges: RendererEdge[];
};

const NODE_TYPE_CATEGORY: Record<string, RendererNodeCategory> = {
  PROJECT: "project",
  SOURCE_DOCUMENT: "source-document",
  SECTION: "section",
  ATOM: "atom",
  DIMENSION: "dimension",
  RESEARCH_TASK: "research-task",
  EVIDENCE: "evidence",
  CONCLUSION: "conclusion",
  SEMANTIC_GROUP: "semantic-group",
  CONTRACT: "plan-stage",
  IMPLEMENTATION: "execution-stage",
  VERIFICATION: "verification-stage",
};

export function normalizeNodeCategory(nodeType: string | undefined): RendererNodeCategory {
  if (!nodeType) return "unknown";
  return NODE_TYPE_CATEGORY[nodeType.toUpperCase()] ?? "unknown";
}

export function normalizeEdgeCategory(edgeType: string | undefined): RendererEdgeCategory {
  if (!edgeType) return "unknown";
  const upper = edgeType.toUpperCase();
  if (RELATION_EDGE_TYPES.has(upper)) return "authority-relation";
  if (EXECUTION_EDGE_TYPES.has(upper)) return "execution-dependency";
  return "unknown";
}

function safeLabel(node: SemanticGraphNode): string {
  const title = typeof node.title === "string" ? node.title.trim() : "";
  if (title) return title;
  const key = typeof node.node_key === "string" ? node.node_key.trim() : "";
  if (key) return key;
  return `Node ${node.id.slice(0, 8)}`;
}

function nodeContent(node: SemanticGraphNode): RendererNodeContent {
  const payloadAtomId = node.payload?.atom_id;
  return {
    id: node.id,
    data: {
      sourceId: node.id,
      category: normalizeNodeCategory(node.node_type),
      label: safeLabel(node),
      typeLabel: node.node_type ?? "Unknown",
      status: typeof node.status === "string" ? node.status : undefined,
      atomId: typeof payloadAtomId === "string" ? payloadAtomId : undefined,
    },
  };
}

function edgeContent(edge: SemanticGraphEdge): RendererEdge {
  return {
    id: edge.id,
    source: edge.from_node_id,
    target: edge.to_node_id,
    data: {
      sourceId: edge.id,
      category: normalizeEdgeCategory(edge.edge_type),
      relationLabel: edge.edge_type,
      rationale: edge.rationale || undefined,
      inferred: typeof edge.inferred === "boolean" ? edge.inferred : undefined,
    },
  };
}

/**
 * Pure transformation from a semantic graph (database authority) into
 * renderer-ready content with no positions. Duplicate node IDs are
 * deduplicated (first occurrence wins). Edges referencing a missing
 * endpoint are dropped rather than allowed to crash the renderer.
 */
export function semanticGraphToRendererContent(graph: SemanticGraph | undefined | null): RendererGraphContent {
  if (!graph) {
    return { nodes: [], edges: [] };
  }
  const seen = new Set<string>();
  const nodes: RendererNodeContent[] = [];
  for (const node of graph.nodes ?? []) {
    if (!node || typeof node.id !== "string" || seen.has(node.id)) continue;
    seen.add(node.id);
    nodes.push(nodeContent(node));
  }
  const nodeIds = new Set(nodes.map((node) => node.id));
  const edgeIds = new Set<string>();
  const edges: RendererEdge[] = [];
  for (const edge of graph.edges ?? []) {
    if (!edge || typeof edge.id !== "string" || edgeIds.has(edge.id)) continue;
    if (!nodeIds.has(edge.from_node_id) || !nodeIds.has(edge.to_node_id)) continue;
    edgeIds.add(edge.id);
    edges.push(edgeContent(edge));
  }
  return { nodes, edges };
}

/**
 * Pure transformation building an authority-mode renderer graph directly
 * from authority relation records when no synthesized plan graph exists yet.
 * Atom identity beyond its ID is not fetched here; nodes render label-safe
 * from the ID only (Group 15 owns full atom-authority editing).
 */
export function relationsToRendererContent(relations: AuthorityRelation[]): RendererGraphContent {
  const nodeIds = new Set<string>();
  for (const relation of relations) {
    if (relation.from_atom_id) nodeIds.add(relation.from_atom_id);
    if (relation.to_atom_id) nodeIds.add(relation.to_atom_id);
  }
  const nodes: RendererNodeContent[] = [...nodeIds].map((id) => ({
    id,
    data: {
      sourceId: id,
      category: "atom" as const,
      label: `Atom ${id.slice(0, 8)}`,
      typeLabel: "ATOM",
      atomId: id,
    },
  }));
  const edgeIds = new Set<string>();
  const edges: RendererEdge[] = [];
  for (const relation of relations) {
    if (!relation.id || edgeIds.has(relation.id)) continue;
    if (!nodeIds.has(relation.from_atom_id) || !nodeIds.has(relation.to_atom_id)) continue;
    edgeIds.add(relation.id);
    edges.push({
      id: relation.id,
      source: relation.from_atom_id,
      target: relation.to_atom_id,
      data: {
        sourceId: relation.id,
        category: "authority-relation",
        relationLabel: relation.relation_type,
        rationale: relation.rationale || undefined,
        confidence: typeof relation.confidence === "number" ? relation.confidence : undefined,
        inferred: typeof relation.inferred === "boolean" ? relation.inferred : undefined,
      },
    });
  }
  return { nodes, edges };
}

/**
 * Merges renderer content with a visual layout state to produce fully
 * positioned renderer nodes. This is the only place layout coordinates are
 * attached; semantic content passed in is not mutated.
 */
export function mergeLayoutPositions(content: RendererNodeContent[], layout: VisualLayoutState): RendererNode[] {
  return content.map((node) => ({
    ...node,
    position: layout.nodePositions[node.id] ?? { x: 0, y: 0 },
  }));
}
