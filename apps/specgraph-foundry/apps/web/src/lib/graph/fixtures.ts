import type { SemanticGraph, SemanticGraphEdge, SemanticGraphNode } from "./schemas";

/**
 * Deterministic, seeded performance fixtures. Imported only by tests
 * (graph.test.ts, layout.test.ts, and component tests) — never by
 * production data paths. Node/edge IDs and structure are fully reproducible
 * for a given node count.
 */

function mulberry32(seed: number) {
  let state = seed;
  return () => {
    state |= 0;
    state = (state + 0x6d2b79f5) | 0;
    let t = Math.imul(state ^ (state >>> 15), 1 | state);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const STAGE_TYPES = ["CONTRACT", "IMPLEMENTATION", "VERIFICATION"] as const;
const STATUSES = ["PENDING", "READY", "BLOCKED", "COMPLETE"] as const;

export function buildFixtureGraph(nodeCount: number, seed = 42): SemanticGraph {
  const random = mulberry32(seed + nodeCount);
  const nodes: SemanticGraphNode[] = [];
  for (let index = 0; index < nodeCount; index += 1) {
    const stage = STAGE_TYPES[index % STAGE_TYPES.length];
    const status = STATUSES[Math.floor(random() * STATUSES.length)];
    nodes.push({
      id: `fixture-node-${index.toString(10).padStart(6, "0")}`,
      node_key: `fixture-${index}`,
      node_type: stage,
      title: `Fixture stage ${index}`,
      status,
      payload: { fixtureIndex: index },
      created_at: new Date(0).toISOString(),
    });
  }
  const edges: SemanticGraphEdge[] = [];
  for (let index = 1; index < nodeCount; index += 1) {
    edges.push({
      id: `fixture-edge-${index.toString(10).padStart(6, "0")}`,
      from_node_id: nodes[index - 1].id,
      to_node_id: nodes[index].id,
      edge_type: "MUST_PRECEDE",
      rationale: "Deterministic fixture chain.",
      inferred: false,
      created_at: new Date(0).toISOString(),
    });
  }
  const extraEdgeCount = Math.floor(nodeCount / 10);
  for (let index = 0; index < extraEdgeCount; index += 1) {
    const from = Math.floor(random() * nodeCount);
    const to = Math.floor(random() * nodeCount);
    if (from === to) continue;
    const [lo, hi] = from < to ? [from, to] : [to, from];
    edges.push({
      id: `fixture-cross-edge-${index.toString(10).padStart(6, "0")}`,
      from_node_id: nodes[lo].id,
      to_node_id: nodes[hi].id,
      edge_type: "MUST_PRECEDE",
      rationale: "Deterministic fixture cross-dependency.",
      inferred: true,
      created_at: new Date(0).toISOString(),
    });
  }
  return {
    id: `fixture-graph-${nodeCount}`,
    name: `Fixture graph (${nodeCount} nodes)`,
    kind: "EXECUTION",
    enforce_acyclic: true,
    nodes,
    edges,
  };
}

export const FIXTURE_SIZES = [100, 1000, 10000] as const;
