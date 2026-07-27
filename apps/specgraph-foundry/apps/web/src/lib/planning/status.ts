import type { FindingSeverity, PlanStatus } from "./schemas";

export function normalizePlanStatus(value: unknown): PlanStatus {
  const status = String(value ?? "UNKNOWN").toUpperCase();
  if (status === "DRAFT" || status === "BLOCKED" || status === "INVALID" || status === "VERIFIED") {
    return status;
  }
  return "UNKNOWN";
}

export function planStatusTone(status: PlanStatus): "neutral" | "success" | "warning" | "danger" | "info" {
  if (status === "VERIFIED") return "success";
  if (status === "INVALID") return "danger";
  if (status === "BLOCKED") return "warning";
  if (status === "DRAFT") return "info";
  return "neutral";
}

export function normalizeFindingSeverity(value: unknown): FindingSeverity {
  const severity = String(value ?? "UNKNOWN").toUpperCase();
  if (severity === "ERROR" || severity === "WARNING" || severity === "INFO") {
    return severity;
  }
  return "UNKNOWN";
}

export function findingSeverityTone(severity: FindingSeverity): "neutral" | "success" | "warning" | "danger" | "info" {
  if (severity === "ERROR") return "danger";
  if (severity === "WARNING") return "warning";
  if (severity === "INFO") return "info";
  return "neutral";
}

export type ExecutionStage = "CONTRACT" | "IMPLEMENTATION" | "VERIFICATION" | "UNKNOWN";

export function normalizeExecutionStage(nodeType: string | undefined): ExecutionStage {
  const stage = String(nodeType ?? "").toUpperCase();
  if (stage === "CONTRACT" || stage === "IMPLEMENTATION" || stage === "VERIFICATION") {
    return stage;
  }
  return "UNKNOWN";
}
