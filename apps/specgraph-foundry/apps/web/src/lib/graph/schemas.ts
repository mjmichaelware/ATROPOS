import type { ApiResult } from "@/lib/api/client";

export type FreeformRecord = Record<string, unknown>;

/**
 * Semantic graph domain types. These describe database-authoritative content
 * returned by the API. They never carry visual/renderer state.
 */

export type GraphNodeStatus = "PENDING" | "READY" | "CLAIMED" | "RUNNING" | "BLOCKED" | "FAILED" | "CANCELLED" | "COMPLETE" | "UNKNOWN";

export type GraphKind = "AUTHORITY" | "EXECUTION" | "RESEARCH" | "CUSTOM" | "UNKNOWN";

export type SemanticGraphNode = FreeformRecord & {
  id: string;
  graph_id?: string;
  node_key?: string;
  node_type?: string;
  title?: string;
  status?: string;
  payload?: FreeformRecord;
  created_at?: string;
};

export type SemanticGraphEdge = FreeformRecord & {
  id: string;
  graph_id?: string;
  from_node_id: string;
  to_node_id: string;
  edge_type?: string;
  inferred?: boolean;
  rationale?: string;
  created_at?: string;
};

export type SemanticGraph = FreeformRecord & {
  id: string;
  name?: string;
  kind?: string;
  enforce_acyclic?: boolean;
  nodes: SemanticGraphNode[];
  edges: SemanticGraphEdge[];
};

export type AuthorityRelation = FreeformRecord & {
  id: string;
  project_id?: string;
  from_atom_id: string;
  to_atom_id: string;
  relation_type?: string;
  rationale?: string;
  confidence?: number;
  inferred?: boolean;
  created_at?: string;
};

export type PlanSummary = FreeformRecord & {
  id: string;
  status?: string;
  fingerprint?: string;
  allow_open_research?: boolean;
  created_at?: string;
  detail_route?: string;
};

export type PlanDetail = FreeformRecord & {
  id: string;
  status?: string;
  authority_graph?: SemanticGraph;
  execution_graph?: SemanticGraph;
  ready_nodes?: FreeformRecord[];
  bindings?: FreeformRecord[];
  findings?: FreeformRecord[];
};

export type PlanningCounts = FreeformRecord & {
  authority_relations?: number;
  plans?: number;
  draft_plans?: number;
  blocked_plans?: number;
  verified_plans?: number;
  authority_nodes?: number;
  authority_edges?: number;
  execution_nodes?: number;
  execution_edges?: number;
  ready_nodes?: number;
  blocked_nodes?: number;
};

export type PlanningWorkspace = FreeformRecord & {
  project?: FreeformRecord;
  counts?: PlanningCounts;
  relations?: AuthorityRelation[];
  relations_count?: number;
  relations_has_more?: boolean;
  relations_route?: string;
  plans?: PlanSummary[];
  plans_count?: number;
  plans_has_more?: boolean;
  plans_route?: string;
  latest_plan?: PlanSummary | null;
};

export type PageResult<T> = ApiResult<{ items: T[] }>;

/**
 * Visual layout types. These describe renderer/preference state only and
 * must never carry semantic relationship meaning. This boundary is enforced
 * structurally: no field below can express a node type, edge type,
 * dependency, readiness, verification, or provenance fact.
 */

export type GraphMode = "authority" | "execution";

export type LayoutAlgorithm = "blueprint" | "compact" | "freeform" | "focus";

export type LayoutPosition = {
  x: number;
  y: number;
};

export type LayoutViewport = {
  x: number;
  y: number;
  zoom: number;
};

export type VisualLayoutState = {
  version: 1;
  algorithm: LayoutAlgorithm;
  nodePositions: Record<string, LayoutPosition>;
  viewport?: LayoutViewport;
  expandedGroupIds: string[];
  collapsedGroupIds: string[];
  generatedAt: string;
};

export function createEmptyLayoutState(algorithm: LayoutAlgorithm): VisualLayoutState {
  return {
    version: 1,
    algorithm,
    nodePositions: {},
    expandedGroupIds: [],
    collapsedGroupIds: [],
    generatedAt: new Date(0).toISOString(),
  };
}
