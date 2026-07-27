import { describe, expect, it } from "vitest";
import { buildFixtureGraph, FIXTURE_SIZES } from "./fixtures";
import { loadLayoutPreference, saveLayoutPreference } from "./layout-preferences";
import { availableCategories, availableStatuses, connectedRelationshipCount, emptyGraphFilterState, filterGraphContent, neighborhood } from "./search";
import { isSafeIdentifier, isSafeToPersist, isValidProjectId } from "./security";
import { createEmptyLayoutState } from "./schemas";
import { mergeLayoutPositions, normalizeEdgeCategory, normalizeNodeCategory, relationsToRendererContent, semanticGraphToRendererContent } from "./transform";
import { DEFAULT_GRAPH_URL_STATE, parseGraphUrlState, serializeGraphUrlState } from "./url-state";
import { computeZoomTier, detailedNodeBudget, graphSizeTier, requiresLargeGraphSafeMode } from "./zoom";

describe("graph category normalization", () => {
  it("maps only real backend node types and falls back to unknown", () => {
    expect(normalizeNodeCategory("ATOM")).toBe("atom");
    expect(normalizeNodeCategory("CONTRACT")).toBe("plan-stage");
    expect(normalizeNodeCategory("IMPLEMENTATION")).toBe("execution-stage");
    expect(normalizeNodeCategory("VERIFICATION")).toBe("verification-stage");
    expect(normalizeNodeCategory("SOMETHING_NEW")).toBe("unknown");
    expect(normalizeNodeCategory(undefined)).toBe("unknown");
  });

  it("maps only real backend edge types and falls back to unknown", () => {
    expect(normalizeEdgeCategory("REQUIRES")).toBe("authority-relation");
    expect(normalizeEdgeCategory("MUST_PRECEDE")).toBe("execution-dependency");
    expect(normalizeEdgeCategory("MYSTERY")).toBe("unknown");
  });
});

describe("semantic to renderer transformation", () => {
  it("does not mutate the semantic graph input", () => {
    const graph = {
      id: "graph-1",
      nodes: [{ id: "n1", node_type: "ATOM", title: "Atom one", status: "READY", created_at: "" }],
      edges: [{ id: "e1", from_node_id: "n1", to_node_id: "n1x", edge_type: "REQUIRES", created_at: "" }],
    };
    const snapshot = JSON.parse(JSON.stringify(graph));
    semanticGraphToRendererContent(graph);
    expect(graph).toEqual(snapshot);
  });

  it("drops edges with a missing endpoint instead of crashing", () => {
    const graph = {
      id: "graph-1",
      nodes: [{ id: "n1", node_type: "ATOM", title: "Atom one", status: "READY", created_at: "" }],
      edges: [{ id: "e1", from_node_id: "n1", to_node_id: "does-not-exist", edge_type: "REQUIRES", created_at: "" }],
    };
    const content = semanticGraphToRendererContent(graph);
    expect(content.nodes).toHaveLength(1);
    expect(content.edges).toHaveLength(0);
  });

  it("deduplicates duplicate node and edge IDs safely", () => {
    const graph = {
      id: "graph-1",
      nodes: [
        { id: "n1", node_type: "ATOM", title: "First", status: "READY", created_at: "" },
        { id: "n1", node_type: "ATOM", title: "Duplicate", status: "READY", created_at: "" },
      ],
      edges: [],
    };
    const content = semanticGraphToRendererContent(graph);
    expect(content.nodes).toHaveLength(1);
    expect(content.nodes[0].data.label).toBe("First");
  });

  it("renders unknown node types neutrally without inventing detail", () => {
    const graph = {
      id: "graph-1",
      nodes: [{ id: "n1", node_type: "FUTURE_TYPE", created_at: "" }],
      edges: [],
    };
    const content = semanticGraphToRendererContent(graph);
    expect(content.nodes[0].data.category).toBe("unknown");
    expect(content.nodes[0].data.label).toBe("Node n1");
  });

  it("handles a missing graph without crashing", () => {
    expect(semanticGraphToRendererContent(undefined)).toEqual({ nodes: [], edges: [] });
    expect(semanticGraphToRendererContent(null)).toEqual({ nodes: [], edges: [] });
  });

  it("builds an authority-mode graph directly from relation records with no plan", () => {
    const content = relationsToRendererContent([
      { id: "rel-1", from_atom_id: "atom-a", to_atom_id: "atom-b", relation_type: "REQUIRES", confidence: 0.9, inferred: false },
    ]);
    expect(content.nodes).toHaveLength(2);
    expect(content.edges).toHaveLength(1);
    expect(content.edges[0].data.confidence).toBe(0.9);
  });

  it("never fabricates a confidence value that was not returned", () => {
    const graph = {
      id: "graph-1",
      nodes: [
        { id: "n1", node_type: "ATOM", created_at: "" },
        { id: "n2", node_type: "ATOM", created_at: "" },
      ],
      edges: [{ id: "e1", from_node_id: "n1", to_node_id: "n2", edge_type: "MUST_PRECEDE", created_at: "" }],
    };
    const content = semanticGraphToRendererContent(graph);
    expect(content.edges[0].data.confidence).toBeUndefined();
  });
});

describe("layout state / renderer merge", () => {
  it("keeps layout positions structurally separate from semantic content", () => {
    const layout = createEmptyLayoutState("blueprint");
    layout.nodePositions["n1"] = { x: 12, y: 34 };
    const merged = mergeLayoutPositions([{ id: "n1", data: { sourceId: "n1", category: "atom", label: "A", typeLabel: "ATOM" } }], layout);
    expect(merged[0].position).toEqual({ x: 12, y: 34 });
    expect(Object.keys(layout)).not.toContain("relation_type");
    expect(Object.keys(layout)).not.toContain("edge_type");
  });

  it("assigns an origin fallback position for unpositioned nodes", () => {
    const layout = createEmptyLayoutState("blueprint");
    const merged = mergeLayoutPositions([{ id: "unpositioned", data: { sourceId: "unpositioned", category: "unknown", label: "?", typeLabel: "unknown" } }], layout);
    expect(merged[0].position).toEqual({ x: 0, y: 0 });
  });

  it("layout state JSON round-trips without gaining semantic fields", () => {
    const layout = createEmptyLayoutState("compact");
    layout.nodePositions["n1"] = { x: 1, y: 2 };
    const serialized = JSON.stringify(layout);
    const parsed = JSON.parse(serialized);
    expect(Object.keys(parsed).sort()).toEqual(["algorithm", "collapsedGroupIds", "expandedGroupIds", "generatedAt", "nodePositions", "version"]);
  });
});

describe("search and filters over the loaded subset", () => {
  const content = {
    nodes: [
      { id: "n1", data: { sourceId: "n1", category: "atom" as const, label: "Alpha atom", typeLabel: "ATOM", status: "READY" } },
      { id: "n2", data: { sourceId: "n2", category: "plan-stage" as const, label: "Beta stage", typeLabel: "CONTRACT", status: "BLOCKED" } },
    ],
    edges: [{ id: "e1", source: "n1", target: "n2", data: { sourceId: "e1", category: "execution-dependency" as const } }],
  };

  it("filters the currently loaded subset by query, category, and status", () => {
    expect(filterGraphContent(content, { ...emptyGraphFilterState(), query: "alpha" }).nodes).toHaveLength(1);
    expect(filterGraphContent(content, { ...emptyGraphFilterState(), category: "plan-stage" }).nodes).toHaveLength(1);
    expect(filterGraphContent(content, { ...emptyGraphFilterState(), status: "BLOCKED" }).nodes).toHaveLength(1);
  });

  it("drops edges whose endpoint was filtered out", () => {
    const filtered = filterGraphContent(content, { ...emptyGraphFilterState(), category: "atom" });
    expect(filtered.edges).toHaveLength(0);
  });

  it("reports only categories and statuses actually present", () => {
    expect(availableCategories(content)).toEqual(["atom", "plan-stage"]);
    expect(availableStatuses(content)).toEqual(["BLOCKED", "READY"]);
  });

  it("computes connected relationship counts and neighborhoods from loaded edges only", () => {
    expect(connectedRelationshipCount("n1", content.edges)).toBe(1);
    expect(neighborhood("n1", content).nodes).toHaveLength(2);
  });
});

describe("security boundaries", () => {
  it("validates project IDs and bounded identifiers", () => {
    expect(isValidProjectId("project-123")).toBe(true);
    expect(isValidProjectId("../../etc/passwd")).toBe(false);
    expect(isValidProjectId(undefined)).toBe(false);
    expect(isSafeIdentifier("a".repeat(200))).toBe(false);
  });

  it("bounds persisted layout state size", () => {
    expect(isSafeToPersist("{}")).toBe(true);
    expect(isSafeToPersist("x".repeat(100_000))).toBe(false);
    expect(isSafeToPersist("")).toBe(false);
  });
});

describe("semantic zoom and adaptive quality", () => {
  it("requires higher zoom to reach detail tiers on larger graphs", () => {
    expect(computeZoomTier(0.6, 50)).toBe("label");
    expect(computeZoomTier(0.6, 5000)).toBe("constellation");
    expect(computeZoomTier(1.7, 5000)).toBe("inspection");
  });

  it("is deterministic for identical inputs", () => {
    expect(computeZoomTier(0.8, 500)).toBe(computeZoomTier(0.8, 500));
  });

  it("classifies graph size tiers matching the required fixture boundaries", () => {
    expect(graphSizeTier(100)).toBe("small");
    expect(graphSizeTier(1000)).toBe("medium");
    expect(graphSizeTier(10000)).toBe("large");
  });

  it("forces large-graph safe mode only for the large tier", () => {
    expect(requiresLargeGraphSafeMode(1000)).toBe(false);
    expect(requiresLargeGraphSafeMode(10000)).toBe(true);
  });

  it("bounds the detailed-node rendering budget for medium/large graphs", () => {
    expect(detailedNodeBudget("small")).toBeGreaterThan(detailedNodeBudget("medium"));
    expect(detailedNodeBudget("medium")).toBeGreaterThan(detailedNodeBudget("large"));
  });
});

describe("shareable graph URL state", () => {
  it("round-trips non-default state and omits default fields", () => {
    const state = { ...DEFAULT_GRAPH_URL_STATE, mode: "execution" as const, query: "atom", selected: "node-123" };
    const params = serializeGraphUrlState(state);
    expect(params.get("mode")).toBe("execution");
    expect(params.has("view")).toBe(false);
    expect(parseGraphUrlState(params)).toEqual(state);
  });

  it("falls back to safe defaults for malformed or oversized input", () => {
    const params = new URLSearchParams({ mode: "delete-everything", layout: "physics-chaos", q: "x".repeat(500), selected: "<script>alert(1)</script>" });
    const parsed = parseGraphUrlState(params);
    expect(parsed.mode).toBe("authority");
    expect(parsed.algorithm).toBe("blueprint");
    expect(parsed.query.length).toBeLessThanOrEqual(200);
    expect(parsed.selected).toBeUndefined();
  });
});

describe("client-local layout preference boundary", () => {
  it("persists and reloads bounded layout state per project/graph", () => {
    const store = new Map<string, string>();
    const storage = { getItem: (key: string) => store.get(key) ?? null, setItem: (key: string, value: string) => void store.set(key, value) };
    const state = createEmptyLayoutState("compact");
    state.nodePositions["n1"] = { x: 5, y: 6 };
    expect(saveLayoutPreference(storage, "project-1", "graph-1", state)).toBe(true);
    expect(loadLayoutPreference(storage, "project-1", "graph-1", "blueprint").nodePositions.n1).toEqual({ x: 5, y: 6 });
  });

  it("refuses to persist oversized state and falls back safely on corrupt data", () => {
    const store = new Map<string, string>();
    const storage = { getItem: (key: string) => store.get(key) ?? null, setItem: (key: string, value: string) => void store.set(key, value) };
    const huge = createEmptyLayoutState("blueprint");
    for (let i = 0; i < 5000; i += 1) huge.nodePositions[`n${i}`] = { x: i, y: i };
    expect(saveLayoutPreference(storage, "project-1", "graph-1", huge)).toBe(false);
    storage.setItem("sg-graph-layout:project-1:graph-1", "not json");
    expect(loadLayoutPreference(storage, "project-1", "graph-1", "blueprint").nodePositions).toEqual({});
  });
});

describe("deterministic performance fixtures", () => {
  it.each(FIXTURE_SIZES)("produces exactly %i nodes with stable, repeatable IDs", (size) => {
    const first = buildFixtureGraph(size);
    const second = buildFixtureGraph(size);
    expect(first.nodes).toHaveLength(size);
    expect(first.nodes.map((node) => node.id)).toEqual(second.nodes.map((node) => node.id));
    expect(first.edges.map((edge) => edge.id)).toEqual(second.edges.map((edge) => edge.id));
  });

  it("never imports fixtures from a production module", async () => {
    const { readFileSync, readdirSync } = await import("node:fs");
    const { join } = await import("node:path");
    const libDir = join(__dirname);
    const componentDir = join(__dirname, "..", "..", "components", "graph");
    const files = [
      ...readdirSync(libDir).filter((name) => name.endsWith(".ts") && !name.endsWith(".test.ts") && name !== "fixtures.ts"),
      ...readdirSync(componentDir).filter((name) => name.endsWith(".tsx") && !name.endsWith(".test.tsx")),
    ].map((name) => (name.endsWith(".tsx") ? join(componentDir, name) : join(libDir, name)));
    for (const file of files) {
      expect(readFileSync(file, "utf8")).not.toMatch(/from ["'].*fixtures["']/);
    }
  });
});
