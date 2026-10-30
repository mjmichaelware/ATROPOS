import type { FreeformRecord, PlanBinding } from "./schemas";
import { normalizeExecutionStage, type ExecutionStage } from "./status";

export function bindingByNodeId(bindings: PlanBinding[], nodeId: string): PlanBinding | undefined {
  return bindings.find((binding) => binding.graph_node_id === nodeId);
}

export function bindingByAtomAndStage(bindings: PlanBinding[], atomId: string, stage: string): PlanBinding | undefined {
  return bindings.find((binding) => binding.atom_id === atomId && binding.stage === stage);
}

/**
 * Canonical readiness comes only from the server-returned ready_nodes list.
 * This never derives readiness from loaded predecessor/edge state.
 */
export function isServerReadyNode(nodeId: string, readyNodes: FreeformRecord[] | undefined): boolean {
  if (!Array.isArray(readyNodes)) return false;
  return readyNodes.some((node) => typeof node?.id === "string" && node.id === nodeId);
}

export function stageDistribution(nodeTypes: Array<string | undefined>): Record<ExecutionStage, number> {
  const distribution: Record<ExecutionStage, number> = { CONTRACT: 0, IMPLEMENTATION: 0, VERIFICATION: 0, UNKNOWN: 0 };
  for (const nodeType of nodeTypes) {
    distribution[normalizeExecutionStage(nodeType)] += 1;
  }
  return distribution;
}
