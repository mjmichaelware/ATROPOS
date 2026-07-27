import { describe, expect, it } from "vitest";
import { bindingByAtomAndStage, bindingByNodeId, isServerReadyNode, stageDistribution } from "./bindings";
import { buildRequiresSubgraph, checkProposedRelationCycle } from "./cycle";
import { filterFindings, findingCounts, findingFocusNodeId, groupFindingsBySeverity } from "./findings";
import { isSupportedRelationType, validateRelationInput } from "./relations";
import type { AuthorityRelation, PlanBinding, PlanFinding } from "./schemas";
import { normalizeExecutionStage, normalizeFindingSeverity, normalizePlanStatus, planStatusTone } from "./status";

function relation(overrides: Partial<AuthorityRelation> = {}): AuthorityRelation {
  return { id: "rel-1", from_atom_id: "a", to_atom_id: "b", relation_type: "REQUIRES", ...overrides };
}

describe("relation type support", () => {
  it("recognizes only the seven real backend relation types", () => {
    for (const type of ["REQUIRES", "REFINES", "CONFLICTS_WITH", "DUPLICATES", "IMPLEMENTS", "VERIFIES", "RELATES_TO"]) {
      expect(isSupportedRelationType(type)).toBe(true);
    }
    expect(isSupportedRelationType("INVENTED_TYPE")).toBe(false);
  });
});

describe("relation input validation", () => {
  it("requires distinct source/target atoms and a supported type", () => {
    expect(validateRelationInput({ from_atom_id: "a", to_atom_id: "a", relation_type: "REQUIRES" })).toHaveProperty("to_atom_id");
    expect(validateRelationInput({ from_atom_id: "", to_atom_id: "b", relation_type: "REQUIRES" })).toHaveProperty("from_atom_id");
    expect(validateRelationInput({ from_atom_id: "a", to_atom_id: "b", relation_type: "NOT_A_TYPE" as never })).toHaveProperty("relation_type");
  });

  it("rejects non-finite or out-of-range confidence and oversized rationale", () => {
    expect(validateRelationInput({ from_atom_id: "a", to_atom_id: "b", relation_type: "REQUIRES", confidence: Number.NaN })).toHaveProperty("confidence");
    expect(validateRelationInput({ from_atom_id: "a", to_atom_id: "b", relation_type: "REQUIRES", confidence: 1.5 })).toHaveProperty("confidence");
    expect(validateRelationInput({ from_atom_id: "a", to_atom_id: "b", relation_type: "REQUIRES", rationale: "x".repeat(2001) })).toHaveProperty("rationale");
  });

  it("accepts a valid input with no errors", () => {
    expect(validateRelationInput({ from_atom_id: "a", to_atom_id: "b", relation_type: "REQUIRES", confidence: 0.9, rationale: "ok" })).toEqual({});
  });
});

describe("cycle advisory", () => {
  it("treats non-REQUIRES relation types as not applicable to cycle checking", () => {
    const result = checkProposedRelationCycle([], { from_atom_id: "a", to_atom_id: "b", relation_type: "CONFLICTS_WITH" });
    expect(result.kind).toBe("not-applicable");
  });

  it("finds no cycle in an acyclic loaded subset", () => {
    const relations = [relation({ id: "r1", from_atom_id: "a", to_atom_id: "b" }), relation({ id: "r2", from_atom_id: "b", to_atom_id: "c" })];
    const result = checkProposedRelationCycle(relations, { from_atom_id: "c", to_atom_id: "d", relation_type: "REQUIRES" });
    expect(result.kind).toBe("no-cycle-in-loaded-subset");
  });

  it("detects a direct cycle", () => {
    const relations = [relation({ id: "r1", from_atom_id: "a", to_atom_id: "b" })];
    const result = checkProposedRelationCycle(relations, { from_atom_id: "b", to_atom_id: "a", relation_type: "REQUIRES" });
    expect(result.kind).toBe("cycle-detected");
    if (result.kind === "cycle-detected") expect(result.path).toEqual(["b", "a", "b"]);
  });

  it("detects a long/deep cycle and reconstructs a bounded path", () => {
    const relations = Array.from({ length: 50 }, (_, index) =>
      relation({ id: `r${index}`, from_atom_id: `n${index}`, to_atom_id: `n${index + 1}` }),
    );
    const result = checkProposedRelationCycle(relations, { from_atom_id: "n50", to_atom_id: "n0", relation_type: "REQUIRES" });
    expect(result.kind).toBe("cycle-detected");
    if (result.kind === "cycle-detected") {
      expect(result.path[0]).toBe("n50");
      expect(result.path.at(-1)).toBe("n50");
      expect(result.path.length).toBe(52);
    }
  });

  it("handles disconnected subgraphs without a false cycle", () => {
    const relations = [relation({ id: "r1", from_atom_id: "a", to_atom_id: "b" }), relation({ id: "r2", from_atom_id: "x", to_atom_id: "y" })];
    const result = checkProposedRelationCycle(relations, { from_atom_id: "y", to_atom_id: "b", relation_type: "REQUIRES" });
    expect(result.kind).toBe("no-cycle-in-loaded-subset");
  });

  it("ignores duplicate, malformed, and unknown-type relation records", () => {
    const relations = [
      relation({ id: "r1", from_atom_id: "a", to_atom_id: "b" }),
      relation({ id: "r1-dup", from_atom_id: "a", to_atom_id: "b" }),
      { id: "bad" } as unknown as AuthorityRelation,
      relation({ id: "r2", from_atom_id: "b", to_atom_id: "c", relation_type: "RELATES_TO" }),
    ];
    const subgraph = buildRequiresSubgraph(relations);
    expect(subgraph.get("a")).toEqual(new Set(["b"]));
    expect(subgraph.has("b")).toBe(false);
  });

  it("never claims the full project graph is acyclic from a bounded loaded subset", () => {
    const result = checkProposedRelationCycle([], { from_atom_id: "a", to_atom_id: "b", relation_type: "REQUIRES" });
    expect(result.kind).toBe("no-cycle-in-loaded-subset");
    expect(JSON.stringify(result)).not.toMatch(/acyclic|no cycles exist/i);
  });
});

describe("plan status and finding normalization", () => {
  it("normalizes only the four real plan statuses", () => {
    expect(normalizePlanStatus("verified")).toBe("VERIFIED");
    expect(normalizePlanStatus("future-status")).toBe("UNKNOWN");
    expect(planStatusTone("INVALID")).toBe("danger");
  });

  it("normalizes only real finding severities", () => {
    expect(normalizeFindingSeverity("error")).toBe("ERROR");
    expect(normalizeFindingSeverity("catastrophic")).toBe("UNKNOWN");
  });

  it("normalizes only real execution stages", () => {
    expect(normalizeExecutionStage("CONTRACT")).toBe("CONTRACT");
    expect(normalizeExecutionStage("SOMETHING_ELSE")).toBe("UNKNOWN");
  });
});

describe("finding filtering, grouping, and focus", () => {
  const findings: PlanFinding[] = [
    { id: "f1", severity: "ERROR", code: "NODE_COUNT_MISMATCH", message: "m", entity_id: "node-1" },
    { id: "f2", severity: "WARNING", code: "SOMETHING", message: "m" },
    { id: "f3", severity: "unknown-severity", code: "X", message: "m" },
  ];

  it("filters by severity while preserving the real server code", () => {
    expect(filterFindings(findings, "ERROR")).toHaveLength(1);
    expect(filterFindings(findings, "ERROR")[0].code).toBe("NODE_COUNT_MISMATCH");
  });

  it("groups and counts findings by normalized severity", () => {
    const groups = groupFindingsBySeverity(findings);
    expect(groups.ERROR).toHaveLength(1);
    expect(groups.UNKNOWN).toHaveLength(1);
    expect(findingCounts(findings)).toEqual({ error: 1, warning: 1, info: 0, unknown: 1, total: 3 });
  });

  it("only focuses an entity ID that maps to an actually loaded graph node", () => {
    expect(findingFocusNodeId(findings[0], new Set(["node-1"]))).toBe("node-1");
    expect(findingFocusNodeId(findings[0], new Set(["node-2"]))).toBeUndefined();
    expect(findingFocusNodeId(findings[1], new Set(["node-1"]))).toBeUndefined();
  });
});

describe("plan bindings and execution readiness", () => {
  const bindings: PlanBinding[] = [
    { id: "b1", graph_node_id: "node-1", atom_id: "atom-1", stage: "CONTRACT" },
    { id: "b2", graph_node_id: "node-2", atom_id: "atom-1", stage: "IMPLEMENTATION" },
  ];

  it("looks up bindings by node ID and by atom/stage", () => {
    expect(bindingByNodeId(bindings, "node-1")?.id).toBe("b1");
    expect(bindingByAtomAndStage(bindings, "atom-1", "IMPLEMENTATION")?.id).toBe("b2");
    expect(bindingByNodeId(bindings, "does-not-exist")).toBeUndefined();
  });

  it("derives execution readiness only from the server ready_nodes list, never from loaded predecessors", () => {
    expect(isServerReadyNode("node-1", [{ id: "node-1" }])).toBe(true);
    expect(isServerReadyNode("node-1", [{ id: "node-2" }])).toBe(false);
    expect(isServerReadyNode("node-1", undefined)).toBe(false);
  });

  it("computes stage distribution from real node types only", () => {
    expect(stageDistribution(["CONTRACT", "CONTRACT", "IMPLEMENTATION", "MYSTERY"])).toEqual({
      CONTRACT: 2,
      IMPLEMENTATION: 1,
      VERIFICATION: 0,
      UNKNOWN: 1,
    });
  });
});
