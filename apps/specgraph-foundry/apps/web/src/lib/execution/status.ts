import type { FreeformRecord } from "./schemas";

export type RunStatus = "QUEUED" | "RUNNING" | "SUCCEEDED" | "FAILED" | "REJECTED" | "INVALID" | "VERIFIED" | "UNKNOWN";

const RUN_STATUSES: RunStatus[] = ["QUEUED", "RUNNING", "SUCCEEDED", "FAILED", "REJECTED", "INVALID", "VERIFIED"];

export function normalizeRunStatus(value: unknown): RunStatus {
  const status = String(value ?? "UNKNOWN").toUpperCase();
  return (RUN_STATUSES as string[]).includes(status) ? (status as RunStatus) : "UNKNOWN";
}

export function runStatusTone(status: RunStatus): "neutral" | "success" | "warning" | "danger" | "info" {
  if (status === "VERIFIED" || status === "SUCCEEDED") return "success";
  if (status === "FAILED" || status === "REJECTED" || status === "INVALID") return "danger";
  if (status === "RUNNING") return "info";
  if (status === "QUEUED") return "warning";
  return "neutral";
}

export type NodeStatus = "PENDING" | "READY" | "CLAIMED" | "RUNNING" | "BLOCKED" | "FAILED" | "CANCELLED" | "COMPLETE" | "UNKNOWN";

const NODE_STATUSES: NodeStatus[] = ["PENDING", "READY", "CLAIMED", "RUNNING", "BLOCKED", "FAILED", "CANCELLED", "COMPLETE"];

export function normalizeNodeStatus(value: unknown): NodeStatus {
  const status = String(value ?? "UNKNOWN").toUpperCase();
  return (NODE_STATUSES as string[]).includes(status) ? (status as NodeStatus) : "UNKNOWN";
}

/**
 * Canonical readiness comes only from the server-returned ready_nodes list
 * on the run detail response — never derived from loaded node/edge state.
 */
export function isServerReadyExecutionNode(nodeId: string, readyNodes: FreeformRecord[] | undefined): boolean {
  if (!Array.isArray(readyNodes)) return false;
  return readyNodes.some((node) => typeof node?.id === "string" && node.id === nodeId);
}

export function stageOf(nodeType: string | undefined): "CONTRACT" | "IMPLEMENTATION" | "VERIFICATION" | "UNKNOWN" {
  const stage = String(nodeType ?? "").toUpperCase();
  if (stage === "CONTRACT" || stage === "IMPLEMENTATION" || stage === "VERIFICATION") return stage;
  return "UNKNOWN";
}
